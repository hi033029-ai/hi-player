# Hi Browser native downloads

Hi Browser owns HTTP transfers through OkHttp. No Android DownloadManager APIs,
intents to third-party download apps, or polling of the system download queue.
Android's non-exported foreground service supplies process priority and notifications;
MediaStore is used only to publish completed files, not to download them.

- Two simultaneous transfers; the rest wait in a persistent queue.
- Per-file pause/resume, cancel/restart, retry, open, share, and video playback.
- Bytes, speed, known-size progress, ETA and actionable failure text.
- Three automatic network/server retries with exponential backoff.
- HTTP Range + If-Range resumption with strong ETag or Last-Modified. Servers
  without validators or ignoring Range restart safely instead of appending.
- Original user agent, cookies and referring page are retained for authenticated
  downloads. Credentials are stripped when redirected to a different origin.
- Requests never relax TLS validation; HTTPS-to-HTTP redirects are rejected.
- Private partial files and atomic queue journal live in noBackupFilesDir so
  cookies are neither exported to backups nor stored in public download folders.
- Files are published to Downloads/Hi Browser on Android 10+. Older versions use
  that folder if storage permission is granted, otherwise app-specific storage.
- Partial MediaStore destinations are hidden and removed if publishing fails.
- Removing history does not delete completed files. Cancel removes the partial file.
- Process interruption/force-stop is restored as Paused; resume explicitly in the
  browser. Android 15 dataSync time limits also pause transfers safely.
- Resume is conditional on server support. Expired links may require a fresh
  download from the website. DRM, blob: and data: downloads are not implemented.
- Previous Android Download Manager jobs are not imported or controlled by this
  engine. Existing files remain on disk; old system jobs must be managed in Android.
- Publishing temporarily needs space for both the private partial and final copy.

Transport and model tests: `gradle testDebugUnitTest --tests 'com.example.downloads.*'`.
Device QA still required: foreground/background downloads on Android 10/13/15/16,
notification denial, long downloads, low storage, logout/expired cookies, process
kill/resume, and opening/sharing files through installed apps.
