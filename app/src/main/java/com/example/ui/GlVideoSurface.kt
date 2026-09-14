package com.example.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.player.ColorPreset
import com.example.player.ColorPresets
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@UnstableApi
class GlVideoSurface(context: Context) : GLSurfaceView(context) {
    private val renderer = VideoGlRenderer()
    private var player: ExoPlayer? = null

    init {
        setEGLContextClientVersion(2)
        setPreserveEGLContextOnPause(true)
        // Keep Compose controls and gesture overlays above the video surface.
        // Without the media-overlay ordering, SurfaceView can visually and
        // interactively cover the HDR menu and gesture layer.
        setZOrderMediaOverlay(true)
        // Gestures belong to the Compose GestureOverlay above the video.
        // GLSurfaceView otherwise consumes vertical brightness/volume swipes
        // and horizontal seek gestures before Compose can receive them.
        isClickable = false
        isFocusable = false
        setOnTouchListener { _, _ ->
            parent?.requestDisallowInterceptTouchEvent(false)
            false
        }
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        renderer.requestRender = { requestRender() }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false

    fun setPlayer(next: ExoPlayer) {
        if (player === next) return
        player?.let { oldPlayer ->
            post { oldPlayer.clearVideoSurface() }
        }
        player = next
        renderer.onSurfaceReady = { surface ->
            // Renderer callbacks arrive on GLSurfaceView's GLThread, while
            // ExoPlayer is owned by the app main thread.
            post { next.setVideoSurface(surface) }
        }
        renderer.surface?.let { surface ->
            post { next.setVideoSurface(surface) }
        }
    }

    fun setColorPreset(preset: ColorPreset) {
        renderer.setPreset(preset)
        requestRender()
    }

    override fun onDetachedFromWindow() {
        player?.let { currentPlayer ->
            post { currentPlayer.clearVideoSurface() }
        }
        player = null
        renderer.releaseSurface()
        super.onDetachedFromWindow()
    }

    private class VideoGlRenderer : Renderer, SurfaceTexture.OnFrameAvailableListener {
        private val vertexBuffer: FloatBuffer = floatBuffer(floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f,
        ))
        private val textureMatrix = FloatArray(16)
        private var program = 0
        private var textureId = 0
        private var positionHandle = 0
        private var texCoordHandle = 0
        private var matrixHandle = 0
        private var brightnessHandle = 0
        private var contrastHandle = 0
        private var saturationHandle = 0
        private var redHandle = 0
        private var greenHandle = 0
        private var blueHandle = 0
        private var vibranceHandle = 0
        private var preset = ColorPresets.NATURAL_HDR
        var surface: Surface? = null
            private set
        private var surfaceTexture: SurfaceTexture? = null
        var onSurfaceReady: ((Surface) -> Unit)? = null
        var requestRender: (() -> Unit)? = null

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            matrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
            brightnessHandle = GLES20.glGetUniformLocation(program, "uBrightness")
            contrastHandle = GLES20.glGetUniformLocation(program, "uContrast")
            saturationHandle = GLES20.glGetUniformLocation(program, "uSaturation")
            redHandle = GLES20.glGetUniformLocation(program, "uRed")
            greenHandle = GLES20.glGetUniformLocation(program, "uGreen")
            blueHandle = GLES20.glGetUniformLocation(program, "uBlue")
            vibranceHandle = GLES20.glGetUniformLocation(program, "uVibrance")

            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val texture = SurfaceTexture(textureId)
            surfaceTexture = texture
            texture.setOnFrameAvailableListener(this)
            surface = Surface(texture)
            onSurfaceReady?.invoke(surface!!)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            val currentSurface = surface ?: return
            val texture = surfaceTexture ?: return
            try { texture.updateTexImage() } catch (_: RuntimeException) { return }
            texture.getTransformMatrix(textureMatrix)

            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniformMatrix4fv(matrixHandle, 1, false, textureMatrix, 0)
            GLES20.glUniform1f(brightnessHandle, preset.brightness)
            GLES20.glUniform1f(contrastHandle, preset.contrast)
            GLES20.glUniform1f(saturationHandle, preset.saturation)
            GLES20.glUniform1f(redHandle, preset.red)
            GLES20.glUniform1f(greenHandle, preset.green)
            GLES20.glUniform1f(blueHandle, preset.blue)
            GLES20.glUniform1f(vibranceHandle, preset.vibrance)
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
            requestRender?.invoke()
        }

        fun setPreset(next: ColorPreset) { preset = next }

        fun releaseSurface() {
            surfaceTexture?.release()
            surfaceTexture = null
            surface?.release()
            surface = null
        }

        private fun linkProgram(vertex: String, fragment: String): Int {
            fun compile(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                return shader
            }
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vertex))
            GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
            GLES20.glLinkProgram(program)
            return program
        }

        companion object {
            private const val VERTEX_SHADER = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                uniform mat4 uTexMatrix;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    // SurfaceTexture's transform is already applied; invert the
                    // sampled Y coordinate once so decoded video is upright.
                    vec2 transformed = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
                    vTexCoord = vec2(transformed.x, 1.0 - transformed.y);
                }
            """
            private const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                uniform samplerExternalOES uTexture;
                uniform float uBrightness;
                uniform float uContrast;
                uniform float uSaturation;
                uniform float uRed;
                uniform float uGreen;
                uniform float uBlue;
                uniform float uVibrance;
                varying vec2 vTexCoord;
                void main() {
                    vec4 tex = texture2D(uTexture, vTexCoord);
                    vec3 color = tex.rgb * uBrightness;
                    color = (color - 0.5) * uContrast + 0.5;
                    color.r *= uRed; color.g *= uGreen; color.b *= uBlue;
                    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    color = mix(vec3(luminance), color, uSaturation);
                    float maxChannel = max(color.r, max(color.g, color.b));
                    float minChannel = min(color.r, min(color.g, color.b));
                    float colorfulness = maxChannel - minChannel;
                    color += (color - vec3(luminance)) * (uVibrance - 1.0) * (1.0 - colorfulness);
                    gl_FragColor = vec4(clamp(color, 0.0, 1.0), tex.a);
                }
            """

            private fun floatBuffer(values: FloatArray): FloatBuffer =
                ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(values).position(0)
                }
        }
    }
}
