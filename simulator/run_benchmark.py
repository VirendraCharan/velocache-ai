import argparse
import csv
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
CONFIGURATIONS = ("cache-disabled", "cache-aside", "predictive-warming")


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def run_command(command, repo_root):
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


def collect_environment_metadata(repo_root):
    docker_info = run_command(
        ["docker", "info", "--format", "{{json .}}"], repo_root
    )
    info = {}
    if docker_info:
        try:
            info = json.loads(docker_info)
        except (TypeError, ValueError):
            pass
    return {
        "testDateUtc": utc_now(),
        "hardware": {
            "system": platform.system(),
            "release": platform.release(),
            "machine": platform.machine(),
            "processor": platform.processor(),
            "cpuCount": os.cpu_count(),
        },
        "software": {
            "python": platform.python_version(),
            "dockerServer": run_command(
                ["docker", "version", "--format", "{{.Server.Version}}"],
                repo_root,
            ),
            "dockerCompose": run_command(
                ["docker", "compose", "version", "--short"], repo_root
            ),
        },
        "docker": {
            "cpuCount": info.get("NCPU"),
            "memoryBytes": info.get("MemTotal"),
        },
    }


def parse_args(repo_root):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--duration", type=int, default=900)
    parser.add_argument("--tenants-per-run", type=int, default=3)
    parser.add_argument("--pause", type=float, default=30)
    parser.add_argument("--warmup", type=float, default=0)
    parser.add_argument("--cooldown", type=float, default=0)
    parser.add_argument("--burst-size", type=int, default=150)
    parser.add_argument("--burst-duration", type=int, default=120)
    parser.add_argument("--spike-interval", type=int, default=300)
    parser.add_argument("--interval", type=float, default=2)
    parser.add_argument("--endpoint", default="/api/data")
    parser.add_argument(
        "--out-dir",
        default=os.path.join("simulator", "results"),
    )
    parser.add_argument("--base-url", default="http://localhost:8080")
    args = parser.parse_args()

    checks = (
        (args.repeats, 1, "--repeats"),
        (args.tenants_per_run, 1, "--tenants-per-run"),
        (args.burst_size, 1, "--burst-size"),
        (args.burst_duration, 1, "--burst-duration"),
        (args.spike_interval, 1, "--spike-interval"),
        (args.duration, 0, "--duration"),
        (args.pause, 0, "--pause"),
        (args.warmup, 0, "--warmup"),
        (args.cooldown, 0, "--cooldown"),
        (args.interval, 0, "--interval"),
    )
    for value, minimum, name in checks:
        if value < minimum:
            parser.error("{0} must be at least {1}".format(name, minimum))

    if os.path.isabs(args.out_dir):
        args.out_dir = os.path.normpath(args.out_dir)
    else:
        args.out_dir = os.path.normpath(os.path.join(repo_root, args.out_dir))
    args.base_url = args.base_url.rstrip("/")
    return args


