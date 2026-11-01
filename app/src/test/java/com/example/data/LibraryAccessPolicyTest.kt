package com.example.data

import org.junit.Assert.*
import org.junit.Test

class LibraryAccessPolicyTest {
    @Test fun noGrantMeansNoLibraryOrCache() {
        val access = LibraryAccessPolicy.resolve(false, false, false, false)
        assertEquals(LibraryAccessPolicy.Scope.NONE, access)
        assertFalse(LibraryAccessPolicy.canRestoreCache(access))
    }
    @Test fun selectedVideosNeverRestoreFullLibraryCache() {
        val access = LibraryAccessPolicy.resolve(false, false, true, false)
        assertEquals(LibraryAccessPolicy.Scope.SELECTED, access)
        assertFalse(LibraryAccessPolicy.canRestoreCache(access))
    }
    @Test fun selectedVideoGrantCannotAuthorizeMusic() {
        assertEquals(LibraryAccessPolicy.Scope.NONE, LibraryAccessPolicy.resolve(false, false, true, true))
    }
    @Test fun allFilesIsARealSeparateGrant() {
        assertEquals(LibraryAccessPolicy.Scope.FULL, LibraryAccessPolicy.resolve(false, true, false, false))
    }
    @Test fun fullMediaGrantMayUseLocalCache() {
        assertTrue(LibraryAccessPolicy.canRestoreCache(LibraryAccessPolicy.resolve(true, false, false, true)))
    }
}
