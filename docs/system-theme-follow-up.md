# System appearance follow-up

- Only System, Ice Blue and Custom remain in the app theme selector. Removed fixed Monochrome Light/Dark preference identifiers resolve to System, including older aliases and restored profile IDs.
- Upgrade migration v2 resets the previously saved override to System once, including installations that already ran v1 and later retained Ice Blue. Saved custom colour values are retained. Explicit selections made after v2 are not reset each launch.
- System continues to resolve from Compose's live Android light/dark configuration. Ice Blue and Custom remain deliberate overrides.
- First-use/setup no longer forces a white/blue screen over the selected System palette; text, cards and pinned actions use the active palette with contrasting button content.
- Browser's separate web-page dark setting is preserved without retaining removed global theme enum modes. Its palette is resolved using an explicit darkness override.

Source-only changes; no version change or build executed. Pending regression checks cover old-ID normalization, one-time migration, later explicit choices, picker options and live configuration changes. Real-device appearance changes, setup dark/light screenshots and browser controls remain to be checked on an approved build.
