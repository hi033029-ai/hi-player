# Music Now Playing, real equalizer, and separate Files sections

## Source changes
- Expanded audio playback hides the library/category toolbar and list. Back or the down arrow returns to the library plus mini-player, without stopping music. Library tabs remain available when browsing with the mini-player.
- Song title and artist are at the top, lyrics/artwork in a flexible area underneath, with seek/playback controls at the bottom. Removed the large framed hero/lyrics card, duplicate status-bar padding, waveform spacing and AUDIO PRO badge. Plain and synced lyric behavior remains unchanged.
- Mini-player is one compact card: tap the body or expand arrow for the large player. Play/Next/Close remain independent controls, so they do not accidentally expand it.
- Replaced the label-only Cinema 3D/preset dialog with native Android Equalizer frequency-band sliders, an on/off switch, real frequency-mapped presets, optional supported BassBoost strength, and reset. Gain range and band count come from the device; gains are bounded to its range and at most +/-12 dB. Preset curves use modest +/-6 dB boosts. Settings and custom gains persist; the default is Flat with effects off.
- Effects attach only to the music ExoPlayer audio session (never global session 0), reattach when its session changes, and release with the view model. Unsupported devices/outputs report this instead of promising processing. No virtual fake surround/200% claims, no changes to the video player's separate DSP. Device/headset validation is still required.
- Files root again has independent expandable Archives, Documents and Folders & files sections beneath the scrollable storage summary. Storage shortcuts expand/scroll to the section; paths are disjoint between categories. Nested folder browsing is unchanged. Root numeric breadcrumb and idle selection circles remain removed. Selection/select-all uses the section of the long-pressed item, with icon actions replacing its sticky category heading rather than disappearing inside the storage summary.

## Validation
Source inspection and git diff whitespace checks only. Added regression sources for curves, file membership, mini-player expansion/transport, metadata/lyrics ordering and equalizer sliders; updated the root-section regression. No Gradle, compile, tests, APK, version change or main merge performed for this feature request.
