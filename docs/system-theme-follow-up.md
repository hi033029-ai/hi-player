# System appearance follow-up

- Only System, Ice Blue and Custom remain in the app theme selector. Removed fixed Monochrome Light/Dark preference identifiers resolve to System, including older aliases and restored profile IDs.
- Upgrade migration v2 resets the previously saved override to System once, including installations that already ran v1 and later retained Ice Blue. Saved custom colour values are retained. Explicit selections made after v2 are not reset each launch.
- System continues to resolve from Compose's live Android light/dark configuration. Ice Blue and Custom remain deliberate overrides.
- First-use/setup no longer forces a white/blue screen over the selected System palette; text, cards and pinned actions use the active palette with contrasting button content.
- Browser's separate web-page dark setting is preserved without retaining removed global theme enum modes. Its palette is resolved using an explicit darkness override.

Approved release validation for 1.0.80 (81): APK assembled and theme normalization, migration, contrast, picker and live configuration tests passed within the 73-test targeted suite. The native dark welcome snapshot was visually inspected. Real-device appearance switches, browser controls and custom palettes still require device checks.
