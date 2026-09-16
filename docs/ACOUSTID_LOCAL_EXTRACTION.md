AcoustID lookup requires a Chromaprint fingerprint and whole-audio duration. The official service documents the `client`, `duration`, `fingerprint`, and optional `meta` parameters, recommends respecting its request limits, and returns recording metadata under results/recordings/artists. Local-file extraction is preferred for videos already in Hi Player because microphone capture cannot reliably hear same-device playback. The requested recognition window is 10 seconds; short clips may still fail when audio is remixed, noisy, or absent from AcoustID.

Source: https://acoustid.org/webservice
