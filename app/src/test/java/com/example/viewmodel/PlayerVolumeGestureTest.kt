package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerVolumeGestureTest {
    @Test fun subStepMovementsAccumulateAndBoostCanCrossBackIntoSystemVolume() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val store = ViewModelStore()
        val model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[PlayerViewModel::class.java]
        try {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,max / 3,0)
            val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            model.onVolumeGestureStart()
            repeat(100) { model.onVolumeGesture(.002f) }
            model.onVolumeGestureEnd()
            assertTrue("Small events must sum to audible system-volume steps", audio.getStreamVolume(AudioManager.STREAM_MUSIC) >= before + 2)
            model.onVolumeGestureLevel(1.25f)
            model.onVolumeGestureStart()
            repeat(50) { model.onVolumeGesture(-.01f) }
            model.onVolumeGestureEnd()
            assertEquals(0,model.engine.volumeBoostPercent.value)
            assertTrue(kotlin.math.abs((.75f * max).toInt() - audio.getStreamVolume(AudioManager.STREAM_MUSIC)) <= 1)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,1,0)
            model.onVolumeGestureStart(); model.onVolumeGesture(.1f); model.onVolumeGestureEnd()
            assertTrue("Next swipe must rebase after hardware volume change",model.volumeLevel.value!! < .3f)
        } finally { store.clear() }
    }
}
