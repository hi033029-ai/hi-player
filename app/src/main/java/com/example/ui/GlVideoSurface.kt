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
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.SeekParameters
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
    private var gestureStartBrightness = 0.5f
    private var gestureStartVolume = 1f
    private var pinchDistance = 0f
    private var pinchActive = false
    private var suppressSingleFingerAfterPinch = false
    private var longPressHandled = false
    private var holdSeekDirection = 0
    private var holdSeekRunnable: Runnable? = null
    private var holdSeekWasPlaying = false
    private var holdSeekPreviousSpeed = 1f
    private var holdSeekPreviousParameters = SeekParameters.DEFAULT
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
            } else if (e.x < width / 3f) {
                beginHoldSeek(-1)
            } else if (e.x > width * 2f / 3f) {
                beginHoldSeek(1)
            } else {
                onLongPressVideo?.invoke()
            }
        }
    })

    var onBrightnessDelta: ((Float) -> Unit)? = null
    var onVolumeDelta: ((Float) -> Unit)? = null
    var onBrightnessLevel: ((Float) -> Unit)? = null
    var onVolumeLevel: ((Float) -> Unit)? = null
    var currentBrightnessLevel: (() -> Float)? = null
    var currentVolumeLevel: (() -> Float)? = null
    var onScrubStart: (() -> Unit)? = null
    var onScrubMove: ((Long) -> Unit)? = null
    var onScrubEnd: (() -> Unit)? = null
    var isInteractionLocked: (() -> Boolean)? = null
    var onHdrToggle: (() -> Unit)? = null
    var onHdrLongPress: (() -> Unit)? = null
    var onPresetSelected: ((com.example.player.ColorPreset) -> Unit)? = null
    var onPinchZoom: ((Float) -> Unit)? = null
    var onPinchStart: (() -> Unit)? = null
    var onPinchEnd: (() -> Unit)? = null
    var onSingleTap: (() -> Unit)? = null
    var onDoubleTapLeft: (() -> Unit)? = null
    var onDoubleTapCenter: (() -> Unit)? = null
    var onDoubleTapRight: (() -> Unit)? = null
    var onLongPressVideo: (() -> Unit)? = null
    var onHoldSeekStart: ((Boolean) -> Unit)? = null
    var onHoldSeekEnd: (() -> Unit)? = null

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
        if (isInteractionLocked?.invoke() == true) {
            // The Compose unlock control remains above this media overlay; the
            // native surface must not run seek, zoom, HUD, or playback gestures.
            return true
        }
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
                gestureStartBrightness = brightnessLevel
                gestureStartVolume = volumeLevel
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pinchDistance = pointerDistance(event)
                    pinchActive = true
                    suppressSingleFingerAfterPinch = false
                    onPinchStart?.invoke()
                    horizontalScrub = false
                    verticalControl = false
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (pinchActive) {
                    pinchDistance = 0f
                    pinchActive = false
                    suppressSingleFingerAfterPinch = true
                    horizontalScrub = false
                    verticalControl = false
                    onPinchEnd?.invoke()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinchActive && event.pointerCount >= 2 && pinchDistance > 0f) {
                    val distance = pointerDistance(event)
                    if (distance > 0f) {
                        onPinchZoom?.invoke((distance / pinchDistance).coerceIn(0.85f, 1.15f))
                        pinchDistance = distance
                    }
                    return true
                }
                if (pinchActive || suppressSingleFingerAfterPinch) return true
                if (holdSeekDirection != 0) return true
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
                    // Base the entire gesture on the level at ACTION_DOWN.
                    // Reading the system value on every MOVE causes a visible
                    // jump when a new swipe begins or changes direction.
                    val totalDelta = -(event.y - downY) / height.toFloat()
                    if (leftSide) {
                        brightnessLevel = (gestureStartBrightness + totalDelta).coerceIn(0f, 1f)
                        onBrightnessLevel?.invoke(brightnessLevel)
                        showHud(
                            "Brightness ${(brightnessLevel * 100f).toInt()}%",
                            brightnessLevel,
                            false,
                        )
                    } else {
                        volumeLevel = (gestureStartVolume + totalDelta).coerceIn(0f, 2f)
                        onVolumeLevel?.invoke(volumeLevel)
                        showHud(
                            "Volume ${(volumeLevel * 100f).toInt()}%",
                            volumeLevel / 2f,
                            true,
                        )
                    }
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pinchActive) {
                    pinchDistance = 0f
                    pinchActive = false
                    onPinchEnd?.invoke()
                }
                if (horizontalScrub) onScrubEnd?.invoke()
                endHoldSeek()
                if (!longPressHandled && !horizontalScrub && !verticalControl &&
                    isHdrTap(downX, downY)
                ) {
                    onHdrToggle?.invoke()
                }
                pinchDistance = 0f
                horizontalScrub = false
                verticalControl = false
                suppressSingleFingerAfterPinch = false
                scrubDeltaMs = 0L
                return true
            }
        }
        return true
    }

    private fun beginHoldSeek(direction: Int) {
        endHoldSeek()
        holdSeekDirection = direction
        player?.let { current ->
            holdSeekWasPlaying = current.isPlaying
            holdSeekPreviousSpeed = current.playbackParameters.speed
            holdSeekPreviousParameters = current.seekParameters
            // Reverse playback is emulated with timed backward seeks. Use
            // exact targets at a lower cadence so each target has time to
            // decode; very frequent keyframe seeks can repeatedly land on the
            // same GOP and make rewind appear not to move.
            current.setSeekParameters(SeekParameters.EXACT)
            if (direction > 0) {
                // Forward playback is genuinely continuous instead of a series
                // of visible seek jumps.
                current.playbackParameters = PlaybackParameters(4f)
                current.play()
            } else {
                // Media3 does not support reverse playback. Use small exact
                // seeks at a video-clock cadence so rewind appears continuous.
                current.pause()
            }
        }
        onHoldSeekStart?.invoke(direction > 0)
        val runner = object : Runnable {
            override fun run() {
                val current = player ?: return
                if (direction < 0) {
                    // Media3 has no reverse playback mode. Apply a 1-second
                    // reverse step every 500 ms for a visible 2x rewind.
                    val next = (current.currentPosition - 1_000L).coerceAtLeast(0L)
                    current.seekTo(next)
                }
                postDelayed(this, 500L)
            }
        }
        holdSeekRunnable = runner
        post(runner)
    }

    private fun endHoldSeek() {
        holdSeekRunnable?.let(::removeCallbacks)
        holdSeekRunnable = null
        if (holdSeekDirection != 0) {
            player?.let { current ->
                current.playbackParameters = PlaybackParameters(holdSeekPreviousSpeed)
                current.setSeekParameters(holdSeekPreviousParameters)
                if (holdSeekWasPlaying) current.play() else current.pause()
            }
            onHoldSeekEnd?.invoke()
        }
        holdSeekDirection = 0
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

    fun dismissHud() {
        hudDismissRunnable?.let(::removeCallbacks)
        hudDismissRunnable = null
        hudPopup?.dismiss()
        hudPopup = null
        hudText = null
        hudBar = null
        hudIsVolume = null
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
                // Keep the meter floating over the video; the previous black
                // panel obscured the picture during brightness/volume swipes.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setPadding(dp(8), dp(9), dp(8), dp(9))
            }
            hudText = TextView(context).apply {
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            hudBar = VerticalLevelBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(150))
            }
            container.addView(hudText)
            val meterRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, 0)
            }
            meterRow.addView(hudBar)
            container.addView(meterRow)
            hudPopup = PopupWindow(
                container,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                false,
            ).apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                isTouchable = false
                elevation = 10f
                // Use physical edges rather than relative START/END so volume
                // remains on the right on every device/layout direction.
                val sideGravity = if (isVolume) Gravity.RIGHT else Gravity.LEFT
                val edgeOffset = (20f * resources.displayMetrics.density).toInt()
                showAtLocation(this@GlVideoSurface.rootView, sideGravity or Gravity.CENTER_VERTICAL, edgeOffset, 0)
            }
            hudIsVolume = isVolume
        }
        hudText?.apply {
            text = label.replace(" ", "\n")
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
        }.also { postDelayed(it, 1_300L) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
            val inset = width * 0.22f
            val radius = (width - inset * 2f) / 2f
            val bounds = RectF(inset, 0f, width - inset, height.toFloat())
            canvas.drawRoundRect(bounds, radius, radius, backgroundPaint)
            val fillTop = height * (1f - level)
            val fillBounds = RectF(inset, fillTop, width - inset, height.toFloat())
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
        endHoldSeek()
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
        private var targetHueHandle = 0
        private var vibranceHandle = 0
        private var sharpnessHandle = 0
        private var targetCountHandle = 0
        private var targetHuesHandle = 0
        private var targetRedsHandle = 0
        private var targetGreensHandle = 0
        private var targetBluesHandle = 0
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
            targetHueHandle = GLES20.glGetUniformLocation(program, "uTargetHue")
            vibranceHandle = GLES20.glGetUniformLocation(program, "uVibrance")
            sharpnessHandle = GLES20.glGetUniformLocation(program, "uSharpness")
            targetCountHandle = GLES20.glGetUniformLocation(program, "uTargetCount")
            targetHuesHandle = GLES20.glGetUniformLocation(program, "uTargetHues[0]")
            targetRedsHandle = GLES20.glGetUniformLocation(program, "uTargetReds[0]")
            targetGreensHandle = GLES20.glGetUniformLocation(program, "uTargetGreens[0]")
            targetBluesHandle = GLES20.glGetUniformLocation(program, "uTargetBlues[0]")

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
            GLES20.glUniform1f(targetHueHandle, preset.targetHue)
            GLES20.glUniform1f(vibranceHandle, preset.vibrance)
            GLES20.glUniform1f(sharpnessHandle, preset.sharpness)
            val adjustments = preset.targetAdjustments.take(MAX_TARGET_ADJUSTMENTS)
            GLES20.glUniform1i(targetCountHandle, adjustments.size)
            if (adjustments.isNotEmpty()) {
                GLES20.glUniform1fv(targetHuesHandle, adjustments.size, adjustments.map { it.hue }.toFloatArray(), 0)
                GLES20.glUniform1fv(targetRedsHandle, adjustments.size, adjustments.map { it.red }.toFloatArray(), 0)
                GLES20.glUniform1fv(targetGreensHandle, adjustments.size, adjustments.map { it.green }.toFloatArray(), 0)
                GLES20.glUniform1fv(targetBluesHandle, adjustments.size, adjustments.map { it.blue }.toFloatArray(), 0)
            }
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
            private const val MAX_TARGET_ADJUSTMENTS = 8
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
                uniform float uTargetHue;
                uniform float uVibrance;
                uniform float uSharpness;
                uniform int uTargetCount;
                uniform float uTargetHues[8];
                uniform float uTargetReds[8];
                uniform float uTargetGreens[8];
                uniform float uTargetBlues[8];
                varying vec2 vTexCoord;
                void main() {
                    vec4 tex = texture2D(uTexture, vTexCoord);
                    vec3 color = tex.rgb * uBrightness;
                    color = (color - 0.5) * uContrast + 0.5;
                    float maxChannel = max(color.r, max(color.g, color.b));
                    float minChannel = min(color.r, min(color.g, color.b));
                    float chroma = maxChannel - minChannel;
                    float hue = 0.0;
                    if (chroma > 0.001) {
                        if (maxChannel == color.r) hue = mod((color.g - color.b) / chroma, 6.0);
                        else if (maxChannel == color.g) hue = ((color.b - color.r) / chroma) + 2.0;
                        else hue = ((color.r - color.g) / chroma) + 4.0;
                        hue /= 6.0;
                        if (hue < 0.0) hue += 1.0;
                    }
                    if (uTargetCount > 0) {
                        for (int i = 0; i < 8; i++) {
                            if (i >= uTargetCount) break;
                            float hueDistance = abs(hue - uTargetHues[i]);
                            hueDistance = min(hueDistance, 1.0 - hueDistance);
                            float hueMask = (1.0 - smoothstep(0.02, 0.20, hueDistance)) * smoothstep(0.01, 0.08, chroma);
                            vec3 adjustedColor = color;
                            adjustedColor.r *= uTargetReds[i];
                            adjustedColor.g *= uTargetGreens[i];
                            adjustedColor.b *= uTargetBlues[i];
                            color = mix(color, adjustedColor, hueMask);
                        }
                    } else {
                        vec3 originalColor = color;
                        vec3 adjustedColor = color;
                        adjustedColor.r *= uRed; adjustedColor.g *= uGreen; adjustedColor.b *= uBlue;
                        float hueDistance = abs(hue - uTargetHue);
                        hueDistance = min(hueDistance, 1.0 - hueDistance);
                        float hueMask = uTargetHue < 0.0 ? 1.0 : (1.0 - smoothstep(0.02, 0.20, hueDistance)) * smoothstep(0.01, 0.08, chroma);
                        color = mix(originalColor, adjustedColor, hueMask);
                    }
                    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    color = mix(vec3(luminance), color, uSaturation);
                    float adjustedMaxChannel = max(color.r, max(color.g, color.b));
                    float adjustedMinChannel = min(color.r, min(color.g, color.b));
                    float colorfulness = adjustedMaxChannel - adjustedMinChannel;
                    color += (color - vec3(luminance)) * (uVibrance - 1.0) * (1.0 - colorfulness);
                    // A small four-tap unsharp mask keeps the control lightweight
                    // while making the sharpness slider visibly affect the frame.
                    vec2 pixel = vec2(0.0015, 0.0015);
                    vec3 blur = (
                        texture2D(uTexture, vTexCoord + vec2(pixel.x, 0.0)).rgb +
                        texture2D(uTexture, vTexCoord - vec2(pixel.x, 0.0)).rgb +
                        texture2D(uTexture, vTexCoord + vec2(0.0, pixel.y)).rgb +
                        texture2D(uTexture, vTexCoord - vec2(0.0, pixel.y)).rgb
                    ) * 0.25;
                    color += (color - blur) * uSharpness;
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
