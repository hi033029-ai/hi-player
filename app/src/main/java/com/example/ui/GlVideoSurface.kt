package com.example.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Rect
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
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
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var horizontalScrub = false
    private var scrubDeltaMs = 0L
    private var verticalControl = false
    private var leftSide = false
    private var pinchDistance = 0f
    private var longPressHandled = false
    private var presetPopup: PopupWindow? = null
    private var presetDismissRunnable: Runnable? = null
    private var hudPopup: PopupWindow? = null
    private var hudText: TextView? = null
    private var hudBar: VerticalLevelBar? = null
    private var hudDismissRunnable: Runnable? = null
    private var hudIsVolume: Boolean? = null
    private var brightnessLevel = 1f
    private var volumeLevel = 1f // 1.0 = 100%, 2.0 = 200%
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isHdrTap(e.x, e.y)) onSingleTap?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            when {
                e.x < width / 3f -> onDoubleTapLeft?.invoke()
                e.x > width * 2f / 3f -> onDoubleTapRight?.invoke()
                else -> onDoubleTapCenter?.invoke()
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            longPressHandled = true
            if (isHdrTap(e.x, e.y)) {
                onHdrLongPress?.invoke()
            } else {
                onLongPressVideo?.invoke()
            }
        }
    })

    var onBrightnessDelta: ((Float) -> Unit)? = null
    var onVolumeDelta: ((Float) -> Unit)? = null
    var currentBrightnessLevel: (() -> Float)? = null
    var currentVolumeLevel: (() -> Float)? = null
    var onScrubStart: (() -> Unit)? = null
    var onScrubMove: ((Long) -> Unit)? = null
    var onScrubEnd: (() -> Unit)? = null
    var onHdrToggle: (() -> Unit)? = null
    var onHdrLongPress: (() -> Unit)? = null
    var onPresetSelected: ((com.example.player.ColorPreset) -> Unit)? = null
    var onPinchZoom: ((Float) -> Unit)? = null
    var onSingleTap: (() -> Unit)? = null
    var onDoubleTapLeft: (() -> Unit)? = null
    var onDoubleTapCenter: (() -> Unit)? = null
    var onDoubleTapRight: (() -> Unit)? = null
    var onLongPressVideo: (() -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        setPreserveEGLContextOnPause(true)
        // Keep Compose controls and gesture overlays above the video surface.
        // Without the media-overlay ordering, SurfaceView can visually and
        // interactively cover the HDR menu and gesture layer.
        setZOrderMediaOverlay(true)
        // This view is the single input owner for the player. A competing
        // Compose pointer layer or OnTouchListener can cancel vertical swipes
        // before ACTION_MOVE reaches the native gesture state machine.
        isClickable = true
        isFocusable = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        renderer.requestRender = { requestRender() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && w > 0 && h > 0) {
            // Keep the lower edge available for volume/brightness swipes instead
            // of letting gesture-navigation interpret the same touch as Home.
            val exclusionHeight = (160f * resources.displayMetrics.density).toInt()
            systemGestureExclusionRects = listOf(Rect(0, (h - exclusionHeight).coerceAtLeast(0), w, h))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                horizontalScrub = false
                verticalControl = false
                longPressHandled = false
                leftSide = event.x < width / 2f
                scrubDeltaMs = 0L
                // Capture the real baseline at the beginning of every swipe;
                // never restart a gesture from the hardcoded 100% default.
                brightnessLevel = currentBrightnessLevel?.invoke()?.coerceIn(0f, 1f) ?: brightnessLevel
                volumeLevel = currentVolumeLevel?.invoke()?.coerceIn(0f, 2f) ?: volumeLevel
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pinchDistance = pointerDistance(event)
                    horizontalScrub = false
                    verticalControl = false
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && pinchDistance > 0f) {
                    val distance = pointerDistance(event)
                    if (distance > 0f) {
                        onPinchZoom?.invoke((distance / pinchDistance).coerceIn(0.85f, 1.15f))
                        pinchDistance = distance
                    }
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                if (!horizontalScrub && !verticalControl) {
                    if (kotlin.math.abs(dx) > 25f && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        horizontalScrub = true
                        scrubDeltaMs = 0L
                        onScrubStart?.invoke()
                    } else if (kotlin.math.abs(dy) > 25f) {
                        verticalControl = true
                    }
                }
                if (horizontalScrub) {
                    scrubDeltaMs += ((event.x - lastX) * 120L).toLong()
                    onScrubMove?.invoke(scrubDeltaMs)
                } else if (verticalControl && height > 0) {
                    val delta = -(event.y - lastY) / height.toFloat()
                    if (leftSide) {
                        brightnessLevel = (brightnessLevel + delta).coerceIn(0f, 1f)
                        onBrightnessDelta?.invoke(delta)
                        showHud("Brightness ${"%.0f".format(brightnessLevel * 100f)}%", brightnessLevel, false)
                    } else {
                        volumeLevel = (volumeLevel + delta).coerceIn(0f, 2f)
                        onVolumeDelta?.invoke(delta)
                        showHud("Volume ${"%.0f".format(volumeLevel * 100f)}%", volumeLevel / 2f, true)
                    }
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (horizontalScrub) onScrubEnd?.invoke()
                if (!longPressHandled && !horizontalScrub && !verticalControl &&
                    isHdrTap(downX, downY)
                ) {
                    onHdrToggle?.invoke()
                }
                pinchDistance = 0f
                horizontalScrub = false
                verticalControl = false
                scrubDeltaMs = 0L
                return true
            }
        }
        return true
    }

    private fun isHdrTap(x: Float, y: Float): Boolean {
        val d = resources.displayMetrics.density
        val rowBottom = height - 88f * d
        val rowTop = height - 172f * d
        // Rotate, PiP, HDR: the HDR icon is the third utility action from the left.
        return y in rowTop..rowBottom && x in (80f * d)..(176f * d)
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun showPresetPopup(selectedName: String) {
        dismissPresetPopup()
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.argb(235, 0, 0, 0))
            setPadding(16, 8, 16, 8)
        }
        ColorPresets.ALL.forEach { preset ->
            val item = TextView(context).apply {
                text = preset.name
                textSize = 14f
                setTextColor(if (preset.name == selectedName) android.graphics.Color.CYAN else android.graphics.Color.WHITE)
                setPadding(20, 14, 20, 14)
                setOnClickListener {
                    onPresetSelected?.invoke(preset)
                    dismissPresetPopup()
                }
            }
            list.addView(item)
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(list)
        }
        presetPopup = PopupWindow(scroll, 720, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 12f
            // The HDR icon is in the left utility group above the seek bar.
            // Place the horizontal choices directly above it.
            showAtLocation(this@GlVideoSurface, Gravity.BOTTOM or Gravity.START, 28, 205)
        }
        presetDismissRunnable = Runnable { dismissPresetPopup() }
            .also { postDelayed(it, 3_000L) }
    }

    fun dismissPresetPopup() {
        presetDismissRunnable?.let(::removeCallbacks)
        presetDismissRunnable = null
        presetPopup?.dismiss()
        presetPopup = null
    }

    private fun showHud(label: String, normalizedProgress: Float, isVolume: Boolean) {
        if (hudPopup != null && hudIsVolume != isVolume) {
            hudDismissRunnable?.let(::removeCallbacks)
            hudPopup?.dismiss()
            hudPopup = null
            hudText = null
            hudBar = null
        }
        val color = if (isVolume && volumeLevel > 1f) {
            if (volumeLevel >= 1.8f) android.graphics.Color.RED else 0xFFFFA000.toInt()
        } else {
            android.graphics.Color.CYAN
        }
        if (hudPopup == null) {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundColor(android.graphics.Color.argb(215, 0, 0, 0))
                setPadding(12, 16, 12, 16)
            }
            hudText = TextView(context).apply {
                textSize = 16f
                gravity = Gravity.CENTER
            }
            hudBar = VerticalLevelBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(18, 230).apply { topMargin = 12 }
            }
            container.addView(hudText)
            container.addView(hudBar)
            hudPopup = PopupWindow(
                container,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                false,
            ).apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                isTouchable = false
                elevation = 10f
                val sideGravity = if (isVolume) Gravity.END else Gravity.START
                val edgeOffset = (20f * resources.displayMetrics.density).toInt()
                showAtLocation(this@GlVideoSurface, sideGravity or Gravity.CENTER_VERTICAL, edgeOffset, 0)
            }
            hudIsVolume = isVolume
        }
        hudText?.apply {
            text = label
            setTextColor(color)
        }
        hudBar?.apply {
            setLevel(normalizedProgress)
            setIndicatorColor(color)
        }
        hudDismissRunnable?.let(::removeCallbacks)
        hudDismissRunnable = Runnable {
            hudPopup?.dismiss()
            hudPopup = null
            hudText = null
            hudBar = null
            hudDismissRunnable = null
            hudIsVolume = null
        }.also { postDelayed(it, 850L) }
    }

    private class VerticalLevelBar(context: Context) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(100, 255, 255, 255)
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var level = 0f

        fun setLevel(nextLevel: Float) {
            level = nextLevel.coerceIn(0f, 1f)
            invalidate()
        }

        fun setIndicatorColor(color: Int) {
            fillPaint.color = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = width / 2f
            val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(bounds, radius, radius, backgroundPaint)
            val fillTop = height * (1f - level)
            val fillBounds = RectF(0f, fillTop, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(fillBounds, radius, radius, fillPaint)
        }
    }

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
        dismissPresetPopup()
        hudDismissRunnable?.let(::removeCallbacks)
        hudDismissRunnable = null
        hudPopup?.dismiss()
        hudPopup = null
        hudText = null
        hudBar = null
        hudIsVolume = null
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
