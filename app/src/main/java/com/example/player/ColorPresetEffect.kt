package com.example.player

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Media3 supplies linear BT.2020 HDR (FP16); SDR uses its configured working space.
 * Transfer/range conversion and any required tone mapping have ONE owner: Media3.
 * Never feed this shader a PQ-coded external OES texture or enable an SDR VLC filter.
 */
@UnstableApi
class ColorPresetEffect(initial: ColorPreset) : GlEffect {
    // The UI publishes immutable settings; each GL frame sees a single complete grade.
    @Volatile private var current = initial
    internal fun update(preset: ColorPreset) { current = preset }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Shader(useHdr) { current }

    private class Shader(private val hdr: Boolean, private val settings: () -> ColorPreset) : BaseGlShaderProgram(hdr, 1) {
        private val program = try {
            GlProgram(VERTEX, FRAGMENT).apply {
                setBufferAttribute("aPosition", GlUtil.getNormalizedCoordinateBounds(), 4)
            }
        } catch (error: GlUtil.GlException) { throw VideoFrameProcessingException(error) }

        private var width = 1
        private var height = 1

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            width = inputWidth; height = inputHeight
            return Size(width, height)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val preset = settings()
                program.use()
                program.setSamplerTexIdUniform("uTexture", inputTexId, 0)
                program.setFloatsUniform("uGain", floatArrayOf(preset.red, preset.green, preset.blue))
                program.setFloatsUniform("uLuma", if (hdr) floatArrayOf(.2627f, .6780f, .0593f) else floatArrayOf(.2126f, .7152f, .0722f))
                program.setFloatUniform("uBrightness", preset.brightness)
                program.setFloatUniform("uContrast", preset.contrast)
                program.setFloatUniform("uSaturation", preset.saturation * preset.vibrance)
                program.setFloatUniform("uSharpness", preset.sharpness)
                program.setFloatUniform(
                    "uHdrMidtoneLift",
                    if (hdr && !preset.isIdentityTransform()) GRADED_HDR_MIDTONE_LIFT else 1f,
                )
                program.setFloatsUniform("uTexel", floatArrayOf(1f / width, 1f / height))
                val targets = preset.targetAdjustments.take(8).toMutableList()
                if (targets.isEmpty() && preset.targetHue >= 0) targets.add(ColorTargetAdjustment(preset.targetHue, preset.red, preset.green, preset.blue))
                program.setIntUniform("uTargetCount", targets.size)
                repeat(8) { i ->
                    val t = targets.getOrNull(i)
                    program.setFloatsUniform("uTarget$i", floatArrayOf(t?.hue ?: -1f, t?.red ?: 1f, t?.green ?: 1f, t?.blue ?: 1f))
                    program.setFloatUniform("uTargetSat$i", t?.saturation ?: 1f)
                }
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtil.checkGlError()
            } catch (error: GlUtil.GlException) { throw VideoFrameProcessingException(error) }
        }

        override fun release() {
            super.release()
            try { program.delete() } catch (error: GlUtil.GlException) { throw VideoFrameProcessingException(error) }
        }
    }

    private companion object {
        const val VERTEX = """
            attribute vec4 aPosition;
            varying vec2 vTexCoord;
            void main() { gl_Position = aPosition; vTexCoord = (aPosition.xy + 1.0) * 0.5; }
        """

        const val FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec3 uGain, uLuma;
            uniform float uBrightness, uContrast, uSaturation, uSharpness, uHdrMidtoneLift;
            uniform vec2 uTexel;
            uniform int uTargetCount;
            uniform vec4 uTarget0, uTarget1, uTarget2, uTarget3, uTarget4, uTarget5, uTarget6, uTarget7;
            uniform float uTargetSat0, uTargetSat1, uTargetSat2, uTargetSat3, uTargetSat4, uTargetSat5, uTargetSat6, uTargetSat7;
            varying vec2 vTexCoord;

            vec3 target(vec3 c, vec4 t, float targetSat) {
                float hi = max(c.r, max(c.g, c.b)), lo = min(c.r, min(c.g, c.b));
                float d = hi - lo;
                if (d < 0.00001 || hi < 0.00001) return c;
                float h = hi == c.r ? mod((c.g-c.b)/d, 6.0) : (hi == c.g ? (c.b-c.r)/d+2.0 : (c.r-c.g)/d+4.0);
                h = fract(h/6.0+1.0);
                float distance = abs(h-t.x); distance = min(distance, 1.0-distance);
                float w = (1.0-smoothstep(0.025, 0.075, distance))*smoothstep(0.14, 0.32, d/hi)*smoothstep(0.03, 0.10, hi);
                vec3 adj = c * mix(vec3(1.0), t.yzw, w);
                float l = dot(adj, uLuma);
                return max(vec3(0.0), mix(vec3(l), adj, mix(1.0, targetSat, w)));
            }

            void main() {
                vec4 pixel = texture2D(uTexture, vTexCoord);
                vec3 c = pixel.rgb;
                if (uSharpness > 0.0001) {
                    vec3 mean = (texture2D(uTexture, vTexCoord+vec2(uTexel.x,0)).rgb
                                +texture2D(uTexture, vTexCoord-vec2(uTexel.x,0)).rgb
                                +texture2D(uTexture, vTexCoord+vec2(0,uTexel.y)).rgb
                                +texture2D(uTexture, vTexCoord-vec2(0,uTexel.y)).rgb)*0.25;
                    c = max(vec3(0.0), c+(c-mean)*uSharpness);
                }
                if (uTargetCount>0) c=target(c,uTarget0,uTargetSat0);
                if (uTargetCount>1) c=target(c,uTarget1,uTargetSat1);
                if (uTargetCount>2) c=target(c,uTarget2,uTargetSat2);
                if (uTargetCount>3) c=target(c,uTarget3,uTargetSat3);
                if (uTargetCount>4) c=target(c,uTarget4,uTargetSat4);
                if (uTargetCount>5) c=target(c,uTarget5,uTargetSat5);
                if (uTargetCount>6) c=target(c,uTarget6,uTargetSat6);
                if (uTargetCount>7) c=target(c,uTarget7,uTargetSat7);
                if (uTargetCount==0) c*=uGain;
                c*=uBrightness;
                float preContrastLuma = max(dot(c,uLuma),0.0);
                float hdrLift = mix(
                    uHdrMidtoneLift,
                    1.0,
                    smoothstep(0.10,0.75,preContrastLuma)
                );
                c *= hdrLift;
                float luma = max(dot(c,uLuma),0.0);
                float k = max(uContrast,0.01);
                float contrastGain = (k*luma+0.18)/(luma+0.18*k);
                if (k >= 1.0) contrastGain = max(1.0, contrastGain);
                c *= contrastGain;
                c=mix(vec3(dot(c,uLuma)),c,uSaturation);
                gl_FragColor=vec4(max(c,vec3(0.0)),pixel.a);
            }
        """
    }
}

internal const val GRADED_HDR_MIDTONE_LIFT = 1.25f
