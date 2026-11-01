# Build validation — 1.0.77 (78)

- User approved building this test APK and requested OneDrive/Dropbox Coming soon entries.
- OneDrive and Dropbox Settings rows are disabled and labelled Coming soon; no OAuth registration is claimed.
- Compiled and packaged assembleDebug with Drive testing enabled using JDK 21 and Gradle 9.3.1.
- Corrected Android file removal API from unavailable Os.unlink to Os.remove in batch rename.
- Signature verified against the existing committed signing certificate (SHA-256 d5476ab29cc741fcc49d65a7c39a2fb7062dfe80c1a68a336e6f81e7915d5f68).
- YouTube deterministic JavaScript fixtures passed; this is not live-site validation.
- Full unit suite: 207 tests, 205 passed, 2 failed, none skipped. Failures: LibrarySelectionLayoutTest.storageOverviewScrollsAwayWithRootFiles (off-screen lazy item not found by performScrollTo), OrbitBrowserFeaturesTest.genericUrlRulesOnlyApplyToSubresources (generic adserver fragment expectation failed). These remain unresolved; this is a preview APK, not a fully passing release.
- No phone/emulator or live Google Drive, Jellyfin, WebDAV, SMB, Microsoft or Dropbox checks were performed.
- Initial combined build/test attempt exhausted or lost its Gradle daemon; sequential lower-memory packaging and tests completed independently.
- Main is unchanged. Feature-branch preview only.
