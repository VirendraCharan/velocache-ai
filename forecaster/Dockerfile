FROM python:3.11-slim
# Compose mounts the repository's forecaster folder at /app/forecaster, so the Spring app (which writes the traffic exports) and this worker (which writes the forecasts) share the same files. The scripts are also copied in so the image can run on its own.
WORKDIR /app/forecaster
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY slidingWindowForecaster.py run_forecaster_loop.py ./
ENV PYTHONUNBUFFERED=1
CMD ["python", "run_forecaster_loop.py"]
