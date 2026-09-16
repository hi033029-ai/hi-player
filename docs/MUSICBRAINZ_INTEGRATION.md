# MusicBrainz integration

MusicBrainz provides free metadata lookup through `https://musicbrainz.org/ws/2/recording/`. The app uses the AcoustID-recognized title and artist to search recordings, sends JSON format, limits results to one, and supplies an identifying `User-Agent`. MusicBrainz is a metadata database; it does not identify raw microphone audio or humming by itself. AcoustID/Chromaprint remains the audio-identification step, and YouTube remains the video-search/playback step.

Reference: https://musicbrainz.org/doc/MusicBrainz_API

## Fallback behavior

The lookup tries an exact artist/title recording query first, then a broader artist/title query, and finally a title-only query. Each additional request is delayed to respect MusicBrainz's one-request-per-second average limit. Results are ranked by title and artist similarity rather than blindly accepting the first result. If all searches return no match—or MusicBrainz is temporarily unavailable—the app retains the original AcoustID artist and title and continues directly to the YouTube search.
