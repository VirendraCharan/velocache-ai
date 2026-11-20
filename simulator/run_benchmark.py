import argparse
import json
import os
import platform
import statistics
import subprocess
import sys
import time
from datetime import datetime, timezone

import requests


REQUEST_TIMEOUT_SECONDS = 10
MEDIAN_FIELDS = (
    "served",
    "hits",
    "misses",
    "p99Ms",
    "maxMs",
    "slowCalls",
    "refused",
    "errors",
)


def collect_environment_metadata(repo_root):
    def run_command(command):
        try:
            result = subprocess.run(
                command,
                cwd=repo_root,
                capture_output=True,
                text=True,
                check=False,
            )
        except OSError:
            return None
        if result.returncode != 0:
            return None
        return result.stdout.strip()

    server_version = run_command(
        ["docker", "version", "--format", "{{.Server.Version}}"]
    )
    compose_version = run_command(
        ["docker", "compose", "version", "--short"]
    )
    info_output = run_command(["docker", "info", "--format", "{{json .}}"])
    docker_cpu_count = None
    docker_memory_bytes = None
    if info_output is not None:
        try:
            info = json.loads(info_output)
            docker_cpu_count = info.get("NCPU")
            docker_memory_bytes = info.get("MemTotal")
        except (TypeError, ValueError):
            pass

    return {
        "testDateUtc": datetime.now(timezone.utc).isoformat(),
        "hardware": {
            "system": platform.system(),
            "release": platform.release(),
            "machine": platform.machine(),
            "processor": platform.processor(),
            "cpuCount": os.cpu_count(),
        },
        "docker": {
            "serverVersion": server_version,
            "composeVersion": compose_version,
            "cpuCount": docker_cpu_count,
            "memoryBytes": docker_memory_bytes,
        },
    }


