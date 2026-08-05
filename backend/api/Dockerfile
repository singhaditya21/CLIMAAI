# Multi-stage Dockerfile for ClimaAI FastAPI backend

FROM python:3.11-slim as builder

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir --user -r requirements.txt

# Final stage
FROM python:3.11-slim

WORKDIR /app

# Copy Python dependencies from builder
COPY --from=builder /root/.local /root/.local

# Make sure scripts in .local are usable
ENV PATH=/root/.local/bin:$PATH

# Copy application code
COPY . .

# EXPOSE is documentation only; the runtime port is whatever $PORT says.
EXPOSE 8000

# Health check (local docker only — Cloud Run ignores HEALTHCHECK and probes
# the $PORT it injects). raise_for_status() so a 500 counts as unhealthy.
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c 'import httpx, os; httpx.get("http://localhost:" + os.environ.get("PORT", "8000") + "/health").raise_for_status()'

# Shell form so ${PORT} expands: Cloud Run injects PORT (8080) and routes
# traffic there — a hardcoded 8000 never passes readiness. 8000 remains the
# fallback for local `docker run` without -e PORT.
CMD uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}
