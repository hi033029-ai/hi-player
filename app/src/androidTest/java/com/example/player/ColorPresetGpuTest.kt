package com.example.player

import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES30
import android.opengl.GLES20
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Numeric shader regression, independent of screenshots and display tone mapping.
 * This tests the grading operation; it does NOT certify a phone's HDR output.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ColorPresetGpuTest {
    @Test fun contrastRetainsDarkDetailInsteadOfExponentiatingNormalizedHdrValues() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),1))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        assertTrue(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,IntArray(1),0))
        val eglContext = EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE),0)
        val surface = EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,8,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE),0)
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,eglContext))
        val textures = IntArray(2)
        val framebuffer = IntArray(1)
        try {
            val levels = intArrayOf(0,4,16,46,64,128,220,255)
            val input = ByteBuffer.allocateDirect(32)
            levels.forEach { v -> repeat(3) { input.put(v.toByte()) }; input.put(255.toByte()) }; input.flip()
            GLES20.glGenTextures(2,textures,0)
            textures.forEachIndexed { index,texture ->
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,8,1,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,if (index == 0) input else null)
            }
            GLES20.glGenFramebuffers(1,framebuffer,0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer[0])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,textures[1],0)
            assertEquals(GLES20.GL_FRAMEBUFFER_COMPLETE,GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER))
            GLES20.glViewport(0,0,8,1)
            listOf(false,true).forEach { hdr ->
                val preset = ColorPresets.CUSTOM.copy(contrast = 1.15f)
                val effect = ColorPresetEffect(preset)
                val shader = effect.toGlShaderProgram(InstrumentationRegistry.getInstrumentation().targetContext,hdr) as BaseGlShaderProgram
                try {
                    shader.configure(8,1); shader.drawFrame(textures[0],0)
                    val output = ByteBuffer.allocateDirect(32)
                    GLES20.glReadPixels(0,0,8,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,output)
                    assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
                    levels.forEachIndexed { i,v ->
                        val red = output.get(i*4).toInt() and 255
                        val expected = (expectedGray(v / 255f, preset, hdr) * 255f).coerceIn(0f, 255f)
                        assertEquals("Unexpected graded value $v -> $red (HDR shader=$hdr)", expected.toDouble(), red.toDouble(), 3.0)
                        assertEquals("Gray must stay neutral",red,output.get(i*4+1).toInt() and 255)
                        assertEquals(red,output.get(i*4+2).toInt() and 255)
                    }
                    assertEquals(0,output.get(0).toInt() and 255)
                    assertTrue("Pivot luminance must not darken",(output.get(12).toInt() and 255) >= 46)

                    // A slider move updates the already configured program on the next frame.
                    effect.update(ColorPresets.CUSTOM.copy(brightness = .5f))
                    shader.drawFrame(textures[0], 33_333L)
                    GLES20.glReadPixels(0,0,8,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,output)
                    val expectedAfterEdit = expectedGray(128 / 255f, ColorPresets.CUSTOM.copy(brightness = .5f), hdr) * 255f
                    assertEquals(expectedAfterEdit.toDouble(), (output.get(20).toInt() and 255).toDouble(), 3.0)
                } finally { shader.release() }
            }
        } finally {
            GLES20.glDeleteFramebuffers(1,framebuffer,0); GLES20.glDeleteTextures(2,textures,0)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,eglContext); EGL14.eglTerminate(display)
        }
    }
    @Test fun fp16HdrPreservesBlackAndHeadroomAndBrightActuallyRaisesSceneLuminance() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),1))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        assertTrue(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,IntArray(1),0))
        val eglContext = EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE),0)
        val surface = EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,10,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE),0)
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,eglContext))
        val textures = IntArray(2)
        val framebuffer = IntArray(1)
        try {
            // PQ EOTF's normalized linear space: these correspond to 0, .5, 2,
            // 20, 100, 203, 1000, 4000, 10000 nits, plus intermediate headroom.
            val levels = floatArrayOf(0f,.00005f,.0002f,.002f,.01f,.0203f,.1f,.4f,1f,1.2f)
            val input = ByteBuffer.allocateDirect(levels.size*16).order(ByteOrder.nativeOrder()).asFloatBuffer()
            levels.forEach { v -> repeat(3) { input.put(v) }; input.put(1f) }; input.flip()
            GLES20.glGenTextures(2,textures,0)
            textures.forEachIndexed { index,texture ->
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
                GLES30.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES30.GL_RGBA16F,levels.size,1,0,GLES20.GL_RGBA,GLES20.GL_FLOAT,if (index == 0) input else null)
            }
            assertEquals("FP16 texture allocation failed",GLES20.GL_NO_ERROR,GLES20.glGetError())
            GLES20.glGenFramebuffers(1,framebuffer,0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer[0])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,textures[1],0)
            assertEquals("FP16 output attachment required",GLES20.GL_FRAMEBUFFER_COMPLETE,GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER))
            GLES20.glViewport(0,0,levels.size,1)
            val presets = listOf(ColorPresets.NEUTRAL,ColorPresets.CUSTOM.copy(contrast = 1.15f),ColorPresets.HDR_BRIGHT)
            presets.forEachIndexed { index,preset ->
                val shader = ColorPresetEffect(preset).toGlShaderProgram(InstrumentationRegistry.getInstrumentation().targetContext,true) as BaseGlShaderProgram
                try {
                    shader.configure(levels.size,1); shader.drawFrame(textures[0],0)
                    val output = ByteBuffer.allocateDirect(levels.size*16).order(ByteOrder.nativeOrder()).asFloatBuffer()
                    GLES20.glReadPixels(0,0,levels.size,1,GLES20.GL_RGBA,GLES20.GL_FLOAT,output)
                    assertEquals("FP16 numeric readback failed",GLES20.GL_NO_ERROR,GLES20.glGetError())
                    levels.forEachIndexed { i,v ->
                        val actual = output.get(i*4)
                        assertTrue("Non-finite HDR output",actual.isFinite())
                        val expected = expectedGray(v, preset, hdr = true)
                        if (v == 0f) assertEquals("Black must remain black",0f,actual,0f)
                        else assertEquals(
                            "Unexpected FP16 HDR grade for ${preset.name} at $v",
                            expected,
                            actual,
                            maxOf(expected*.006f,.0000001f),
                        )
                        assertEquals("Neutral HDR gray must retain RGB ratios",actual,output.get(i*4+1),maxOf(actual*.002f,.0000001f))
                        assertEquals(actual,output.get(i*4+2),maxOf(actual*.002f,.0000001f))
                    }
                    assertTrue("Intermediate HDR headroom was clipped",output.get((levels.size-1)*4) > 1f)
                } finally { shader.release() }
            }
        } finally {
            GLES20.glDeleteFramebuffers(1,framebuffer,0); GLES20.glDeleteTextures(2,textures,0)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,eglContext); EGL14.eglTerminate(display)
        }
    }

    private fun expectedGray(input: Float, preset: ColorPreset, hdr: Boolean): Float {
        var value = input * preset.red * preset.brightness
        if (hdr && !preset.isIdentityTransform()) {
            val t = ((value - 0.10f) / 0.65f).coerceIn(0f, 1f)
            val smooth = t * t * (3f - 2f * t)
            value *= GRADED_HDR_MIDTONE_LIFT + (1f - GRADED_HDR_MIDTONE_LIFT) * smooth
        }
        val contrast = preset.contrast.coerceAtLeast(0.01f)
        var contrastGain = (contrast * value + 0.18f) / (value + 0.18f * contrast)
        if (contrast >= 1f) contrastGain = maxOf(1f, contrastGain)
        return value * contrastGain
    }

}
