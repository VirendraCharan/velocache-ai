import importlib.util
import os
import sys
import unittest
from unittest.mock import patch


MODULE_PATH = os.path.join(os.path.dirname(__file__), "run_benchmark.py")
SPEC = importlib.util.spec_from_file_location("run_benchmark", MODULE_PATH)
benchmark = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(benchmark)

SIMULATOR_PATH = os.path.join(os.path.dirname(__file__), "simulator.py")
SIMULATOR_SPEC = importlib.util.spec_from_file_location(
    "simulator", SIMULATOR_PATH
)
simulator = importlib.util.module_from_spec(SIMULATOR_SPEC)
SIMULATOR_SPEC.loader.exec_module(simulator)


class BenchmarkTests(unittest.TestCase):
    def test_reset_deletes_only_each_tenant_pattern(self):
        commands = []

        def fake_command(command, repo_root):
            commands.append(command)
            return "benchmark-key" if "--scan" in command else "1"

        with patch.object(benchmark, "run_command", side_effect=fake_command):
            reset = benchmark.reset_cache_state("repo", ["tenant-a", "tenant-b"])

        self.assertFalse(reset["globalFlushDb"])
        self.assertEqual(
            4,
            len(commands),
        )
        self.assertTrue(
            all("FLUSHDB" not in command for command in commands)
        )

    def test_warming_state_response_is_verified(self):
        response = type(
            "Response",
            (),
            {
                "raise_for_status": lambda self: None,
                "json": lambda self: {"warmingEnabled": False},
            },
        )()
        with patch.object(benchmark.requests, "post", return_value=response):
            with self.assertRaises(RuntimeError):
                benchmark.set_warming("http://loopback", "ops", True)

    def test_statuses_and_latency_only_count_valid_200_data(self):
        counters = simulator.empty_results()
        for status in (200, 429, 500, None):
            simulator.record_result(counters["data"], status)
        self.assertEqual(counters["data"]["successful200"], 1)
        self.assertEqual(counters["data"]["refused429"], 1)
        self.assertEqual(counters["data"]["otherStatusErrors"], 1)
        self.assertEqual(counters["data"]["transportErrors"], 1)
        self.assertTrue(
            benchmark.latency_metrics([10, 20])["insufficientData"]
        )
        self.assertEqual(
            20,
            benchmark.latency_metrics([10, 20, 30])["p50Ms"],
        )

    def test_validate_simulator_result_accepts_matching_shape(self):
        benchmark.validate_simulator_result(
            {
                "latencies": [10.25, 20.5],
                "requestAccounting": {
                    "data": {"successful200": 2},
                },
            }
        )

    def test_validate_simulator_result_rejects_missing_or_mismatched_shape(self):
        with self.assertRaisesRegex(RuntimeError, "missing top-level latencies"):
            benchmark.validate_simulator_result(
                {"requestAccounting": {"data": {"successful200": 1}}}
            )
        with self.assertRaisesRegex(RuntimeError, "latency shape mismatch"):
            benchmark.validate_simulator_result(
                {
                    "latencies": [10.25],
                    "requestAccounting": {
                        "data": {"successful200": 2},
                    },
                }
            )

    def test_simulator_uses_supplied_base_url(self):
        response = type("Response", (), {"status_code": 200})()
        with patch.object(simulator.requests, "post", return_value=response) as post:
            simulator.send_traffic(
                "http://127.0.0.1:18080",
                "tenant",
                "token",
                "/api/data",
            )
        self.assertTrue(
            post.call_args.args[0].startswith("http://127.0.0.1:18080/")
        )

    def test_normal_configurations_do_not_start_simulated_outages(self):
        with open(MODULE_PATH, encoding="utf-8") as source_file:
            source = source_file.read()
        self.assertNotIn("kill-redis", source)
        self.assertNotIn("start_simulated_outage", source)

    def test_evidence_code_does_not_write_environment_values(self):
        with open(MODULE_PATH, encoding="utf-8") as source_file:
            source = source_file.read()
        self.assertNotIn('os.environ.items()', source)
        self.assertNotIn('os.environ["VELOCACHE_OPS_KEY"]', source.split("benchmark = {", 1)[-1])

    def test_benchmark_argument_validation(self):
        with patch.object(sys, "argv", ["run_benchmark.py", "--warmup", "-1"]):
            with self.assertRaises(SystemExit):
                benchmark.parse_args("repo")


if __name__ == "__main__":
    unittest.main()
