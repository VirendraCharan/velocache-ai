import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

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

df.to_csv("traffic_data.csv", index=False)



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
from datetime import datetime

def run_forecast(csv_path, endpoint_name):
    df = pd.read_csv(csv_path)

    if len(df) < 5:
        return {
            "endpoint": endpoint_name,
            "predicted_spike_at": None,
            "confidence": "low",
            "reason": "insufficient_history"
        }

    future_minutes, predictions = forecast_next(df)

    baseline = df["request_count"].tail(15).mean()
    spike_threshold = baseline * 1.5

    spike_minute = None
    for minute, pred in zip(future_minutes, predictions):
        if pred > spike_threshold:
            spike_minute = int(minute)
            break

    result = {
        "endpoint": endpoint_name,
        "predicted_spike_at": spike_minute,
        "confidence": "high" if spike_minute else "none",
        "current_baseline": round(baseline, 1),
        "generated_at": datetime.now().isoformat()
    }

    with open("forecast_output.json", "w") as f:
        json.dump(result, f, indent=2)

    return result

test_df = df[df["minute"] <= 22]  # cuts data off right after the minute-20 spike starts
test_df.to_csv("test_data.csv", index=False)
print(run_forecast("test_data.csv", "/traffic"))

if __name__ == "__main__":
    print(run_forecast("traffic_data.csv", "/traffic"))

plt.plot(df["minute"], df["request_count"])
plt.xlabel("Minute")
plt.ylabel("Request Count")
plt.title("Synthetic VeloCache Traffic")
plt.savefig("traffic_plot.png")
plt.show()