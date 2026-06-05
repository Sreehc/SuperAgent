#!/usr/bin/env python3
import argparse
import json
import subprocess
import sys
import time
from pathlib import Path


def load_suite(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def tail(content: str, max_lines: int = 40) -> list[str]:
    if not content:
        return []
    lines = content.strip().splitlines()
    return lines[-max_lines:]


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
    if summary["retrievalPassRate"] < thresholds["minRetrievalPassRate"]:
        failures.append(
            f"retrieval pass rate {summary['retrievalPassRate']:.2%} < required {thresholds['minRetrievalPassRate']:.2%}"
        )
    if summary["groundingPassRate"] < thresholds["minGroundingPassRate"]:
        failures.append(
            f"grounding pass rate {summary['groundingPassRate']:.2%} < required {thresholds['minGroundingPassRate']:.2%}"
        )
    if summary["noEvidencePrecision"] < thresholds["minNoEvidencePrecision"]:
        failures.append(
            f"no-evidence precision {summary['noEvidencePrecision']:.2%} < required {thresholds['minNoEvidencePrecision']:.2%}"
        )
    if summary["traceFieldCompleteness"] < thresholds["traceFieldCompleteness"]:
        failures.append("trace field completeness gate failed")
    if summary["versionConsistencyRate"] < thresholds["versionConsistencyRate"]:
        failures.append(
            f"version consistency rate {summary['versionConsistencyRate']:.2%} < required {thresholds['versionConsistencyRate']:.2%}"
        )
    if summary["guardrailPassRate"] < thresholds["guardrailPassRate"]:
        failures.append(
            f"guardrail pass rate {summary['guardrailPassRate']:.2%} < required {thresholds['guardrailPassRate']:.2%}"
        )
    if summary["neighborExpansionRate"] < thresholds["neighborExpansionRate"]:
        failures.append(
            f"neighbor expansion rate {summary['neighborExpansionRate']:.2%} < required {thresholds['neighborExpansionRate']:.2%}"
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
        "retrievalPassRate": ratio(results, "retrieval"),
        "groundingPassRate": ratio(results, "grounding"),
        "noEvidencePrecision": ratio(results, "no-evidence"),
        "traceFieldCompleteness": ratio(results, "trace-fields"),
        "versionConsistencyRate": ratio(results, "version-consistency"),
        "guardrailPassRate": ratio(results, "guardrails"),
        "neighborExpansionRate": ratio(results, "neighbor-expansion"),
        "criticalE2EPassed": critical_e2e_passed(results),
        "thresholds": thresholds,
        "cases": results,
    }
    summary["gateFailures"] = evaluate_thresholds(summary, thresholds)
    summary["passed"] = not summary["gateFailures"]
    return summary


def print_summary(summary: dict) -> None:
    print(f"[rag-eval] suite={summary['suiteKey']}")
    print(
        "[rag-eval] passRate={:.2%} retrievalPassRate={:.2%} groundingPassRate={:.2%} noEvidencePrecision={:.2%} traceFieldCompleteness={:.2%} versionConsistencyRate={:.2%} guardrailPassRate={:.2%} neighborExpansionRate={:.2%}".format(
            summary["passRate"],
            summary["retrievalPassRate"],
            summary["groundingPassRate"],
            summary["noEvidencePrecision"],
            summary["traceFieldCompleteness"],
            summary["versionConsistencyRate"],
            summary["guardrailPassRate"],
            summary["neighborExpansionRate"],
        )
    )
    for item in summary["cases"]:
        status = "PASS" if item["passed"] else "FAIL"
        print(f"[rag-eval] {status} {item['caseKey']} ({item['durationMs']}ms)")
        if not item["passed"]:
            if item["stdoutTail"]:
                print("[rag-eval] stdout tail:")
                print("\n".join(item["stdoutTail"]))
            if item["stderrTail"]:
                print("[rag-eval] stderr tail:")
                print("\n".join(item["stderrTail"]))
    if summary["gateFailures"]:
        print("[rag-eval] gate failures:")
        for failure in summary["gateFailures"]:
            print(f"[rag-eval] - {failure}")
    else:
        print("[rag-eval] all gates passed")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run SuperAgent rag-eval suite.")
    parser.add_argument(
        "--suite",
        default="eval/rag-eval-suite.json",
        help="Path to the suite definition JSON.",
    )
    parser.add_argument(
        "--output",
        default="artifacts/rag-eval-summary.json",
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
        print(f"[rag-eval] running {case['caseKey']}: {case['command']}")
        results.append(run_case(repo_root, case))

    summary = build_summary(suite, results)
    with output_path.open("w", encoding="utf-8") as handle:
        json.dump(summary, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    print_summary(summary)
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
