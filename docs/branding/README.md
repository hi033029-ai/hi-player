# Hi Player — Pixel Play

Vector adaptation of the first AI-generated teal/cyan ribbon-and-pixels concept selected by Khan. This is a small-size production adaptation, not an exact bitmap trace or a claim of trademark clearance.

## Master and generation

`tools/generate_app_icon.py` generates `hi-player-icon.svg` and the six Android vector resources from the same 48-unit geometry. Python 3 only; no Gradle or APK build. Five substantial pixel fragments replace the old fine checkerboard. The hollow play aperture is transparent, not a white painted triangle. Two clipped tonal shapes retain the selected concept’s folded ribbon appearance.

The entire silhouette including detached pixels is centered. A 512px transparent browser-rendered preview has alpha bounds `(52, 64, 460, 448)`, centered at `(256, 256)`. Minimum pixel width is 3 master units. Adaptive artwork is scaled and padded inside Android’s central 66dp safe circle; launcher backgrounds remain white. Vector geometry scales without stretching, although very small raster displays naturally lose detail.

## Updated app surfaces

- Shared `hi_player_brand_mark.xml`: headers, onboarding, navigation hub, browser and other callers of `rememberHiPlayerLogoPainter()` / `HiPlayerLogoBadge`.
- Launcher API 24–25: square/round `mipmap-anydpi` vectors.
- Launcher API 26+: updated adaptive foreground, existing background and adaptive XML references.
- API 33+ themed icons: updated monochrome alpha mask.
- System splash and browser shortcut fallback: existing references inherit the replacement.
- Media/download notification small icons: updated white alpha-only mark.

Playback controls, media thumbnails, album art, website favicons and third-party service branding are intentionally unchanged. Historical unused `hi_player_logo.png` and density WebP resources remain in the repository, but active branding callers use the vector master and the supported platform resource selection prefers the anydpi/adaptive variants.

## Artwork checks, not runtime validation

Inspected transparent 512px and 48px, legacy square/round 48px, adaptive circular preview, monochrome master and 24px notification preview. The cutout, substantial pixels, centered margins and mask padding remain visible. Browser SVG rendering was used after ImageMagick’s SVG renderer mishandled clipping; previews must not use that broken render.

Generated XML was parsed for well-formedness and generator outputs checked for reproducibility. No Gradle, compile, lint, unit tests, emulator/device checks, packaging or APK workflow ran for this source-only change. Version and signing key are unchanged. Next approved build must validate Android vector clipping/gradients, launcher masks/themed icons, splash and notifications on actual devices. Launchers may cache the prior icon until an update/refresh.
