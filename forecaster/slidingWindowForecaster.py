import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import os
import glob
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

minutes = 100
base_traffic = 20
spike_interval = 20
spike_height = 50

request_counts = []
for minute in range (minutes):
    value = base_traffic + np.random.randint(-3,3)
    if minute % spike_interval < 3:
        value += spike_height
    request_counts.append(value)

df = pd.DataFrame({
    "minute": range(minutes),
    "request_count": request_counts
})

df.to_csv(os.path.join(SCRIPT_DIR, "traffic_data.csv"), index=False)



from sklearn.linear_model import LinearRegression

def forecast_next(df, window_size=15, steps_ahead=5):
    recent = df.tail(window_size)
    X = recent["minute"].values.reshape(-1, 1)
    y = recent["request_count"].values

    model = LinearRegression()
    model.fit(X, y)

    last_minute = recent["minute"].values[-1]
    future_minutes = np.array([last_minute + i for i in range(1, steps_ahead + 1)]).reshape(-1, 1)
    predictions = model.predict(future_minutes)

    return future_minutes.flatten(), predictions

import json
from datetime import datetime, timedelta

def run_forecast(csv_path, endpoint_name, tenant_id=None, output_path=None):
    if output_path is None:
        output_path = os.path.join(SCRIPT_DIR, "forecast_output.json")

    df = pd.read_csv(csv_path)

    if len(df) < 5:
        result = {
            "tenant_id": tenant_id,
            "endpoint": endpoint_name,
            "predicted_spike_at": None,
            "confidence": "low",
            "reason": "insufficient_history",
            "generated_at": datetime.now().isoformat()
        }
    else:
        future_minutes, predictions = forecast_next(df)

        baseline = df["request_count"].tail(15).mean()
        spike_threshold = baseline * 1.5

        spike_minute = None
        for minute, pred in zip(future_minutes, predictions):
            if pred > spike_threshold:
                spike_minute = int(minute)
                break

            
        spike_at = None
        if spike_minute is not None:
            midnight = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
            spike_at = (midnight + timedelta(minutes=spike_minute)).isoformat()
        result = {
            "tenant_id": tenant_id,
            "endpoint": endpoint_name,
            "predicted_spike_at": spike_at,
            "confidence": "high" if spike_minute else "none",
            "current_baseline": round(baseline, 1),
            "generated_at": datetime.now().isoformat()
        }

    with open(output_path, "w") as f:
        json.dump(result, f, indent=2)

    return result

test_df = df[df["minute"] <= 22]  # cuts data off right after the minute-20 spike starts
test_df.to_csv(os.path.join(SCRIPT_DIR, "test_data.csv"), index=False)
print(run_forecast(os.path.join(SCRIPT_DIR, "test_data.csv"), "/traffic"))

def run_all_exports():
    pattern = os.path.join(SCRIPT_DIR, "*__*-export.csv")
    for csv_path in glob.glob(pattern):
        file_name = os.path.basename(csv_path)
        base = file_name[:-len("-export.csv")]
        tenant_id, safe_endpoint = base.split("__", 1)
        endpoint = safe_endpoint.replace("~", "/")

        output_path = os.path.join(SCRIPT_DIR, base + "-forecast.json")
        result = run_forecast(csv_path, endpoint, tenant_id, output_path)
        print(tenant_id, endpoint, result["confidence"], result["predicted_spike_at"])

if __name__ == "__main__":
    run_all_exports()
plt.plot(df["minute"], df["request_count"])
plt.xlabel("Minute")
plt.ylabel("Request Count")
plt.title("Synthetic VeloCache Traffic")
plt.savefig("traffic_plot.png")