# TMDB integration requirements

**Reviewed and implemented:** 2026-10-13

TMDB provides **metadata and artwork only** for Hi Player’s Discover experience. It does not provide playable video streams. Playback remains limited to user-authorized personal sources such as a selected Google Drive folder, Jellyfin, or WebDAV.

## Current implementation

- The app owner registered a free **TMDB Developer Plan** for **personal, non-commercial use**.
- The API Read Access Token is stored only in the secure task connector; it is not committed to Git or placed in project files.
- A token is injected only into the signed **debug** build at build time. The release build intentionally receives an empty token, because a client-embedded credential can be extracted and the registered TMDB plan is not authorised for public/commercial distribution.
- Discover uses TMDB for Trending, Now Playing, New TV Episodes, Coming Soon, search, title details, ratings, genres, seasons, cast, characters, and TMDB artwork.
- Discover has no TMDB playback source. A title can be played only when a matching file is found in a user-connected personal media source.
- The app includes the approved TMDB blue logo and required visible attribution on Discover content.

## Compliance rules

1. The current Developer Plan is free only for the certified personal, non-commercial use. It must not be used in a revenue-generating or business/public-distribution environment without TMDB’s appropriate written agreement.
2. The following attribution must remain visible in an About or Credits location whenever TMDB data or images are used:

   > This product uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.

3. TMDB metadata must not be cached for more than six months. Hi Player currently fetches Discover metadata directly and does not persist a catalog cache.
4. Never commit, log, share, scrape, or hardcode the API token. Never use a shared or hidden key.
5. If public Play Store distribution becomes the plan, stop using this personal credential. Obtain TMDB’s correct commercial/public permission and move access behind a controlled backend before enabling TMDB in a public release.

## Source links

- TMDB Application Authentication: https://developer.themoviedb.org/docs/authentication-application
- TMDB Logos & Attribution: https://www.themoviedb.org/about/logos-attribution
- TMDB API Terms of Use: https://www.themoviedb.org/api-terms-of-use
- Google Play sensitive file access policy: https://support.google.com/googleplay/android-developer/answer/16558241?hl=en

## Google Drive folder selection

Hi Player’s Drive connection uses Android’s Storage Access Framework. The account picker identifies the Google account, then the Google Drive document picker asks the user to select one folder. Android grants read access only to that folder; Hi Player does not receive a Google password or broad Drive OAuth token. This limited, user-selected access follows the privacy-first approach recommended by Google Play for file access.
