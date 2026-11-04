# Play candidate build setup

The manual **Build Play candidate** workflow prepares a signed Android App Bundle as a GitHub Actions artifact. It does not submit or publish to Google Play, change existing features, or establish policy/legal approval. Do not run it until the owner approves a build and the release-readiness issues are reviewed. The existing test APK workflow and public debug key are unchanged.

## Signing setup

1. Determine whether this package already has a Play listing and use its existing upload key if one exists. Otherwise create a new dedicated upload key in a secure, backed-up location. Never reuse the committed public debug key or commit the new key/passwords.
2. Configure a protected GitHub environment named `play-release`; limit who can run the workflow and provide secrets. Run only a reviewed, trusted branch: code in a workflow can access its secrets.
3. Add environment secrets `UPLOAD_KEYSTORE_BASE64` (base64 of the keystore file), `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_PASSWORD`, and `UPLOAD_KEY_ALIAS`. These must be entered in GitHub's secret settings, never in chat or the repository. The workflow temporarily decodes the keystore on the runner, rejects the known public debug certificate, and deletes the temporary file after the job.
4. The owner explicitly approves the build, then runs the workflow from the reviewed branch. It runs unit tests before `bundleRelease` and uploads the bundle only as an Actions artifact; failed tests stop the build. No build is triggered by adding this workflow.
5. Before any upload, verify the AAB signature and package/version; confirm Play App Signing enrollment and the app-signing certificate registered with Firebase/Google. A Play-signed install using a different certificate cannot normally update the existing debug-signed APK in place. Plan a safe data migration before uninstalling anything.

The workflow intentionally enables the existing Drive feature to preserve behavior, but `drive.readonly` is restricted and may require Google verification. YouTube ad skipping, file-access and package-install permissions, online lyric rights, codec/filter licenses, privacy and other review items remain unresolved: see [Play Store readiness](play-store-readiness.md). Do not claim or imply that a successful build means Play or legal approval.
