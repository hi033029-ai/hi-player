package com.example.connections

import android.net.Uri
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class SmbCredentials(val host: String, val share: String, val username: String, val password: CharArray, val domain: String, val encrypted: Boolean)
internal data class SmbStatus(val id: String? = null, val label: String = "")
internal class SmbHandle(private val client: SMBClient, private val connection: Connection, private val session: Session, val share: DiskShare) : Closeable {
    fun file(path: String): com.hierynomus.smbj.share.File = share.openFile(StoragePolicy.smbPath(path),
        EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_READ_ATTRIBUTES), EnumSet.noneOf(FileAttributes::class.java),
        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
        SMB2CreateDisposition.FILE_OPEN, EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE))
    override fun close() { runCatching { share.close() }; runCatching { session.close() }; runCatching { connection.close() }; runCatching { client.close() } }
}
/** SMB2/3 only. Credentials and connection identity live in process memory, never a URL or Bundle. */
internal object SmbStorage {
    private var active: Pair<String, SmbCredentials>? = null
    private val _status = MutableStateFlow(SmbStatus())
    val status = _status.asStateFlow()
    fun validate(host: String, share: String) {
        require(host.isNotBlank() && host.length <= 253 && host.all { it.isLetterOrDigit() || it in ".-:" })
        require(share.isNotBlank() && share.length <= 80 && share.none { it in "/\\:@\u0000" } && share !in setOf(".", ".."))
    }
    fun connect(credentials: SmbCredentials): SmbHandle {
        validate(credentials.host, credentials.share)
        val config = SmbConfig.builder().withTimeout(15, TimeUnit.SECONDS).withSoTimeout(20, TimeUnit.SECONDS)
            .withSigningRequired(true).withEncryptData(credentials.encrypted)
        if (credentials.encrypted) config.withDialects(SMB2Dialect.SMB_3_1_1, SMB2Dialect.SMB_3_0_2, SMB2Dialect.SMB_3_0)
        val client = SMBClient(config.build())
        var connection: Connection? = null; var session: Session? = null
        try {
            val connected = client.connect(credentials.host); connection = connected
            val authenticated = connected.authenticate(AuthenticationContext(credentials.username, credentials.password, credentials.domain)); session = authenticated
            val share = authenticated.connectShare(credentials.share)
            if (share !is DiskShare) { share.close(); error("Not a disk share") }
            return SmbHandle(client, connected, authenticated, share)
        } catch (e: Exception) { runCatching { session?.close() }; runCatching { connection?.close() }; runCatching { client.close() }; throw e }
    }
    @Synchronized fun activate(credentials: SmbCredentials) {
        active?.second?.password?.fill('\u0000')
        val id = UUID.randomUUID().toString()
        active = id to SmbCredentials(credentials.host, credentials.share, credentials.username, credentials.password.copyOf(), credentials.domain, credentials.encrypted)
        _status.value = SmbStatus(id, credentials.share)
    }
    @Synchronized fun disconnect() { active?.second?.password?.fill('\u0000'); active = null; _status.value = SmbStatus() }
    @Synchronized fun requireActive(id: String) { check(active?.first == id) { "Reconnect network folder" } }
    fun open(id: String): SmbHandle {
        val copy = synchronized(this) { val entry = checkNotNull(active); check(entry.first == id)
            val c = entry.second; SmbCredentials(c.host, c.share, c.username, c.password.copyOf(), c.domain, c.encrypted) }
        return try { connect(copy).also { handle -> try { requireActive(id) } catch (e: Exception) { handle.close(); throw e } } }
        finally { copy.password.fill('\u0000') }
    }
    fun list(id: String, folder: String): StoragePage = open(id).use { handle ->
        val path = StoragePolicy.smbPath(folder)
        val files = handle.share.openDirectory(path, EnumSet.of(AccessMask.FILE_LIST_DIRECTORY, AccessMask.FILE_READ_ATTRIBUTES),
            EnumSet.noneOf(FileAttributes::class.java), EnumSet.allOf(SMB2ShareAccess::class.java), SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE)).use { directory -> directory.iterator().asSequence().filter { it.fileName !in setOf(".", "..") }.take(1001).map { item ->
            val name = item.fileName
            require(name.none { it in "/\\\u0000" })
            val child = if (path.isBlank()) name else "$path\\$name"
            StorageFile(child, name, (item.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L, StoragePolicy.mime(name), item.endOfFile)
        }.toList() }
        requireActive(id)
        StoragePage(files.take(1000).sortedWith(compareByDescending<StorageFile> { it.folder }.thenBy { it.name.lowercase() }),
            note = if (files.size > 1000) "Showing the first 1,000 entries. Use smaller shared folders to see more." else null)
    }
    fun mediaUri(id: String, file: String): Uri = Uri.Builder().scheme("hiplayer-smb").authority(id).appendPath(StoragePolicy.smbPath(file)).build()
}
