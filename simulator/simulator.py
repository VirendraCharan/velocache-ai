import argparse
import json
import os
import threading
import time

import requests


INTERVAL_SECONDS = 2
SPIKE_SEND_INTERVAL_SECONDS = 0.8
SPIKE_INTERVAL_SECONDS = 300
SPIKE_DURATION_SECONDS = 120
REQUEST_TIMEOUT_SECONDS = 10


def get_token(dev_secret, tenant_id):
    url = "http://localhost:8080/api/dev/token"
    headers = {"X-Dev-Secret": dev_secret}
    params = {"tenantId": tenant_id}
    try:
        response = requests.post(
            url,
            headers=headers,
            params=params,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException:
        return None
    if response.status_code != 200:
        return None
    return response.text


def send_traffic(tenant_id, token, endpoint):
    url = "http://localhost:8080/api/track"
    headers = {"Authorization": "Bearer " + token}
    body = {"endpoint": endpoint, "requestCount": 1}
    try:
        response = requests.post(
            url,
            headers=headers,
            json=body,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException:
        return None
    return response.status_code


def read_data(tenant_id, token, endpoint):
    url = "http://localhost:8080/api/data"
    headers = {"Authorization": "Bearer " + token}
    params = {"endpoint": endpoint}
    start = time.time()
    try:
        response = requests.get(
            url,
            headers=headers,
            params=params,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException:
        return None, None
    elapsed_ms = (time.time() - start) * 1000
    return response.status_code, elapsed_ms


def is_spike_time(elapsed_seconds):
    position_in_cycle = elapsed_seconds % SPIKE_INTERVAL_SECONDS
    return position_in_cycle < SPIKE_DURATION_SECONDS


def empty_results():
    return {"latencies": [], "refused": 0, "errors": 0}


def run_tenant(tenant, dev_secret, start_time, duration_seconds, results):
    tenant_results = empty_results()
    token = get_token(dev_secret, tenant)
    if token is None:
        tenant_results["errors"] += 1
        results[tenant] = tenant_results
        return

    while time.time() - start_time < duration_seconds:
        elapsed = time.time() - start_time
        currently_spiking = is_spike_time(elapsed)

        status = send_traffic(tenant, token, "/simulated/" + tenant)
        if status is None:
            tenant_results["errors"] += 1
        elif status == 429:
            tenant_results["refused"] += 1
        elif status != 200:
            tenant_results["errors"] += 1
        print(tenant, status, "SPIKE" if currently_spiking else "calm")

        if currently_spiking:
            read_status, read_ms = read_data(tenant, token, "/api/data")
            print(
                tenant,
                "data",
                read_status,
                round(read_ms) if read_ms is not None else "-",
                "ms",
            )
            if read_status == 200:
                tenant_results["latencies"].append(read_ms)
            elif read_status == 429:
                tenant_results["refused"] += 1
            else:
                tenant_results["errors"] += 1

        time.sleep(
            SPIKE_SEND_INTERVAL_SECONDS if currently_spiking else INTERVAL_SECONDS
        )

    results[tenant] = tenant_results


def summarize(result):
    ordered = sorted(result["latencies"])
    served = len(ordered)
    if served:
        p99 = ordered[int(0.99 * (served - 1))]
        maximum = ordered[-1]
    else:
        p99 = 0
        maximum = 0
    return {
        "served": served,
        "p99Ms": round(p99),
        "maxMs": round(maximum),
        "slowCalls": len([latency for latency in ordered if latency > 100]),
        "refused": result["refused"],
        "errors": result["errors"],
    }


def main():
    dev_secret = os.environ.get("VELOCACHE_DEV_SECRET")
    if not dev_secret:
        print("VELOCACHE_DEV_SECRET is missing")
        return 1

    parser = argparse.ArgumentParser()
    parser.add_argument("label", nargs="?", default="run")
    parser.add_argument("--tenants", default="tenant-a")
    parser.add_argument("--duration", type=int, default=900)
    parser.add_argument("--out")
    args = parser.parse_args()

    tenants = [tenant.strip() for tenant in args.tenants.split(",") if tenant.strip()]
    start_time = time.time()
    results = {}
    threads = [
        threading.Thread(
            target=run_tenant,
            args=(tenant, dev_secret, start_time, args.duration, results),
        )
        for tenant in tenants
    ]
    for thread in threads:
        thread.start()

    next_progress = 60
    while any(thread.is_alive() for thread in threads):
        for thread in threads:
            thread.join(timeout=0.5)
        elapsed = time.time() - start_time
        if elapsed >= next_progress:
            print("elapsed seconds:", int(elapsed))
            next_progress += 60

    summaries = {tenant: summarize(results[tenant]) for tenant in tenants}
    combined = empty_results()
    for tenant in tenants:
        combined["latencies"].extend(results[tenant]["latencies"])
        combined["refused"] += results[tenant]["refused"]
        combined["errors"] += results[tenant]["errors"]
    combined_summary = summarize(combined)

    print("---- SUMMARY ----")
    print("served data calls:", combined_summary["served"])
    print("p99 ms:", combined_summary["p99Ms"])
    print("max ms:", combined_summary["maxMs"])
    print("calls over 100 ms:", combined_summary["slowCalls"])
    print("refused (429):", combined_summary["refused"])
    print("errors:", combined_summary["errors"])
    for tenant in tenants:
        print(tenant, summaries[tenant])

    if args.out:
        parent = os.path.dirname(os.path.abspath(args.out))
        os.makedirs(parent, exist_ok=True)
        output = {
            "label": args.label,
            "tenants": tenants,
            "durationSeconds": args.duration,
            **combined_summary,
            "perTenant": summaries,
        }
        with open(args.out, "w") as output_file:
            json.dump(output, output_file, indent=2)
            output_file.write("\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
