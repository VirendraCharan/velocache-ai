import requests
import time
import sys

DEV_SECRET = "velocache-local-dev-7391"
LABEL = sys.argv[1] if len(sys.argv) > 1 else "run"

def get_token(tenant_id):
    url = "http://localhost:8080/api/dev/token"
    headers = {"X-Dev-Secret": DEV_SECRET}
    params = {"tenantId": tenant_id}
    response = requests.post(url, headers=headers, params=params)
    return response.text

def send_traffic(tenant_id, token, endpoint):
    url = "http://localhost:8080/api/track"
    headers = {"Authorization": "Bearer " + token}
    body = {"endpoint": endpoint, "requestCount": 1}
    response = requests.post(url, headers=headers, json=body)
    return response.status_code, response.text

def read_data(tenant_id, token, endpoint):
    url = "http://localhost:8080/api/data"
    headers = {"Authorization": "Bearer " + token}
    params = {"endpoint": endpoint}
    start = time.time()
    response = requests.get(url, headers=headers, params=params)
    elapsed_ms = (time.time() - start) * 1000
    return response.status_code, elapsed_ms

TENANTS = ["tenant-a"]
INTERVAL_SECONDS = 2
DURATION_SECONDS = 900
SPIKE_SEND_INTERVAL_SECONDS = 0.8


token = {}
for tenant in TENANTS:
    token[tenant] = get_token(tenant)

SPIKE_INTERVAL_SECONDS = 300
SPIKE_DURATION_SECONDS = 120

def is_spike_time(elapsed_seconds):
    position_in_cycle = elapsed_seconds % SPIKE_INTERVAL_SECONDS
    return position_in_cycle < SPIKE_DURATION_SECONDS
latencies = []
start_time = time.time()
while time.time() - start_time < DURATION_SECONDS:
    elapsed = time.time() - start_time
    currently_spiking = is_spike_time(elapsed)
    
    for tenant in TENANTS:
        status, text = send_traffic(tenant, token[tenant], "/simulated/" + tenant)
        print(tenant, status, "SPIKE" if currently_spiking else "calm")
        if currently_spiking:
            read_status, read_ms = read_data(tenant, token[tenant], "/api/data")
            print(tenant, "data", read_status, round(read_ms), "ms")
            if read_status == 200:
                latencies.append(read_ms)
    if currently_spiking:
         time.sleep(SPIKE_SEND_INTERVAL_SECONDS)
    else:
        time.sleep(INTERVAL_SECONDS)

print("---- SUMMARY ----")
if latencies:
    ordered = sorted(latencies)
    p99 = ordered[int(0.99 * (len(ordered) - 1))]
    print("served data calls:", len(ordered))
    print("p99 ms:", round(p99))
    print("max ms:", round(ordered[-1]))
    print("calls over 100 ms:", len([x for x in ordered if x > 100]))
    with open("benchmark_results.txt", "a") as f:
        f.write(LABEL + ": served=" + str(len(ordered)) + " p99=" + str(round(p99)) + " max=" + str(round(ordered[-1])) + " over100=" + str(len([x for x in ordered if x > 100])) + "\n")
else:
    print("no served data calls")