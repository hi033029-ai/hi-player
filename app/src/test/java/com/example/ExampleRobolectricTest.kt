package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.VideoItem
import com.example.model.VideoResolutionBadge
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Hi Player", appName)
  }

  @Test
  fun `test 4k remux resolution badge logic`() {
    val uhdVideo = VideoItem(
      id = 1L,
      title = "Avatar.The.Way.Of.Water.2022.2160p.UHD.Remux.HEVC.DTS-HD.MA.7.1.mkv",
      uri = android.net.Uri.EMPTY,
      durationMs = 11520000L,
      sizeBytes = 85_899_345_920L,
      width = 3840,
      height = 2160,
      mimeType = "video/x-matroska",
      folderName = "4K Remux",
      isHdr = true,
      codec = "HEVC Main 10 HDR"
    )

    assertEquals(VideoResolutionBadge.UHD_4K, uhdVideo.resolutionBadge)
    assertEquals("3840x2160", uhdVideo.resolutionString)
    assertEquals("80.00 GB", uhdVideo.formattedSize)
    assertEquals("3:12:00", uhdVideo.formattedDuration)
  }

  @Test
  fun `test PlayerViewModel and HiPlayerEngine initialization`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.viewmodel.PlayerViewModel(context)
    org.junit.Assert.assertNotNull(viewModel.engine)
    org.junit.Assert.assertNotNull(viewModel.engine.getPlayer())
    viewModel.engine.release()
  }

  @Test
  fun `test LibraryViewModel navigation modes and view modes`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val libraryViewModel = com.example.viewmodel.LibraryViewModel(context)
    assertEquals(com.example.viewmodel.LibraryNavMode.ALL_VIDEOS, libraryViewModel.navMode.value)
    assertEquals(com.example.viewmodel.LibraryViewMode.LAYER_LIST, libraryViewModel.viewMode.value)
    
    libraryViewModel.toggleViewMode()
    assertEquals(com.example.viewmodel.LibraryViewMode.GRADLE_GRID, libraryViewModel.viewMode.value)

    libraryViewModel.setNavMode(com.example.viewmodel.LibraryNavMode.TREE_FOLDERS)
    assertEquals(com.example.viewmodel.LibraryNavMode.TREE_FOLDERS, libraryViewModel.navMode.value)

    libraryViewModel.setNavMode(com.example.viewmodel.LibraryNavMode.ALL_FOLDERS)
    assertEquals(com.example.viewmodel.LibraryNavMode.ALL_FOLDERS, libraryViewModel.navMode.value)
  }

  @Test
  fun `test FileManagerViewModel archive and document model`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val fileManagerViewModel = com.example.viewmodel.FileManagerViewModel(context)
    org.junit.Assert.assertNotNull(fileManagerViewModel.currentDirectory.value)
    org.junit.Assert.assertNotNull(fileManagerViewModel.allArchives.value)
    org.junit.Assert.assertNotNull(fileManagerViewModel.allDocuments.value)
    org.junit.Assert.assertNotNull(fileManagerViewModel.folderFiles.value)
  }
}
