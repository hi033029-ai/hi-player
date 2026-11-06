package com.example.browser.gecko
import org.junit.Assert.*
import org.junit.Test
class GeckoPermissionOriginTest {
 @Test fun stripsPrivatePathQueryAndFragment() { assertEquals("https://example.com", geckoPermissionOrigin("https://EXAMPLE.com/a?token=secret#x")) }
 @Test fun preservesNonstandardPort() { assertEquals("https://example.com:8443", geckoPermissionOrigin("https://example.com:8443/path")) }
 @Test fun rejectsInsecureOrUserInfoUrls() { assertNull(geckoPermissionOrigin("http://example.com")); assertNull(geckoPermissionOrigin("https://user:pass@example.com/")); assertNull(geckoPermissionOrigin("data:text/plain,foo")) }
}

class GeckoPermissionGateTest {
 @Test fun rejectsOverlapAndIgnoresDuplicateResults() {
  val gate = GeckoPermissionGate(); val first: () -> Unit = {}; val second: () -> Unit = {}
  assertTrue(gate.start(first)); assertFalse(gate.start(second)); assertFalse(gate.finish(second))
  assertTrue(gate.finish(first)); assertFalse(gate.finish(first)); assertTrue(gate.start(second))
 }
 @Test fun closingDeniesOnceAndPreventsStaleGrant() {
  val gate = GeckoPermissionGate(); var denials = 0; val cancel: () -> Unit = { denials++ }
  assertTrue(gate.start(cancel)); gate.close(); gate.close(); assertEquals(1, denials)
  assertFalse(gate.finish(cancel)); assertFalse(gate.start(cancel))
 }
}
