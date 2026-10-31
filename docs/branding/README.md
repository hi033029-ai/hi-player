# Hi Player pixel-play icon

A vector redraw of the supplied teal pixel-play artwork, optically simplified
for small app icons rather than a shrunken copy of the white-padded photograph.
The gradient, hollow play triangle and dissolving/checkerboard edge are retained.
Tiny isolated fragments are consolidated into larger squares; the smallest
colored squares are 2 units on the 48-unit master grid. Details smaller than a
physical pixel cannot all survive at notification size, so the alpha-only
variant intentionally uses fewer fragments.

## Source of truth

`tools/generate_app_icon.py` generates `hi-player-icon.svg` and every Android
vector/adaptive resource from the same geometry. Run with Python 3 from any
working directory. It does not run Gradle or build an APK.

## Surfaces

- In-app headers, navigation hub, onboarding, splash and browser: the shared
  `rememberHiPlayerLogoPainter()` now loads `hi_player_brand_mark.xml` as a
  vector. `HiPlayerLogoBadge` provides a simple white backing without the old
  blue double border/glow. Removed the old density-scaled bitmap/preload cache.
- Android 7 / API 24–25 launcher: `mipmap-anydpi/ic_launcher*.xml`; vector
  resources take precedence over historical density-specific WebP resources.
- Android 8+ launcher: padded 108-unit adaptive foreground and white background.
  Artwork stays within the central 66-unit safe circle. Round and squircle
  launchers share the same artwork without stretching it.
- Android 13+ themed icons: a separate monochrome alpha mask, not a colored
  layer used as a mask. The `monochrome` tag is in the v33 resources only.
- Android system splash/task icon: inherits the application's existing launcher
  resource references. No app-ID or manifest icon-reference change is needed.
- Browser shortcut fallback: already uses `R.mipmap.ic_launcher`, so inherits
  the replacement. Site favicons remain site-specific.
- Playback and browser-download notifications: `hi_player_notification.xml`,
  a transparent white-only icon for Android's status-bar tinting. Existing
  Media3 notification defaults are preserved apart from its small icon.
- Playback control buttons, album artwork, third-party logos and website
  favicons are not app branding and are intentionally unchanged.

Historical `hi_player_logo.png` and density-specific launcher WebP files remain
in the repository, but have no active branding callers/resource selection on
supported API levels. They are not the vector master.

## Review performed (source-only)

- Inspected the vector at 512, 48 and 32 pixels.
- Inspected 48-pixel legacy square/round and circular adaptive previews.
- Inspected the monochrome themed mask at 48 pixels and notification at 24.
- Parsed generated XML for well-formedness, checked resource references and
  verified generator output is reproducible.
- Checked the notification-provider API against the project's Media3 1.5.1
  source. No Gradle compile, lint, unit tests, packaging or APK workflow run.

These are artwork/source checks, not Android runtime validation. Next approved
build should check API 24, API 26 and API 33+ launcher/system splash behavior,
light/dark in-app surfaces, themed icons and both notification types on-device.
Launchers can cache old icons until the app updates or the launcher refreshes.
The app version and signing key are unchanged on this source-only branch.