def read_cache_stats(base_url, ops_key, tenant_ids):
    totals = {"hits": 0, "misses": 0}
    for tenant_id in tenant_ids:
        response = requests.get(
            base_url + "/api/dashboard/cache-stats",
            headers={"X-Ops-Key": ops_key},
            params={"tenantId": tenant_id},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        data = response.json()
        totals["hits"] += data["hits"]
        totals["misses"] += data["misses"]
    return totals


def read_warming_events(base_url, ops_key, tenant_ids):
    total = 0
    for tenant_id in tenant_ids:
        response = requests.get(
            base_url + "/api/dashboard/warming-events",
            headers={"X-Ops-Key": ops_key},
            params={"tenantId": tenant_id, "limit": 50},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        total += sum(
            1 for event in response.json() if event.get("type") == "predicted"
        )
    return total


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
        raise RuntimeError("warming response did not confirm requested setting")


def configure_warming(base_url, ops_key, enabled):
    set_warming(base_url, ops_key, False)
    set_warming(base_url, ops_key, enabled)
    verify_warming(base_url, ops_key, enabled)


def verify_warming(base_url, ops_key, enabled):
    response = requests.get(
        base_url + "/api/dashboard/warming",
        headers={"X-Ops-Key": ops_key},
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    if response.json().get("warmingEnabled") != enabled:
        raise RuntimeError("warming state verification failed")


def reset_cache_state(repo_root, tenant_ids):
    started = utc_now()
    deleted = 0
    for tenant_id in tenant_ids:
        pattern = "velocache:*:" + tenant_id + ":*"
        keys = run_command(
            [
                "docker",
                "compose",
                "exec",
                "-T",
                "redis",
                "redis-cli",
                "--scan",
                "--pattern",
                pattern,
            ],
            repo_root,
        )
        if keys is None:
            raise RuntimeError("could not scan benchmark Redis keys")
        for key in [item for item in keys.splitlines() if item]:
            if run_command(
                [
                    "docker",
                    "compose",
                    "exec",
                    "-T",
                    "redis",
                    "redis-cli",
                    "DEL",
                    key,
                ],
                repo_root,
            ) != "1":
                raise RuntimeError("could not delete benchmark Redis key")
            deleted += 1
    return {
        "startedAtUtc": started,
        "completedAtUtc": utc_now(),
        "method": "per-tenant Redis key scan and DEL",
        "pattern": "velocache:*:<benchmark-tenant>:*",
        "deletedKeys": deleted,
        "globalFlushDb": False,
        "note": (
            "No FLUSHDB is used. A global FLUSHDB would also clear Redis "
            "traffic, forecast, rate-limit, and non-cache state."
        ),
    }


def latency_metrics(observations):
    if len(observations) < 3:
        return {
            "validMeasurements": len(observations),
            "insufficientData": True,
            "medianMs": None,
            "p50Ms": None,
            "p95Ms": None,
            "p99Ms": None,
        }
    ordered = sorted(observations)

    def percentile(value):
        return ordered[int(value * (len(ordered) - 1))]

    return {
        "validMeasurements": len(ordered),
        "insufficientData": False,
        "medianMs": round(statistics.median(ordered), 3),
        "p50Ms": round(percentile(0.50), 3),
        "p95Ms": round(percentile(0.95), 3),
        "p99Ms": round(percentile(0.99), 3),
    }


def run_simulator(
    repo_root, simulator_path, output_path, label, tenant_ids, args, cache_mode
):
    command = [
        sys.executable,
        simulator_path,
        label,
        "--tenants",
        ",".join(tenant_ids),
        "--duration",
        str(args.duration),
        "--burst-size",
        str(args.burst_size),
        "--burst-duration",
        str(args.burst_duration),
        "--endpoint",
        args.endpoint,
        "--spike-interval",
        str(args.spike_interval),
        "--quiet-interval",
        str(args.interval),
        "--base-url",
        args.base_url,
        "--cache-mode",
        cache_mode,
        "--out",
        output_path,
    ]
    completed = subprocess.run(
        command,
        cwd=repo_root,
        env=os.environ.copy(),
        capture_output=True,
        text=True,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError("simulator run failed: " + label)
    return completed.stdout


def build_tenants(stamp, configuration, repeat, count):
    return [
        "benchmark-{0}-{1}-r{2}-t{3}".format(
            stamp, configuration, repeat, tenant_number
        )
        for tenant_number in range(1, count + 1)
    ]


def write_csv(path, runs):
    fields = [
        "configuration",
        "runNumber",
        "timestampUtc",
        "tenants",
        "endpoint",
        "requestAccounting",
        "latenciesMs",
        "warmingEvents",
        "redisOutageRecoveryEvents",
        "errors",
    ]
    with open(path, "w", newline="", encoding="utf-8") as output_file:
        writer = csv.DictWriter(output_file, fieldnames=fields)
        writer.writeheader()
        for run in runs:
            writer.writerow(
                {
                    "configuration": run["configuration"],
                    "runNumber": run["runNumber"],
                    "timestampUtc": run["timestampUtc"],
                    "tenants": json.dumps(run["tenants"]),
                    "endpoint": run["endpoint"],
                    "requestAccounting": json.dumps(
                        run["requestAccounting"]
                    ),
                    "latenciesMs": json.dumps(run["responseLatenciesMs"]),
                    "warmingEvents": run["warmingEvents"],
                    "redisOutageRecoveryEvents": json.dumps(
                        run["redisOutageRecoveryEvents"]
                    ),
                    "errors": run["errors"],
                }
            )


def write_summary(path, benchmark):
    lines = [
        "VeloCache AI benchmark summary",
        "Generated: " + benchmark["generatedAt"],
        "Evidence directory: " + benchmark["evidenceDirectory"],
        "",
    ]
    for configuration, summary in benchmark["summary"].items():
        metrics = summary["latency"]
        latency = (
            "insufficient data (<3 valid measurements)"
            if metrics["insufficientData"]
            else "median={0}ms p50={1}ms p95={2}ms p99={3}ms".format(
                metrics["medianMs"],
                metrics["p50Ms"],
                metrics["p95Ms"],
                metrics["p99Ms"],
            )
        )
        lines.append(
            "{0}: runs={1}, data200={2}, data429={3}, errors={4}, {5}".format(
                configuration,
                summary["runs"],
                summary["dataSuccessful200"],
                summary["dataRefused429"],
                summary["errors"],
                latency,
            )
        )
    with open(path, "w", encoding="utf-8") as output_file:
        output_file.write("\n".join(lines) + "\n")


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

    simulator_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.dirname(simulator_dir)
    args = parse_args(repo_root)
    ops_key = os.environ["VELOCACHE_OPS_KEY"]

    try:
        response = requests.get(
            args.base_url + "/api/dashboard/warming",
            headers={"X-Ops-Key": ops_key},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
    except requests.exceptions.RequestException as error:
        print("benchmark setup failed: " + str(error))
        return 1

    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    evidence_dir = os.path.join(args.out_dir, "slice-2-" + stamp)
    runs_dir = os.path.join(evidence_dir, "runs")
    os.makedirs(runs_dir, exist_ok=True)
    environment = collect_environment_metadata(repo_root)
    simulator_path = os.path.join(simulator_dir, "simulator.py")
    runs = []

    try:
        for configuration in CONFIGURATIONS:
            for repeat in range(1, args.repeats + 1):
                tenant_ids = build_tenants(
                    stamp, configuration, repeat, args.tenants_per_run
                )
                reset = reset_cache_state(repo_root, tenant_ids)
                warming_enabled = configuration == "predictive-warming"
                configure_warming(args.base_url, ops_key, warming_enabled)
                if args.warmup:
                    time.sleep(args.warmup)
                verify_warming(args.base_url, ops_key, warming_enabled)
                before = read_cache_stats(
                    args.base_url, ops_key, tenant_ids
                )
                try:
                    warming_before = read_warming_events(
                        args.base_url, ops_key, tenant_ids
                    )
                except requests.exceptions.RequestException:
                    warming_before = None

                label = "{0}-r{1}".format(configuration, repeat)
                output_path = os.path.join(runs_dir, label + ".json")
                started = utc_now()
                run_simulator(
                    repo_root,
                    simulator_path,
                    output_path,
                    label,
                    tenant_ids,
                    args,
                    "bypass" if configuration == "cache-disabled" else "normal",
                )
                completed = utc_now()
                after = read_cache_stats(args.base_url, ops_key, tenant_ids)
                try:
                    warming_after = read_warming_events(
                        args.base_url, ops_key, tenant_ids
                    )
                    warming_events = (
                        None
                        if warming_before is None
                        else max(0, warming_after - warming_before)
                    )
                except requests.exceptions.RequestException:
                    warming_events = None

                with open(output_path, "r", encoding="utf-8") as input_file:
                    simulator_result = json.load(input_file)
                accounting = simulator_result["requestAccounting"]
                data_counts = accounting["data"]
                cache_delta = {
                    "hits": after["hits"] - before["hits"],
                    "misses": after["misses"] - before["misses"],
                }
                cache_total = cache_delta["hits"] + cache_delta["misses"]
                runs.append(
                    {
                        "configuration": configuration,
                        "runNumber": repeat,
                        "timestampUtc": started,
                        "startedAtUtc": started,
                        "completedAtUtc": completed,
                        "warmupSeconds": args.warmup,
                        "cooldownSeconds": args.cooldown,
                        "tenants": tenant_ids,
                        "endpoint": args.endpoint,
                        "requestAccounting": accounting,
                        "perTenant": simulator_result["perTenant"],
                        "cacheStatsDelta": cache_delta,
                        "cacheHitRatio": (
                            cache_delta["hits"] / cache_total
                            if cache_total
                            else None
                        ),
                        "originCalls": (
                            None
                            if warming_events is None
                            else cache_delta["misses"] + warming_events
                        ),
                        "responseLatenciesMs": simulator_result["latencies"],
                        "latency": latency_metrics(simulator_result["latencies"]),
                        "warmingEvents": warming_events,
                        "redisOutageRecoveryEvents": [],
                        "errors": simulator_result["errors"],
                        "reset": reset,
                        "environment": environment,
                    }
                )
                print(
                    "{0} r{1}: data200={2}, data429={3}, "
                    "transportErrors={4}".format(
                        configuration,
                        repeat,
                        data_counts["successful200"],
                        data_counts["refused429"],
                        data_counts["transportErrors"],
                    )
                )
                if args.cooldown:
                    time.sleep(args.cooldown)
                if args.pause:
                    time.sleep(args.pause)
    except Exception as error:
        print("benchmark failed: " + str(error))
        return 1
    finally:
        try:
            configure_warming(args.base_url, ops_key, True)
        except Exception:
            print("warning: failed to restore warming to ON")

    summary = {}
    for configuration in CONFIGURATIONS:
        selected = [
            run for run in runs if run["configuration"] == configuration
        ]
        observations = [
            value for run in selected for value in run["responseLatenciesMs"]
        ]
        data = [run["requestAccounting"]["data"] for run in selected]
        summary[configuration] = {
            "runs": len(selected),
            "dataSuccessful200": sum(item["successful200"] for item in data),
            "dataRefused429": sum(item["refused429"] for item in data),
            "dataTransportErrors": sum(item["transportErrors"] for item in data),
            "dataOtherStatusErrors": sum(
                item["otherStatusErrors"] for item in data
            ),
            "cacheHitRatio": (
                sum(run["cacheStatsDelta"]["hits"] for run in selected)
                / sum(
                    run["cacheStatsDelta"]["hits"]
                    + run["cacheStatsDelta"]["misses"]
                    for run in selected
                )
                if sum(
                    run["cacheStatsDelta"]["hits"]
                    + run["cacheStatsDelta"]["misses"]
                    for run in selected
                )
                else None
            ),
            "originCalls": (
                None
                if any(run["originCalls"] is None for run in selected)
                else sum(run["originCalls"] for run in selected)
            ),
            "errors": sum(run["errors"] for run in selected),
            "latency": latency_metrics(observations),
        }

    benchmark = {
        "generatedAt": utc_now(),
        "configurationOrder": CONFIGURATIONS,
        "parameters": {
            key: value
            for key, value in vars(args).items()
            if key != "base_url" or value
        },
        "environment": environment,
        "evidenceDirectory": evidence_dir,
        "runs": runs,
        "summary": summary,
        "resetPolicy": (
            "Each run uses dedicated benchmark tenants and deletes only "
            "their velocache:*:<tenant>:* Redis keys before measurement. "
            "No global FLUSHDB is used."
        ),
        "redisOutageRecoveryEvents": (
            "No simulated outage is started by any normal configuration."
        ),
    }
    with open(
        os.path.join(evidence_dir, "benchmark-latest.json"),
        "w",
        encoding="utf-8",
    ) as output_file:
        json.dump(benchmark, output_file, indent=2)
        output_file.write("\n")
    write_csv(os.path.join(evidence_dir, "benchmark-runs.csv"), runs)
    write_summary(os.path.join(evidence_dir, "benchmark-summary.txt"), benchmark)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
