#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path


def load_suite(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def run_case(repo_root: Path, case: dict) -> dict:
    started_at = time.time()
    cwd = repo_root / case["cwd"]
    process = subprocess.run(
        case["command"],
        cwd=str(cwd),
        shell=True,
        text=True,
        capture_output=True,
    )
    duration_ms = int((time.time() - started_at) * 1000)
    return {
        "caseKey": case["caseKey"],
        "description": case["description"],
        "cwd": case["cwd"],
        "command": case["command"],
        "tags": case.get("tags", []),
        "passed": process.returncode == 0,
        "exitCode": process.returncode,
        "durationMs": duration_ms,
        "stdoutTail": tail(process.stdout),
        "stderrTail": tail(process.stderr),
    }


def tail(content: str, max_lines: int = 40) -> list[str]:
    if not content:
        return []
    lines = content.strip().splitlines()
    return lines[-max_lines:]


def ratio(results: list[dict], tag: str | None = None) -> float:
    scoped = [item for item in results if tag is None or tag in item["tags"]]
    if not scoped:
        return 1.0
    passed = sum(1 for item in scoped if item["passed"])
    return passed / len(scoped)


def critical_e2e_passed(results: list[dict]) -> bool:
    critical = [item for item in results if "critical-e2e" in item["tags"]]
    return all(item["passed"] for item in critical)


def evaluate_thresholds(summary: dict, thresholds: dict) -> list[str]:
    failures: list[str] = []
    if summary["passRate"] < thresholds["minPassRate"]:
        failures.append(
            f"pass rate {summary['passRate']:.2%} < required {thresholds['minPassRate']:.2%}"
        )
    if summary["recoveryRate"] < thresholds["minRecoveryRate"]:
        failures.append(
            f"recovery rate {summary['recoveryRate']:.2%} < required {thresholds['minRecoveryRate']:.2%}"
        )
    if summary["toolTraceFieldCompleteness"] < thresholds["toolTraceFieldCompleteness"]:
        failures.append(
            "tool trace field completeness gate failed"
        )
    if summary["highRiskVisibilityRate"] < thresholds["highRiskVisibilityRate"]:
        failures.append(
            "high-risk tool visibility gate failed"
        )
    if not summary["criticalE2EPassed"]:
        failures.append("one or more critical E2E cases failed")
    return failures


def build_summary(suite: dict, results: list[dict]) -> dict:
    thresholds = suite["thresholds"]
    summary = {
        "suiteKey": suite["suiteKey"],
        "description": suite.get("description", ""),
        "generatedAt": int(time.time()),
        "totalCases": len(results),
        "passedCases": sum(1 for item in results if item["passed"]),
        "failedCases": sum(1 for item in results if not item["passed"]),
        "passRate": ratio(results),
        "recoveryRate": ratio(results, "recovery"),
        "toolTraceFieldCompleteness": ratio(results, "tool-trace"),
        "highRiskVisibilityRate": ratio(results, "high-risk-visibility"),
        "criticalE2EPassed": critical_e2e_passed(results),
        "thresholds": thresholds,
        "cases": results,
    }
    summary["gateFailures"] = evaluate_thresholds(summary, thresholds)
    summary["passed"] = not summary["gateFailures"]
    return summary


def print_summary(summary: dict) -> None:
    print(f"[agent-eval] suite={summary['suiteKey']}")
    print(
        "[agent-eval] passRate={:.2%} recoveryRate={:.2%} toolTraceFieldCompleteness={:.2%} highRiskVisibilityRate={:.2%}".format(
            summary["passRate"],
            summary["recoveryRate"],
            summary["toolTraceFieldCompleteness"],
            summary["highRiskVisibilityRate"],
        )
    )
    for item in summary["cases"]:
        status = "PASS" if item["passed"] else "FAIL"
        print(f"[agent-eval] {status} {item['caseKey']} ({item['durationMs']}ms)")
        if not item["passed"]:
            if item["stdoutTail"]:
                print("[agent-eval] stdout tail:")
                print("\n".join(item["stdoutTail"]))
            if item["stderrTail"]:
                print("[agent-eval] stderr tail:")
                print("\n".join(item["stderrTail"]))
    if summary["gateFailures"]:
        print("[agent-eval] gate failures:")
        for failure in summary["gateFailures"]:
            print(f"[agent-eval] - {failure}")
    else:
        print("[agent-eval] all gates passed")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run SuperAgent agent-eval suite.")
    parser.add_argument(
        "--suite",
        default="eval/agent-eval-suite.json",
        help="Path to the suite definition JSON.",
    )
    parser.add_argument(
        "--output",
        default="artifacts/agent-eval-summary.json",
        help="Path to the summary JSON output.",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    suite_path = repo_root / args.suite
    output_path = repo_root / args.output
    output_path.parent.mkdir(parents=True, exist_ok=True)

    suite = load_suite(suite_path)
    results = []
    for case in suite["cases"]:
        print(f"[agent-eval] running {case['caseKey']}: {case['command']}")
        results.append(run_case(repo_root, case))

    summary = build_summary(suite, results)
    with output_path.open("w", encoding="utf-8") as handle:
        json.dump(summary, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    print_summary(summary)
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
