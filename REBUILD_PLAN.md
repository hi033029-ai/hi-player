# Hi Player Rebuild Plan

## Product goal

Hi Player will use one coherent application shell instead of independent screen layouts. The shell owns the status-bar-safe header, navigation, text scale, theme, and one-time onboarding state. Feature screens render only their own content below the shell.

## Application flow

1. On a fresh installation, show the Welcome screen exactly once.
2. Continue to the Permissions step and request only the media and notification permissions appropriate for the Android version.
3. Continue to the Theme step and let the user select the theme.
4. Continue to the Text Size step and persist the chosen UI scale.
5. Mark onboarding complete only after the final step is saved.
6. Open the Home video library on every later launch.

## Shared shell

The root shell will render one header immediately below the system status bar. The header contains the existing Hi Player logo and logo-colored title on the left, with Search, Refresh, and Stream URL actions on the right. It must not move when switching between Video, Music, Files, and Settings, opening folders, changing view modes, or opening stacked menus. Feature screens must not render another global header.

## Content alignment

All folder rows, file rows, video rows, music rows, settings rows, and stacked-menu entries use a shared left alignment token. Rows are borderless and containerless unless a control genuinely needs a surface. No horizontal numbered file markers are allowed.

## Navigation and options

Navigation modes, view modes, sorting, extraction destinations, player options, and settings options use stacked menus or bottom sheets. A menu is opened explicitly by the user and is dismissed without changing unrelated state. Library mode, selected folder, view mode, sort mode, and text scale are persisted with DataStore.

## Preserved and improved features

The rebuild must retain local video scanning, Continue Watching, favorites, folder and tree navigation, music playback, background playback, PiP, subtitles, playback controls, archive extraction, APK installation intents, file-type icons, refresh, search, theme selection, and settings. Stream URL accepts a URL, validates it, creates a playable media item, and reports invalid or unplayable URLs without crashing.

## Acceptance checks

The project is not considered complete until the following are true:

- The project compiles in GitHub Actions with no Kotlin errors or warnings that indicate a missing symbol.
- Only one global header is visible on each main tab, directly below the phone status bar.
- Continue Watching is visible whenever history exists, regardless of library mode.
- Reopening the app restores the previous library mode and view selection.
- Opening a folder and pressing Back returns to its parent without opening archive results automatically.
- Clicking an APK launches the Android installation confirmation flow instead of browsing its contents.
- Extract To opens a destination picker and does not extract until the user confirms the destination action.
- Welcome, Permissions, Theme, and Text Size are shown only on first launch.
- The selected text size changes both content and header dimensions globally.
- Rows are left-aligned and no numbered horizontal file markers remain.
