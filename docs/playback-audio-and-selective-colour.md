# Audio playback, queue order and selective colour

## Behaviour

- The player’s Play as audio action disables video track selection, keeps the current position, and immediately returns to the library without entering PiP. Library overflow Play in background also starts audio-only without opening the video screen.
- Explicit audio playback continues while the library or another app is visible. An existing settings/record read cannot switch it back to video after the user chooses audio.
- Video completion and notification/PiP next/previous use the ViewModel-owned snapshot of the list the user selected from, including displayed sort/filter/folder/Continue Watching order. They do not re-filter a raw library scan by folder or wrap at the last item. Independently opened streams default to one item.
- Music uses a native Media3 playlist built from the visible sorted list or displayed groups. Auto-next, notification controls and in-app controls share that playlist. Moving to the next song does not reopen the full-screen music sheet. Identity uses URI, not a potentially duplicated MediaStore ID.
- The active music/video player registers with the MediaSessionService and starts the service from the playback path. Sessions are explicitly added to the service for notification updates. Metadata includes media title and music artist/album. Changing playback owner pauses the old player. Released engines detach their session; engine pre-warming cannot steal a music notification.
- Choosing an HDR colour swatch only selects the controls. The selected-colour slider is neutral at the midpoint, and lightens/darkens matching source pixels without applying an RGB colour wash. Narrow source-hue masks protect greys, near-black pixels and other hues; overlapping masks blend instead of compounding. Exposure/saturation/vibrance/sharpness are explicitly labelled Whole picture and remain global controls.

## Validation status

Approved release: 1.0.79 (80). Local debug APK compilation/assembly succeeded with the existing signing key. Targeted playback queue, selective-colour, first-use UI, folder queue, tab history, recycle visibility and browser progress regression tests passed (35 tests, zero failures/errors). YouTube skipper JavaScript fixtures and whitespace checks also passed. First-use component screenshots were inspected at 360 × 640. These checks do not replace real-device playback, notification or HDR validation.

Real-device validation must still cover: switching to audio during initial preparation; notification title/play/pause/next/previous; Home and screen-off playback; rapid next/previous; end of queue; shuffled library refresh; duplicate IDs; selected folders and Continue Watching; switching music/video ownership; rotation and PiP; Android media-notification settings; HDR and SDR on real devices using red/green/blue/grey test footage. Background playback does not claim survival of force-stop or process death. The playback engines still live in activity ViewModels.
