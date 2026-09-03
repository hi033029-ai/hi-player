FROM python:3.12-slim

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY shazam_backend/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY shazam_backend /app/shazam_backend
WORKDIR /app/shazam_backend

ENV PYTHONUNBUFFERED=1
EXPOSE 10000

CMD sh -c 'uvicorn main:app --host 0.0.0.0 --port ${PORT:-10000}'
