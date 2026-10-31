# UX feedback and trust review

Scope: key existing loading, onboarding, source-setup, browser, download and
file-action flows. These rules are also in AGENTS.md for future changes.
This is a source update, not a claim that every screen has passed device QA.

## 1. Meaningful loading

- Library list/grid, file manager, Drive file lists and legacy catalog screens
  use non-interactive, layout-shaped skeletons rather than full-screen spinners.
- Skeletons are static (no motion loop) and expose one polite loading label to
  accessibility services instead of announcing decorative bars as real items.
- Existing library content remains visible while refreshing. Its refresh action
  changes to a small busy indicator and rejects duplicate taps.
- Websites have unknown layouts, so the browser retains truthful progress and
  an always-visible Stop loading action, not an invented page layout.
- Playback buffering, tiny action-level indicators and actual byte progress are
  not blindly replaced by skeletons.

## 2. Immediate acknowledgement

- User-initiated library refresh sets its busy flag before requesting scan work.
- Drive list/play requests publish busy/preparing state before asynchronous work;
  navigation clears stale files so rows from a previous folder are not relabeled.
- File rename publishes a Renaming file state before dispatching disk work, guards
  duplicate submissions and clears busy state when its job completes.
- Connection dialogs already change to Connecting immediately; their cancellation
  now remains available. Success is shown only after the actual result.
- Existing delete/extraction/download progress and cancel controls remain intact.

## 3. Consistent onboarding actions

- Four steps share one footer component: 48dp minimum primary action, 20dp horizontal and
  bottom margins, visible secondary action and safe drawing insets. Scrollable
  content reserves footer space. Transitions fade rather than slide buttons.
- Theme/text choices and the current step survive activity recreation. Completing
  onboarding is guarded against duplicate completion callbacks.
- Back is available on theme/text steps (and system Back through intermediate
  steps), Skip setup on welcome, and Not now on optional permissions.
- Skip setup no longer forces a different theme or triggers an automatic
  permission prompt. Declining/deferring permissions can still enter the app;
  allow media access later from the library or Settings.
- Removed the redundant launch-time permission request that followed onboarding.

## 4. No cancellation traps

- Jellyfin/WebDAV setup Cancel, Back/outside dismissal cancel the pending Job;
  disposing the dialog does too. Cancellation is checked before local credential
  storage/callback. This cannot undo a request already received by a remote server.
- Browser loading can be stopped without waiting for the slow-load threshold.
- Existing destructive confirmations are retained. No subscription/billing flow
  was found in the app source; no retention/cancellation funnel was introduced.

## 5. Human-readable errors

- Shared app-owned recovery copy replaces raw exception text in catalog/source
  setup, Drive loading, website sign-in launch and subtitle export.
- Download errors no longer expose HTTP status codes/range/redirect diagnostics.
  Restored failed downloads use a safe Retry/fresh-link message instead of old
  persisted technical error text.
- Browser main-frame network, HTTP and TLS failures use a recovery screen with
  Retry/Home. TLS validation is still cancelled, never bypassed. Website content,
  including a site's own application-level error page returning success, is not
  rewritten or falsely claimed to be under Hi Player's control.
- New diagnostics log only an event and exception class (browser HTTP status is
  internal only), not exception messages, stack traces, URLs or credentials.
- Catalog errors include a visible Try again action. Most catalog screens are
  legacy/retired but still receive the safe error behavior.

## Review and validation status

Source inspection and whitespace/import/caller review only. Added
`UserFacingErrorsTest` source for offline, timeout, cancellation and backend
payload redaction. **No Gradle/compile/lint/unit/instrumentation/APK task run.**
The app version, signing key and workflow are unchanged. Changes stay on the
feature branch until Khan approves a build/merge.

Next approved build/device review must cover:

- Every onboarding step at large font size, narrow/landscape screens, safe insets,
  activity recreation, double taps, permission denial and Not now (no re-prompt).
- Initial library/file/Drive loading, list/grid placeholders, refresh with cached
  content, slow scan, rename failure, repeated taps and job cancellation.
- Offline/timeout/failing sources, HTTP errors, TLS failures and download resume;
  verify recovery actions and make sure no raw technical errors leak into UI.
- Cancel a delayed source connection; confirm no credentials are saved afterward.
  Check log redaction and that existing deletion controls still behave correctly.
- Confirm rendering/accessibility on-device; source review cannot establish
  frame latency, layout fit or runtime correctness.

## Additional continuity and end-to-end polish review

- Explicit bounded tab history replaces the guessed browser origin and hard-coded
  Settings-to-Videos route. Settings has visible/system Back. Child dialogs,
  selections and folder handlers retain priority before outer navigation.
