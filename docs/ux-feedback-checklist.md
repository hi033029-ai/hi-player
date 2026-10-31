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
- Browser main-frame loading uses a neutral content-shaped skeleton until real
  content paints, not a blank progress-only screen. It is explicitly a placeholder,
  not a claim to know a third-party site's layout. Stop/Retry remain available.
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


## Header alignment follow-up after 1.0.72

- Removed the automatic narrow/large-font two-row header. Logo, title, refresh,
  optional search/cast/recycle-bin and account actions now share one vertically
  centered row. The icon geometry is unchanged: its 48-unit artwork is already
  centered in its square; this corrects its position in the header as a whole.
- Fixed-size 48dp action targets stay visible; the decorative brand title takes
  only remaining width and may ellipsize rather than push controls below it.
  App/OS text scaling remains enabled and the row may grow in height.
- Removed the extra in-content Back/Settings row per the annotated screenshot.
  Settings retains its BackHandler and actual visited-tab history.
- Source review only; no Gradle, compile, tests, APK or main merge for this
  follow-up. Next approved device check: Settings and all header callers at
  320/360/393/600dp, 12/14/24sp app text and large OS fonts, with/without Search,
  account signed in/out, refresh busy, Files recycle-bin action, dark/light,
  landscape, and system Back to the actual prior destination.


## Webpage skeleton and website-account clarification

- Replaced the browser's blank, progress-only loading overlay with static page
  skeleton blocks: header placeholder, a large content placeholder, text bars
  and thumbnail/text rows. There is no shimmer, fake loaded content, fabricated
  percentage or pretend website preview. Theme-aware placeholders are one
  non-interactive accessibility group; Stop/Retry are separate live controls.
- Trigger the skeleton on ordinary main-frame navigation/reload as well as new
  tabs. Clear it on real visible content, completion/error or explicit Stop.
  Removed the timer that revealed a potentially blank WebView after 20 seconds;
  slow-load status and Retry remain until a real outcome. Bottom-bar Stop also
  clears the skeleton.
- Website-sign-in requests now show the current Firebase name, email/photo and
  an explicit 'Signed in to Hi Player' label. A signed-out app can open its
  official app sign-in flow. The shorter prompt no longer implies that app
  authorization signs in YouTube or another embedded website. Private-mode
  handoff warnings and explicit Continue/Cancel remain.
- This DOES NOT sign embedded YouTube in. Firebase app authentication cannot
  create a Google website session. Google website sign-in remains in supported
  Custom Tabs, whose account chooser/sessions are controlled by that browser.
  No passwords, app tokens or browser cookies are imported/exported to make
  the app appear signed in on a website; no Google client-ID/UA spoofing.
- Source inspection only; no Gradle/compiler/tests/APK/main merge for this
  follow-up. Next approved device review: fresh/link/reload/redirect/Back/tab
  navigations, paint vs slow/offline/error/Stop/Retry, narrow/landscape and large
  fonts, light/dark skeletons, auth popup/direct/menu routes, actual app profile
  updates/sign-out, private-tab warning, provider absence and real browser
  website sign-in. Embedded-vs-external session separation must remain visible.
