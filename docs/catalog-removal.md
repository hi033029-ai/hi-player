# External catalog removal

The optional external movie/TV catalog has been removed at the owner’s request.

- Removed the metadata HTTP client, catalog endpoints, build-time token fields and retired catalog screen/models.
- Removed online title/rating lookup from local video lists, including its request jobs and rating rows.
- Removed the provider attribution artwork and obsolete integration guides; updated the privacy notice.
- Preserved local thumbnails, file metadata, player features, Google Drive, Jellyfin, WebDAV and SMB connection code.
- Source-only change: no Gradle, tests, lint, APK, version bump, main merge or release was run for this removal.
- Existing installed/published APKs and Git history are unchanged. The source removal takes effect in a future approved build. Revoke any old provider credential in its developer dashboard separately if it is no longer needed; removing source does not revoke credentials.
