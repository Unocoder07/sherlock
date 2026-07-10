# Minimal containerized smoke test — runs on a stable Python, independent of the
# host toolchain. Validates that the infra + envelope round-trip works.
FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY smoke.py .

ENTRYPOINT ["python", "smoke.py"]