def read_cache_stats(base_url, ops_key, tenant_ids):
    headers = {"X-Ops-Key": ops_key}
    totals = {"hits": 0, "misses": 0}

    for tenant_id in tenant_ids:
        response = requests.get(
            base_url + "/api/dashboard/cache-stats",
            headers=headers,
            params={"tenantId": tenant_id},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        data = response.json()
        totals["hits"] += data["hits"]
        totals["misses"] += data["misses"]

    return totals


def read_predicted_warming_events(base_url, ops_key, tenant_id):
    response = requests.get(
        base_url + "/api/dashboard/warming-events",
        headers={"X-Ops-Key": ops_key},
        params={"tenantId": tenant_id, "limit": 50},
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    events = response.json()
    return sum(1 for event in events if event.get("type") == "predicted")


def set_warming(base_url, ops_key, enabled):
    response = requests.post(
        base_url + "/api/dashboard/warming",
        headers={"X-Ops-Key": ops_key},
        params={"enabled": "true" if enabled else "false"},
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    data = response.json()
    if data.get("warmingEnabled") != enabled:
        raise RuntimeError(
            "warming response did not confirm requested setting"
        )


def rounded_median(runs, field):
    return round(statistics.median(run[field] for run in runs))


def parse_args(repo_root):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--duration", type=int, default=900)
    parser.add_argument("--tenants-per-run", type=int, default=3)
    parser.add_argument("--pause", type=float, default=30)
    parser.add_argument(
        "--out-dir",
        default=os.path.join("simulator", "results"),
    )
    parser.add_argument("--base-url", default="http://localhost:8080")
    args = parser.parse_args()

    if args.repeats < 1:
        parser.error("--repeats must be at least 1")
    if args.duration < 0:
        parser.error("--duration must be non-negative")
    if args.tenants_per_run < 1:
        parser.error("--tenants-per-run must be at least 1")
    if args.pause < 0:
        parser.error("--pause must be non-negative")

    if os.path.isabs(args.out_dir):
        args.out_dir = os.path.normpath(args.out_dir)
    else:
        args.out_dir = os.path.normpath(os.path.join(repo_root, args.out_dir))

    args.base_url = args.base_url.rstrip("/")
    return args


def main():
    missing = [
        name
        for name in ("VELOCACHE_DEV_SECRET", "VELOCACHE_OPS_KEY")
        if not os.environ.get(name)
    ]
    if missing:
        for name in missing:
            print(name + " is missing")
        return 1

    dev_secret = os.environ["VELOCACHE_DEV_SECRET"]
    ops_key = os.environ["VELOCACHE_OPS_KEY"]

    this_file = os.path.abspath(__file__)
    simulator_dir = os.path.dirname(this_file)
    repo_root = os.path.dirname(simulator_dir)
    args = parse_args(repo_root)

    try:
        response = requests.get(
            args.base_url + "/api/dashboard/warming",
            headers={"X-Ops-Key": ops_key},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        if response.status_code != 200:
            print("app not reachable or ops key wrong")
            return 1
    except requests.exceptions.RequestException:
        print("app not reachable or ops key wrong")
        return 1

    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    runs_dir = os.path.join(args.out_dir, "runs")
    os.makedirs(runs_dir, exist_ok=True)
    runs = []
    total_runs = args.repeats * 2
    run_number = 0

    try:
        for repeat in range(1, args.repeats + 1):
            for mode in ("off", "on"):
                run_number += 1
                set_warming(args.base_url, ops_key, mode == "on")

                tenant_ids = [
                    "bench-{0}-r{1}-{2}-t{3}".format(
                        stamp, repeat, mode, tenant_number
                    )
                    for tenant_number in range(1, args.tenants_per_run + 1)
                ]

                before = read_cache_stats(
                    args.base_url, ops_key, tenant_ids
                )

                warming_before = None
                try:
                    warming_before = sum(
                        read_predicted_warming_events(
                            args.base_url, ops_key, tenant_id
                        )
                        for tenant_id in tenant_ids
                    )
                except requests.exceptions.RequestException:
                    pass

                label = "{0}-r{1}".format(mode, repeat)
                output_path = os.path.join(runs_dir, label + ".json")
                simulator_path = os.path.join(simulator_dir, "simulator.py")
                started_at_utc = datetime.now(timezone.utc).isoformat()
                completed = subprocess.run(
                    [
                        sys.executable,
                        simulator_path,
                        label,
                        "--tenants",
                        ",".join(tenant_ids),
                        "--duration",
                        str(args.duration),
                        "--out",
                        output_path,
                    ],
                    cwd=repo_root,
                    env=os.environ.copy(),
                )
                completed_at_utc = datetime.now(timezone.utc).isoformat()
                if completed.returncode != 0:
                    print("simulator run failed: " + label)
                    return 1

                after = read_cache_stats(
                    args.base_url, ops_key, tenant_ids
                )

                warming_events = None
                if warming_before is not None:
                    try:
                        warming_after = sum(
                            read_predicted_warming_events(
                                args.base_url, ops_key, tenant_id
                            )
                            for tenant_id in tenant_ids
                        )
                        warming_events = max(
                            0, warming_after - warming_before
                        )
                    except requests.exceptions.RequestException:
                        pass

                with open(output_path, "r", encoding="utf-8") as input_file:
                    simulator_result = json.load(input_file)

                run = {
                    "mode": mode,
                    "repeat": repeat,
                    "startedAtUtc": started_at_utc,
                    "completedAtUtc": completed_at_utc,
                    "served": simulator_result["served"],
                    "hits": after["hits"] - before["hits"],
                    "misses": after["misses"] - before["misses"],
                    "warmingEvents": warming_events,
                    "originCalls": (
                        None
                        if warming_events is None
                        else after["misses"] - before["misses"] + warming_events
                    ),
                    "p99Ms": simulator_result["p99Ms"],
                    "maxMs": simulator_result["maxMs"],
                    "slowCalls": simulator_result["slowCalls"],
                    "refused": simulator_result["refused"],
                    "errors": simulator_result["errors"],
                }
                runs.append(run)
                print(
                    "{0}/{1} {2} r{3}: served={4}, hits={5}, misses={6}, "
                    "warmingEvents={7}, originCalls={8}, p99Ms={9}, "
                    "refused={10}, errors={11}".format(
                        run_number,
                        total_runs,
                        mode,
                        repeat,
                        run["served"],
                        run["hits"],
                        run["misses"],
                        run["warmingEvents"],
                        run["originCalls"],
                        run["p99Ms"],
                        run["refused"],
                        run["errors"],
                    )
                )

                if run_number < total_runs and args.pause > 0:
                    time.sleep(args.pause)
    except Exception as error:
        print("benchmark failed: " + str(error))
        return 1
    finally:
        try:
            set_warming(args.base_url, ops_key, True)
        except Exception:
            print("warning: failed to restore warming to ON")

    medians = {}
    for mode in ("off", "on"):
        mode_runs = [run for run in runs if run["mode"] == mode]
        medians[mode] = {
            field: rounded_median(mode_runs, field)
            for field in MEDIAN_FIELDS
        }

    note = (
        "Median of {0} runs per setting, {1} simulated tenants per run, "
        "{2} seconds each. Simulated 200 ms origin; all traffic sent from "
        "one laptop."
    ).format(args.repeats, args.tenants_per_run, args.duration)

    benchmark = {
        "off": medians["off"],
        "on": medians["on"],
        **collect_environment_metadata(repo_root),
        "repeats": args.repeats,
        "tenantsPerRun": args.tenants_per_run,
        "durationSeconds": args.duration,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "note": note,
        "runs": runs,
    }

    os.makedirs(args.out_dir, exist_ok=True)
    latest_path = os.path.join(args.out_dir, "benchmark-latest.json")
    with open(latest_path, "w", encoding="utf-8") as output_file:
        json.dump(benchmark, output_file, indent=2)
        output_file.write("\n")

    print("OFF medians:")
    print(
        "  served={served} hits={hits} misses={misses} p99Ms={p99Ms} "
        "maxMs={maxMs} slowCalls={slowCalls} refused={refused} errors={errors}".format(
            **medians["off"]
        )
    )
    print("ON medians:")
    print(
        "  served={served} hits={hits} misses={misses} p99Ms={p99Ms} "
        "maxMs={maxMs} slowCalls={slowCalls} refused={refused} errors={errors}".format(
            **medians["on"]
        )
    )

    if any(run["refused"] > 0 or run["errors"] > 0 for run in runs):
        print("WARNING: refused or error counts were observed; numbers may be unreliable")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
