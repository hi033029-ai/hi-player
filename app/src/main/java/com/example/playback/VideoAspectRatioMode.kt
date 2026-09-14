package com.example.playback

/**
 * Aspect ratio / resize modes, e.g. for the "IMAX Full (1.43:1)" button in the
 * bottom-left of the controls bar.
 *
 * ratio == null means "use the video's own natural aspect ratio" (FIT / CROP both
 * respect it, just differently); a non-null ratio forces that fixed ratio (used for
 * IMAX / 16:9 / 4:3 presets and STRETCH-style modes).
 */
enum class VideoAspectRatioMode(val label: String, val ratio: Float?) {
    FIT("Fit", null),                 // letterbox/pillarbox, whole frame visible
    CROP("Full / Crop", null),        // fills container, edges cropped
    IMAX_FULL("IMAX Full (1.43:1)", 1.43f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    STRETCH("Stretch", null);         // ignores aspect, fills exactly — handled as a flag below

    companion object {
        fun next(current: VideoAspectRatioMode): VideoAspectRatioMode {
            val values = entries
            return values[(values.indexOf(current) + 1) % values.size]
        }
    }
}
