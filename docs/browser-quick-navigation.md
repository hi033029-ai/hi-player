# Browser quick navigation and local suggestions

Source-only changes after v1.0.67-demo (68); no new APK or tests run.

- Swipe upward from the bottom edge of a webpage, start page, or full-screen Downloads/History/Bookmarks library to reveal the existing Videos/Music/Browser/Files/Settings navigation. An accessible up-arrow provides the same action. Overlay does not resize WebView or close tabs. Navigation hides after five seconds without touch activity; keyboard, PiP and website fullscreen suppress it.
- Local history suggestions appear above the address/search bar while typing, bounded to four matching recent entries. Selecting one dismisses the keyboard and opens it. Private tabs neither save visits nor expose regular-history suggestions. Existing clear/remove history controls refresh suggestions.
- Final URLs/titles and SPA URL changes refresh stored visits. Only actual accepted main-frame WebView redirects can retire an older history URL in favor of an HTTPS final address. No guessed domain substitution, HTTP downgrade, or silent cross-domain bookmark rewrite.
- Removed demo information/privacy buttons from the account UI and removed demo wording from the Drive sheet. Kept truthful permission guidance and repository privacy documentation: deleting a notice cannot remove Google's restricted-scope review requirement.

Google access block remains unresolved pending the exact Google error/screenshot. Do not claim approval, add unknown test users, switch production/testing audience, broaden scopes, or bypass security checks without determining the cause and obtaining any required authorization.

Validation pending explicit approval: compilation/regression tests, edge swipe over WebView and Downloads on a device, idle timer/touch reset, accessibility/keyboard/PiP/fullscreen, tab restoration across Videos/Music, private history isolation, observed redirects and SPA updates. Added BrowserQuickNavigationTest source; not executed.
