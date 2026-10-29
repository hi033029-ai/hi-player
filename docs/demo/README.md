# Hi Player — demo homepage

Hi Player is an Android video/audio player with a local media library, file browsing, downloads, and an embedded web browser.

This page describes a **development/demo build**, not a Google-verified production Drive integration.

## Google account and optional Drive access
Sign-in uses Google's official account flow and Firebase Authentication. Profile name, email address, and photo are used for the Hi Player account display. Signing into Hi Player does not sign a separate website or browser cookie store into Google.

The optional Drive demo asks for read-only access when the signed-in user opens My Drive. It lists folders, videos, and audio for that account and can stream permitted media into Hi Player. This is not write/delete access. Google may show an unverified-app warning, require additional consent, or block authorization until the project's review is complete. A demo page does not grant Google approval.

## Before trying the demo
- Use an account whose files you are authorized to access. Review Google's permission screen carefully; granting Drive access is optional.
- Google sign-in can be used without granting Drive access. Declining Drive consent must not be treated as a successful Drive connection.
- No complete on-device Drive consent/playback validation or Google restricted-scope review is claimed by this page.
- Do not distribute the demo as a verified production Drive app.

[Demo privacy notice](./privacy.md)

[Source repository](https://github.com/hi033029-ai/hi-player)

Questions or deletion requests: hi033029@gmail.com. This is the project's configured support contact.
