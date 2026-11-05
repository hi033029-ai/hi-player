package com.example.ui.screens

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

internal data class BlockerManifest(val version: Long, val sha256: String)

/** Fixed format, fixed code origin, pinned key; metadata cannot choose other sites/URLs/assets. */
internal object BlockerPackageProtocol {
    const val MAX_SCRIPT_BYTES = 100_000
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun verifyManifest(bytes: ByteArray, signature: ByteArray, key: PublicKey,
        now: Long, appCode: Int, fresh: Boolean): BlockerManifest {
        require(bytes.size in 1..16_384 && signature.size in 1..256)
        require(Signature.getInstance("SHA256withECDSA").run {
            initVerify(key); update(bytes); verify(signature)
        })
        // Parse only AFTER verifying the exact bytes that were signed.
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("schema") == 1 && json.getString("asset") == "orbit_youtube.js")
        val version = json.getLong("version")
        val issued = json.getLong("issuedAt")
        val expires = json.getLong("expiresAt")
        val minimum = json.getInt("minAppVersionCode")
        val hash = json.getString("sha256")
        require(version > 0 && issued > 0 && expires > issued)
        require(expires - issued in 1..(90L * 24 * 60 * 60 * 1000))
        require(minimum in 1..appCode && hash.matches(Regex("[a-f0-9]{64}")))
        if (fresh) require(issued <= now + 300_000L && expires > now)
        return BlockerManifest(version, hash)
    }

    fun verifyScript(bytes: ByteArray, manifest: BlockerManifest) {
        require(bytes.size in 1..MAX_SCRIPT_BYTES && digest(bytes) == manifest.sha256)
    }
}

/** Signed packages are trusted code, not arbitrary remote script URLs. */
internal object SignedBlockerUpdates {
    private const val BASE = "https://raw.githubusercontent.com/hi033029-ai/hi-player/blocker-updates/"
    private const val CHECK_INTERVAL = 6L * 60 * 60 * 1000
    private val lock = Any()
    private var loaded = false
    private var highest = 0L
    private var lastCheck = 0L
    private var active: JSONObject? = null
    private var previous: JSONObject? = null
    @Volatile private var currentScript: String? = null
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private val mutableVersion = MutableStateFlow(0L)
    val version = mutableVersion.asStateFlow()

    fun script(): String? = currentScript
    private fun file(context: Context): AtomicFile {
        val directory = File(context.noBackupFilesDir, "blocker-updates")
        check(directory.isDirectory || directory.mkdirs())
        return AtomicFile(File(directory, "state.json"))
    }
    private fun key(context: Context): PublicKey {
        val pem = context.assets.open("blocker_update_public_key.pem").bufferedReader().use { it.readText() }
        val der = Base64.decode(pem.lines().filterNot { it.startsWith("---") }.joinToString(""), Base64.DEFAULT)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }
    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(json: JSONObject, name: String): ByteArray = Base64.decode(json.getString(name), Base64.DEFAULT)
    private fun checked(context: Context, bundle: JSONObject, fresh: Boolean): Pair<BlockerManifest, String> {
        val manifest = BlockerPackageProtocol.verifyManifest(decode(bundle, "manifest"), decode(bundle, "signature"),
            key(context), System.currentTimeMillis(), BuildConfig.VERSION_CODE, fresh)
        val script = decode(bundle, "script")
        BlockerPackageProtocol.verifyScript(script, manifest)
        return manifest to script.toString(Charsets.UTF_8)
    }

    /** Call on I/O dispatcher. Cached code is reverified; expired installs can still work offline. */
    fun load(context: Context) = synchronized(lock) {
        if (loaded) return@synchronized
        runCatching {
            val bytes = file(context).openRead().use { input ->
                val data = input.readBytesLimited(400_000)
                data
            }
            val state = JSONObject(bytes.toString(Charsets.UTF_8))
            highest = state.getLong("highest").coerceAtLeast(0L)
            lastCheck = state.optLong("lastCheck", 0L)
            active = state.optJSONObject("active")
            previous = state.optJSONObject("previous")
            active?.let {
                val verified = checked(context, it, fresh = false)
                require(verified.first.version <= highest)
                currentScript = verified.second
                mutableVersion.value = verified.first.version
            }
        }.onFailure {
            // Corrupt/invalid cache never executes; retain any readable high-water mark.
            active = null; previous = null; currentScript = null; mutableVersion.value = 0L
        }
        loaded = true
        mutableRevision.value++
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun fetch(path: String, limit: Int, cancelled: () -> Boolean): ByteArray {
        // Only code-computed paths below this repository; redirects are never followed.
        require(path == "manifest.json" || path == "manifest.sig.b64" || path.matches(Regex("packages/[a-f0-9]{64}\\.js")))
        if (cancelled()) throw IOException("Update cancelled")
        val connection = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 10_000; readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            require(connection.responseCode == 200 && connection.contentLengthLong <= limit)
            val deadline = System.nanoTime() + 30_000_000_000L
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (cancelled() || System.nanoTime() > deadline) throw IOException("Update stopped")
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= limit)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun save(context: Context, next: JSONObject?, fallback: JSONObject?, high: Long, checkedAt: Long) {
        val bytes = JSONObject().put("highest", high).put("lastCheck", checkedAt)
            .put("active", next ?: JSONObject.NULL).put("previous", fallback ?: JSONObject.NULL)
            .toString().toByteArray(Charsets.UTF_8)
        val atomic = file(context)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }

    fun update(context: Context, force: Boolean = false, cancelled: () -> Boolean = { false }): Boolean = synchronized(lock) {
        load(context)
        val now = System.currentTimeMillis()
        if (!force && lastCheck > 0 && now - lastCheck in 0L until CHECK_INTERVAL) return@synchronized true
        runCatching {
            val manifestBytes = fetch("manifest.json", 16_384, cancelled)
            val signature = Base64.decode(fetch("manifest.sig.b64", 1024, cancelled), Base64.DEFAULT)
            val manifest = BlockerPackageProtocol.verifyManifest(manifestBytes, signature, key(context),
                now, BuildConfig.VERSION_CODE, fresh = true)
            if (manifest.version <= highest) {
                // Never reinstall a rolled-back/quarantined version or accept a network downgrade.
                save(context, active, previous, highest, now)
                lastCheck = now
                return@runCatching true
            }
            val bytes = fetch("packages/${manifest.sha256}.js", BlockerPackageProtocol.MAX_SCRIPT_BYTES, cancelled)
            BlockerPackageProtocol.verifyScript(bytes, manifest)
            if (cancelled()) throw IOException("Update cancelled")
            val bundle = JSONObject().put("manifest", encode(manifestBytes))
                .put("signature", encode(signature)).put("script", encode(bytes))
            save(context, bundle, active, manifest.version, now)
            previous = active; active = bundle; highest = manifest.version; lastCheck = now
            currentScript = bytes.toString(Charsets.UTF_8)
            mutableVersion.value = manifest.version
            mutableRevision.value++
            true
        }.getOrDefault(false)
    }

    /** Explicit local rollback only. Keep high-water mark so background work cannot reinstall the bad version. */
    fun rollback(context: Context): Boolean = synchronized(lock) {
        load(context)
        runCatching {
            val fallback = previous?.takeIf { runCatching { checked(context, it, fresh = false) }.isSuccess }
            val verified = fallback?.let { checked(context, it, fresh = false) }
            save(context, fallback, null, highest, lastCheck)
            active = fallback; previous = null; currentScript = verified?.second
            mutableVersion.value = verified?.first?.version ?: 0L
            mutableRevision.value++
            true
        }.getOrDefault(false)
    }
}
