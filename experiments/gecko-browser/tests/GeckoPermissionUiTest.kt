package com.example.browser.gecko

import android.content.Context
import android.app.Application
import android.Manifest
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeckoPermissionUiTest {
    private class Registry : ActivityResultRegistry() {
        var launches = 0
        val codes = mutableListOf<Int>()
        override fun <I : Any?, O : Any?> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) { launches++; codes += requestCode }
    }
    private fun setup(): Triple<GeckoPermissionUi, GeckoRuntimePermissionLauncher, Registry> {
        val registry = Registry()
        val launcher = GeckoRuntimePermissionLauncher(ApplicationProvider.getApplicationContext<Context>(), registry)
        return Triple(GeckoPermissionUi(launcher), launcher, registry)
    }
    @Test fun persistentStorageRequiresExplicitConsentAndDoesNotPersistPrivateChoice() {
        val (ui, _, _) = setup()
        var result: Boolean? = null
        ui.requestSite("https://example.org", GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE, true) { result = it }
        assertNull(result)
        assertTrue((ui.request as GeckoPermissionUi.Request.Site).privateMode)
        ui.allow(null, null)
        assertEquals(true, result)
        assertNull(ui.request)
        ui.close()
    }
    @Test fun unknownPermissionsAndOverlappingRequestsAreDenied() {
        val (ui, _, _) = setup()
        var rejected: Boolean? = null
        ui.requestSite("https://example.org", 9999, false) { rejected = it }
        assertEquals(false, rejected)
        var first: Boolean? = null
        ui.requestSite("https://example.org", GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE, false) { first = it }
        ui.requestSite("https://other.example", GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE, false) { rejected = it }
        assertEquals(false, rejected)
        ui.close()
        assertEquals(false, first)
        ui.close()
    }
    @Test fun invalidMediaSelectionIsDeniedWithoutAndroidPrompt() {
        val (ui, _, registry) = setup()
        var answer: Pair<Int?, Int?>? = null
        ui.requestMedia("https://example.org", listOf("camera"), emptyList()) { v, a -> answer = v to a }
        ui.allow(2, null)
        assertEquals(null to null, answer)
        assertEquals(0, registry.launches)
        ui.close()
    }
    @Test fun websiteConsentCannotBypassAndroidCameraPermission() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).denyPermissions(Manifest.permission.CAMERA)
        val (ui, _, registry) = setup()
        var answer: Pair<Int?, Int?>? = null
        ui.requestMedia("https://example.org", listOf("camera"), emptyList()) { v, a -> answer = v to a }
        ui.allow(0, null)
        // CAMERA is now intentionally declared, but site consent alone grants nothing.
        assertNull(answer)
        assertEquals(1, registry.launches)
        registry.dispatchResult(registry.codes.last(), mapOf(Manifest.permission.CAMERA to true))
        // A forged or stale positive result cannot override the operating system.
        assertEquals(null to null, answer)

        answer = null
        ui.requestMedia("https://example.org", listOf("camera"), emptyList()) { v, a -> answer = v to a }
        ui.allow(0, null)
        assertNull(answer)
        assertEquals(2, registry.launches)
        shadowOf(context).grantPermissions(Manifest.permission.CAMERA)
        registry.dispatchResult(registry.codes.last(), mapOf(Manifest.permission.CAMERA to true))
        assertEquals(0 to null, answer)
        ui.close()
    }
    @Test fun undeclaredAndroidPermissionIsDeniedWithoutPrompt() {
        val (ui, launcher, registry) = setup()
        var answer: Boolean? = null
        // Contacts remains undeclared; CAMERA is a supported Gecko capability now.
        launcher.request(listOf(Manifest.permission.READ_CONTACTS)) { answer = it }
        assertEquals(false, answer)
        assertEquals(0, registry.launches)
        ui.close()
        launcher.request(emptyList()) { answer = it }
        assertEquals(false, answer)
    }
    @Test fun cancelledRequestCannotGrantLaterRequest() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val registry = Registry()
        val old = GeckoRuntimePermissionLauncher(context, registry)
        var oldCalls = 0
        old.request(listOf(Manifest.permission.POST_NOTIFICATIONS)) { oldCalls++; assertFalse(it) }
        val oldCode = registry.codes.last()
        old.close()
        val next = GeckoRuntimePermissionLauncher(context, registry)
        var nextResult: Boolean? = null
        next.request(listOf(Manifest.permission.POST_NOTIFICATIONS)) { nextResult = it }
        val nextCode = registry.codes.last()
        registry.dispatchResult(oldCode, mapOf(Manifest.permission.POST_NOTIFICATIONS to true))
        assertNull(nextResult)
        assertEquals(1, oldCalls)
        // A positive callback alone is insufficient if the system still denies access.
        registry.dispatchResult(nextCode, mapOf(Manifest.permission.POST_NOTIFICATIONS to true))
        assertEquals(false, nextResult)
        next.close()
    }
}
