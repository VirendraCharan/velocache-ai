import time
from slidingWindowForecaster import run_all_exports

while True:
    try:
        run_all_exports()
    except Exception as e:
        print("forecast failed:", e)
    time.sleep(30)