- Active tab/history and the player/main destination use saved state. On activity
  recreation, an existing retained player returns to its player screen. If a new
  process has no live player, restoration falls back to the library rather than
  inventing a playable session. Existing periodic/onStop resume-position saves
  remain unchanged; no private stream URL is persisted for automatic playback.
- A shared saveable-state provider retains tab scroll state; Settings expansion
  and non-sensitive search UI use saved state. Rename text is retained, but stale
  destructive targets are not automatically restored/replayed.
- Source setup and Stream & Cast drafts survive activity recreation in session
  ViewModel memory, including passwords/URLs that must not go into saved-state
  Bundles. Explicit cancel/success clears their draft. Process death does not
  preserve these sensitive drafts; privacy takes precedence over disk persistence.
  Interrupted requests are not automatically resubmitted. Cancelling the stream
  resolver also prevents it from opening a player after dismissal. Closing the
  stream window does not pretend to undo a Cast request already sent remotely.
- Removed artificial startup waits and the browser-entry branding overlay. Real
  persisted-settings loading and real network/media buffering remain truthful.
- Shared header switches to wrapping actions on narrow/large-font layouts; its
  refresh icon now reflects the actual library busy state. Navigation hub scrolls
  and its labels wrap. OS font scaling is no longer capped at 1.5x; header titles
  no longer multiply the app's scale twice.
- Onboarding action heights are minimums, not fixed clip boxes. Footer height is
  measured so scrolling content reserves the real space as labels grow. Corrected
  leftover permission-button arguments found during this source review.
- Added TabHistoryTest source for actual origin, duplicate tab selection, root
  safety and reverse-order Back. Tests have NOT been run.

### Release matrix (pending an approved build/device session)

Verify the real Android app, not HTML imitations, at phone/tablet widths,
landscape, OS font scales 1x/1.5x/2x, the largest app text size and long labels.
Review every core journey: local video/audio play → seek → background/return →
resume; library/files → settings → Back; browser → player → Back; download →
pause/retry/open; source setup → switch apps/rotate → continue/cancel; Google
account/Drive → return to the same settings state. Check footer/label fit,
keyboard visibility, TalkBack and restoration after process death separately
from ordinary app switching. No runtime timing, layout or end-to-end correctness
has been established by these source-only changes.


## Browser appearance and one Hi Player account

- Browser appearance now supplies matching light/dark UI colors and explicit
  WebView host `uiMode` / `isLightTheme`, with guarded algorithmic darkening.
  It does not invert video/images. Sites that opt out of darkening or do not
  support a dark scheme may remain light; older WebViews differ in support.
- Switching theme snapshots each tab's latest Back/Forward history when within
  the existing snapshot limit, then recreates its WebView. Pages reload and
  unsent page input can be lost; the setting explicitly warns about this.
- Webpage text has an 80–200% slider/reset, applied live to all retained views,
  new views and page commits. Fixed-layout pages can limit resizing.
- A distinct 12–24sp app-text slider uses the existing global preference and
  reset. The old 1.5x app-only cap no longer made 22–24sp behave identically;
  OS font scaling is still inherited, not multiplied twice in the browser.
- Desktop requests use the installed Chromium version, matching non-mobile
  client hints when supported and a desktop viewport document script. Mobile
  reload restores the original hints and removes the desktop script. Some sites
  can still choose their own layout; this is not a guarantee about every site.
- Removed the outer bottom-sheet frame and outlined loading progress track;
  these were potential sources of the reported overlay hairline. The screenshot
  cannot establish its only cause; confirm on the reporting device/WebView.
- The old static Google sign-in menu tile opened accounts.google.com, not app
  authentication. It now opens the activity-scoped Firebase account card and
  indicates whether Hi Player is signed in. The same card is in browser settings
  and accessible from the unlocked player's top controls. A signed-in profile is
  shown rather than asking the user to authorize the app again.
- Google website sign-in is a separate, clearly labelled secure Custom Tab
  action. App authorization does not create website cookies; no Firebase tokens
  or Google cookies are imported into WebView. Drive still requests its separate
  read-only permission only on user action.

Added `BrowserAppearancePolicyTest` sources for zoom bounds and preserving the
installed engine's version. **Source review only: no compile, Gradle, lint, tests,
APK, merge, or main push performed for this feature update.**

Next approved device review: dark/light switching on old/new WebView providers,
media colors, all open tabs and Back/Forward, webpage 80/100/200%, app 12/14/24sp
plus large OS fonts and narrow/landscape settings, desktop/mobile reload with
client hints and viewport-sensitive sites, overlay hairline, and app account
sign-in/cancel/sign-out/restart consistency across Settings/Browser/Player.
Verify Google OAuth setup and real sign-in on an approved signed build, and
confirm that a secure website tab remains a distinct session.
