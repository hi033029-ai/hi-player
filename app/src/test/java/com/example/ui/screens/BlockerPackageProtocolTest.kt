package com.example.ui.screens

import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BlockerPackageProtocolTest {
    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val now = 1_800_000_000_000L
    private val script = "window.__example = true;".toByteArray()
    private fun manifest(expiry: Long = now + 86_400_000L, minimum: Int = 77): ByteArray = JSONObject()
        .put("schema", 1).put("asset", "orbit_youtube.js").put("version", 1)
        .put("issuedAt", now - 1000).put("expiresAt", expiry)
        .put("minAppVersionCode", minimum).put("sha256", BlockerPackageProtocol.digest(script))
        .toString().toByteArray()
    private fun sign(bytes: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(pair.private); update(bytes); sign()
    }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Invalid package was accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun acceptsSignedManifestAndMatchingScript() {
        val data = manifest()
        val verified = BlockerPackageProtocol.verifyManifest(data, sign(data), pair.public, now, 77, true)
        assertEquals(1L, verified.version)
        BlockerPackageProtocol.verifyScript(script, verified)
        rejected { BlockerPackageProtocol.verifyScript("changed".toByteArray(), verified) }
    }
    @Test fun rejectsModifiedAndExpiredOrIncompatibleManifests() {
        val original = manifest()
        val changed = JSONObject(original.toString(Charsets.UTF_8)).put("version", 2).toString().toByteArray()
        rejected { BlockerPackageProtocol.verifyManifest(changed, sign(original), pair.public, now, 77, true) }
        val expired = manifest(now - 1)
        rejected { BlockerPackageProtocol.verifyManifest(expired, sign(expired), pair.public, now, 77, true) }
        val incompatible = manifest(minimum = 1000)
        rejected { BlockerPackageProtocol.verifyManifest(incompatible, sign(incompatible), pair.public, now, 77, true) }
        // A previously verified install can remain usable offline after the distribution expiry.
        BlockerPackageProtocol.verifyManifest(expired, sign(expired), pair.public, now, 77, false)
    }
    @Test fun rejectsOtherPublisherKeyAndOversizedPayloads() {
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val data = manifest()
        rejected { BlockerPackageProtocol.verifyManifest(data, sign(data), other.public, now, 77, true) }
        val verified = BlockerPackageProtocol.verifyManifest(data, sign(data), pair.public, now, 77, true)
        rejected { BlockerPackageProtocol.verifyScript(ByteArray(100_001), verified) }
    }
}
