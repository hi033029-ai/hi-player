package com.example.player

import android.opengl.EGL14
import android.opengl.GLES20
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

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
        assertTrue(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,IntArray(1),0))
        val eglContext = EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
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
                val shader = ColorPresetEffect(ColorPresets.CUSTOM.copy(contrast = 1.15f))
                    .toGlShaderProgram(InstrumentationRegistry.getInstrumentation().targetContext,hdr) as BaseGlShaderProgram
                try {
                    shader.configure(8,1); shader.drawFrame(textures[0],0)
                    val output = ByteBuffer.allocateDirect(32)
                    GLES20.glReadPixels(0,0,8,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,output)
                    assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
                    levels.forEachIndexed { i,v ->
                        val red = output.get(i*4).toInt() and 255
                        assertTrue("Contrast crushed shadow $v -> $red (HDR shader=$hdr)",red >= v/1.15f-2)
                        assertTrue("Contrast washed out $v -> $red",red <= v*1.15f+2)
                        assertEquals("Gray must stay neutral",red,output.get(i*4+1).toInt() and 255)
                        assertEquals(red,output.get(i*4+2).toInt() and 255)
                    }
                    assertEquals(0,output.get(0).toInt() and 255)
                    assertEquals("Pivot luminance must not darken",46.0,(output.get(12).toInt() and 255).toDouble(),2.0)
                } finally { shader.release() }
            }
        } finally {
            GLES20.glDeleteFramebuffers(1,framebuffer,0); GLES20.glDeleteTextures(2,textures,0)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,eglContext); EGL14.eglTerminate(display)
        }
    }
}
