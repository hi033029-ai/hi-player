# Library privacy, scan feedback and contextual player guide

## Changes

- Scan feedback is visible only while a scan is running. Completion no longer leaves a persistent count/banner in either library. Completed results are the list itself.
- The video scanner no longer fabricates a local library from bundled remote sample videos when no media is found. Old version-1 media cache snapshots are invalidated.
- Both scanners check real Android access before querying or walking directories, then recheck before returning results. Denied access produces no library rows. Library ViewModels clear in-memory lists and durable snapshots on denial; background cache readers/writers also enforce access.
- Selected-video access never restores or merges a full-library snapshot and only queries the selected MediaStore set, without broader filesystem/download supplementation. Full-library disk caching is disabled for selected access.
- All-files access is a separate Android authorization and can permit media access without the individual media grant. Libraries now disclose that access, or selected-only access, instead of implying inaccessible files were scanned. This does not treat app-owned downloads as authorization to scan shared media.
- Returning to a previously visited main destination unwinds its old branch. Home → Music → Home → Settings → Back returns Home, not Music.
- Browser Back unwinds the current page history, then returns to that tab's start page, then exits browser mode. Only explicit Close chooses an opener or recently used tab. Selecting browser Home disposes the previous page/history so it cannot reappear through Back.
- A new skippable player-only visual walkthrough demonstrates swipes and double taps with a moving hand, and highlights the measured HDR control. Open HDR goes to the actual preset menu; Customize retains the contextual colour guide. It does not change HDR or colour settings by itself. Disabling system animations leaves a static demonstration.

## Validation status

Source review and whitespace checks only. Regression fixtures added for access scopes/cache eligibility, home-history reset, browser Back versus Close, scan completion removal, visual guide dismissal and opening HDR. These have not been compiled or executed. No APK, main push, version bump or device validation was performed for this follow-up. Fresh build/validation approval is required.

Physical-device checks must include revoked/denied/full/selected/all-files grants; app upgrade cache invalidation; Settings return; empty library with no synthetic rows; scan disappearance; browser Home/new-tab Back; video portrait/landscape, font scaling and actual HDR-marker placement. Animated guide screenshots remain to be inspected on the approved build.
