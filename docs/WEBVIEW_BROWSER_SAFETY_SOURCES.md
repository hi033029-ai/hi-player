# In-app browser implementation sources

The Settings browser uses Android WebView and DownloadManager only for ordinary, user-initiated browsing and HTTP(S) downloads.

- Android WebView management and Safe Browsing: <https://developer.android.com/develop/ui/views/layout/webapps/managing-webview>
  - WebView performs Safe Browsing URL checks by default, and Android 8.0+ supports enabling Safe Browsing per WebView.
  - A renderer process can be destroyed and the host app must handle this safely.
- Android `DownloadManager.Request`: <https://developer.android.com/reference/android/app/DownloadManager.Request>
  - A request requires an HTTP or HTTPS URI.
  - Public Downloads can be used as a destination for user-visible downloads on current Android versions.
  - The app uses visible system download notifications and never requests hidden-download permission.

The browser does not implement DRM removal, paywall bypass, login bypass, or protected-stream extraction.
