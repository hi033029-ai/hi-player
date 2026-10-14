# Local library TMDB matching

The local-library rating lookup uses the movie or TV search endpoint that matches the file type. When a file name includes a four-digit release year, the lookup passes that year to TMDB and then prefers a candidate with the same year. This fixes broad titles such as `The King.2019...`, while preserving a non-year fallback for files whose year is inaccurate or absent.

The lookup is intentionally metadata-only. It does not rename files, change playback, or fetch streams. A failed lookup is retried after one minute rather than being permanently marked as missing, which allows recovery after temporary network or API failures.

Observed validation, 14 October 2026:

- TMDB search for `Hokum` returns **Hokum (2026)** with a rating.
- TMDB search for `Insidious Out of the Further` returns **Insidious: Out of the Further (2026)** with a rating.
- TMDB movie search for `The King`, restricted to 2019, returns **The King (2019)** with a rating.

Reference: <https://developer.themoviedb.org/reference/search-movie>
