import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile
from shazamio import Shazam

app = FastAPI(title="Hi Player ShazamIO Recognition")
shazam = Shazam()


@app.get("/health")
async def health():
    return {"status": "ok", "recognizer": "shazamio"}


@app.post("/recognize")
async def recognize(file: UploadFile = File(...)):
    suffix = Path(file.filename or "sample.m4a").suffix or ".m4a"
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty audio sample")
    if len(data) > 10 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Audio sample is too large")

    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp:
            temp.write(data)
            temp_path = temp.name
        result = await shazam.recognize(temp_path)
        track = result.get("track") if isinstance(result, dict) else None
        if not track:
            return {"matched": False}
        return {
            "matched": True,
            "title": track.get("title", ""),
            "artist": track.get("subtitle", ""),
            "album": (track.get("sections") or [{}])[0].get("metadata", [{}])[0].get("text"),
            "songLink": track.get("url"),
            "youtubeUrl": None,
        }
    except Exception:
        # Recognition failures are intentionally opaque to the client.
        return {"matched": False}
    finally:
        if temp_path:
            try:
                os.unlink(temp_path)
            except OSError:
                pass
