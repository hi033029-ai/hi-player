# Gecko feature restoration — source-only

Gecko remains the browser engine on supported Android versions. The API 24–25 compatibility route is unchanged. No main merge, APK build, validation task, or version increment is part of these source changes.

## Imported useful features

- Full settings entry with account access, app text sizing, existing Gecko appearance/pop-up controls, video detection, desktop mode and declarative filter-data refresh.
- Existing advanced site controls: block/allow hosts, per-site protection pause, supported wait-page speed and auto-continue settings.
- Translation, QR scanning, home-screen shortcuts, default-browser selection and explicit secure-browser routing reuse the app's existing tools.
- Per-site skipping options are delivered before the bundled helper scripts execute. Script code remains APK-bundled; existing Play-variant restrictions remain in force.

## Responsiveness and truthful state

- Network-filter computation and cosmetic CSS construction run on the filter worker, not in the Android UI callback.
- Restored inactive pages defer their network loads until selected; their session wrappers and tab metadata are retained.
- Page-tool protection saves run on the I/O dispatcher and prevent duplicate submissions.
- Helper delivery is reported by the extension and reset per document. Successful delivery does not claim every page supports skipping.
- Filter checking exposes real pending/cancel/result state without invented progress percentages.

## Still requires follow-up

- Complete website prompt coverage beyond the existing file chooser.
- Safe, user-initiated automatic player handoff parity; no nonfunctional auto-play switch has been added.
- Balanced native tracking-protection profiles and measured cold/warm-start, scrolling, tab-switch and memory profiling.
- Approved compilation, existing and new regression tests, and device checks for these changes. Source review is not runtime verification.

Sign-in, cookies, CAPTCHA, checkout, paywalls, DRM and server-enforced waits must not be bypassed by helper behavior. App authentication and website sessions stay separate.
