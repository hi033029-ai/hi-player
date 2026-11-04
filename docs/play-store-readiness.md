# Play Store readiness — current source review

This is a source review, not a Play approval or legal opinion. The 1.0.90 APK is a test build; it is not the signed release bundle required for a new Play submission. No app features were disabled as part of these documentation and label updates.

## Addressed without changing feature behavior

- The app description and video-settings subtitle use neutral playback wording rather than the prior Blu-ray/Remux claim.
- The internal brand document uses a neutral heading. This is not a trademark clearance or a new icon.
- The third-party inventory identifies FFmpeg, SMBJ, Reorderable and filter sources, removes unsupported direct Retrofit/Moshi claims, and includes LGPL 2.1 text. The inventory is not complete and does not establish license compliance.

## Before a production submission

1. **Signing:** Select the final application ID and establish Play App Signing with a private upload key. The test signing key is public and must not be treated as a secret release key. Confirm package availability and any existing Play listing before changing identity. Register the appropriate Play signing certificate fingerprints for Google/Firebase sign-in. Different signing identities can prevent an in-place update from the test APK; plan migration and preserve user data.
2. **Permissions:** Review actual file-manager and installation uses of `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_VIDEO`, and `REQUEST_INSTALL_PACKAGES` against Play's current policy; submit any required declarations or change the functionality. A name change does not change permission eligibility. [All files access policy](https://support.google.com/googleplay/android-developer/answer/10467955), [permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241).
3. **YouTube and browser:** The existing YouTube ad-skipping code and signed script-update channel remain present. YouTube says blocking its ads violates its terms. Decide whether to remove or redesign that feature for a Play build; a label change does not resolve this. Google Play's code policy has an interpreter/WebView exception but interpreted code still must comply with all Play policies; this is not a blanket approval of the updater. [YouTube](https://support.google.com/youtube/answer/14129599), [Play policy](https://support.google.com/googleplay/android-developer/answer/16559646).
4. **Drive:** The test APK enables `drive.readonly`; the default build flag is off. Confirm whether the release should include it. Restricted-scope verification and other Google requirements may apply; do not describe it as verified without confirmation.
5. **Rights and licenses:** Establish the rights to online lyric content and the provenance of the HEVC test clip. Verify FFmpeg source revision, configuration and relinking obligations and codec patent issues, as well as filter-list terms, transitive dependency notices and icon rights. The added notices are a starting point, not legal clearance.
6. **Privacy and submission:** Review the demo privacy notice for the actual release, publish an accurate policy, complete required Play declarations, store listing, reviewer access instructions and rating forms. Verify real console status rather than inferring it from repository files.
7. **Validation:** Produce and verify a release AAB with the proper key, run the applicable tests, and test real devices and provider sign-in before a staged internal release. Do not claim submission or approval before it occurs.

The `com.example` source namespace is separate from the application ID; its name alone does not prove a publishing prohibition. The cloud redirect scheme in source matches the manifest; whether a provider accepts it depends on its registered OAuth configuration. Existing backup XML explicitly excludes the named media-source preference and settings DataStore, but other stores still require review. The presence of `All rights reserved` with an Apache license is not by itself proof that the license grant is invalid.
