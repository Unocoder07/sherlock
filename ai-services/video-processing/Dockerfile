# Video Processing Service (M5). Build context = repo ROOT (needs contracts/gen-python).
#   docker compose build video-processing
FROM python:3.11-slim

# System deps: build-essential+cmake for insightface's cython extensions;
# libglib2.0-0 for opencv-python-headless; libgomp1 for onnxruntime; curl for healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential cmake libglib2.0-0 libgomp1 curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Python deps first (layer caching). numpy/cython must exist before insightface builds.
COPY ai-services/video-processing/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir numpy==1.26.4 cython \
    && pip install --no-cache-dir -r requirements.txt

# Generated protobuf stubs (host-produced via `buf generate`) as an importable root.
COPY contracts/gen-python /app/_contracts
ENV PYTHONPATH=/app:/app/_contracts

# Application code.
COPY ai-services/video-processing/app /app/app

# Bake the InsightFace buffalo_l models into the image so first-frame latency and
# runtime network are avoided (downloads ~300MB to /root/.insightface at build).
RUN python -c "from insightface.app import FaceAnalysis; FaceAnalysis(name='buffalo_l', providers=['CPUExecutionProvider']).prepare(ctx_id=-1, det_size=(640,640))"

EXPOSE 8098
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8098"]
