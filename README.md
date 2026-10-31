# Hi Player signed YouTube-blocker update channel

These are source-distribution packages for the new pinned-key updater (app versionCode 77+), not an APK and not a zero-ads guarantee. Existing older APKs cannot consume them.

The initial package accompanies PR #21 and is not yet compiler/device/live-site validated. Do not describe signing as runtime testing. New packages must be reviewed, tested with appropriate approval, and signed by the dedicated offline/private key; never commit that key. Publish manifest.json, manifest.sig.b64, and immutable packages/<sha256>.js files together. Preserve older content-addressed scripts. Versions strictly increase, including corrective reverts. Clients can roll back locally and refuse to reinstall the same bad version. Manifests expire; renew with a higher version.

Updating this branch does not push main or build an APK. Source/signing instructions are in docs/signed-blocker-updates.md on the implementation feature branch/PR, then main after approved merge.
