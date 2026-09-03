# Hi Player ShazamIO backend

This is a local, keyless recognition proxy for Hi Player. It uses the MIT-licensed ShazamIO library to identify a short audio sample and does not require an AudD or ACRCloud API key.

## Termux setup

```bash
pkg update -y
pkg install -y python ffmpeg
cd ~/hi-player/shazam_backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 127.0.0.1 --port 8765
```

Keep this process running in Termux while using automatic recognition. The Android app posts audio samples to `http://127.0.0.1:8765/recognize`.

The backend stores each sample only in a temporary file and deletes it after recognition. It never returns a filename as a recognition result; if ShazamIO has no match, it returns `matched: false`.

For an emulator, change the Android endpoint to `http://10.0.2.2:8765/recognize` because `127.0.0.1` refers to the emulator itself.
