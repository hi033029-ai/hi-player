package com.example.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.SingleFrameGlShaderProgram
import android.content.Context
import android.opengl.GLES20

/**
 * Media3's effect library has ready-made Builders for exposure (RgbAdjustment) and
 * saturation (HslAdjustment), but no equivalent for sharpness — there's nothing to
 * configure with a simple Builder call. This is a small custom GL effect instead: a
 * 3x3 box blur blended with the original frame, which softens fine detail (the
 * inverse of an unsharp mask) without fully blurring the image.
 *
 * `strength` is 0f (no change) to 1f (full blur, no original detail); 0.3–0.4 is a
 * mild, natural-looking softening — enough to tone down over-sharpened HDR sources
 * without looking blurry.
 *
 * NOTE ON API STABILITY: unlike RgbAdjustment/HslAdjustment (stable public Builders),
 * this touches SingleFrameGlShaderProgram's constructor/method signatures directly,
 * which move more across media3-effect versions than the simple effect Builders do.
 * If this doesn't compile as-is, check SingleFrameGlShaderProgram's current
 * constructor and drawFrame()/configure() signatures for your installed version —
 * the shader logic itself (the GLSL below) will still be correct regardless.
 */
@UnstableApi
class SharpnessReductionEffect(private val strength: Float = 0.35f) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return BoxBlurShaderProgram(useHdr, strength)
    }

    private class BoxBlurShaderProgram(
        useHdr: Boolean,
        private val strength: Float,
    ) : SingleFrameGlShaderProgram(useHdr) {

        private var texelWidth = 0f
        private var texelHeight = 0f

        override fun configure(inputWidth: Int, inputHeight: Int): android.util.Size {
            texelWidth = 1f / inputWidth
            texelHeight = 1f / inputHeight
            return android.util.Size(inputWidth, inputHeight)
        }

        // Fragment shader: samples a 3x3 neighborhood, averages it (box blur), then
        // mixes that blurred result with the original sample by `strength` — a
        // partial blur reads as "softer/less sharp" rather than fully out of focus.
        private val fragmentShader = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uStrength;
            varying vec2 vTexSamplingCoord;

            void main() {
              vec4 original = texture2D(uTexSampler, vTexSamplingCoord);
              vec4 sum = vec4(0.0);
              for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                  vec2 offset = vec2(float(dx) * uTexelSize.x, float(dy) * uTexelSize.y);
                  sum += texture2D(uTexSampler, vTexSamplingCoord + offset);
                }
              }
              vec4 blurred = sum / 9.0;
              gl_FragColor = mix(original, blurred, uStrength);
            }
        """.trimIndent()

        // NOTE: wiring this fragment shader into GlProgram/drawFrame() follows the
        // same pattern Media3's own built-in shader-based effects use internally
        // (compile via GlProgram, bind uTexelSize/uStrength as uniforms each frame,
        // draw a full-screen quad). Left as the one piece to fill in against your
        // exact media3-effect version's GlProgram helper API, since that binding
        // layer is exactly where method names have moved between releases.
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            TODO(
                "Compile `fragmentShader` above via your media3-effect version's " +
                    "GlProgram helper, bind uTexelSize=($texelWidth,$texelHeight) and " +
                    "uStrength=$strength as uniforms, then draw the full-screen quad " +
                    "— same pattern as Media3's built-in shader effects."
            )
        }
    }
}
