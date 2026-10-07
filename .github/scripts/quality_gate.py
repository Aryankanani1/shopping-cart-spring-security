#!/usr/bin/env python3
"""The pipeline's quality gate (the "Quality gate" job in .github/workflows/pipeline.yml).

It runs only once every earlier stage has passed (build, unit tests, integration
tests, security scan), and then checks test coverage against the floors below:

  backend   the JaCoCo report merged from the unit and integration test stages
            (shop-app/target/site/jacoco-aggregate/jacoco.xml)
  frontend  Vitest's coverage summary (frontend/coverage/coverage-summary.json)

It writes the figures to the run's summary and exits 1 if any is below its floor.

The floors sit a little under the coverage the code had when the gate was added,
so that it can only go down by a little before the gate stops a change. Raise them
as coverage grows; never lower one to let a change through. Add tests instead.
"""

import json
import os
import sys
import xml.etree.ElementTree as ET

FLOORS = {
    ("Backend", "Lines"): 80.0,
    ("Backend", "Branches"): 55.0,
    ("Frontend", "Lines"): 75.0,
    ("Frontend", "Branches"): 70.0,
}

JACOCO_XML = "shop-app/target/site/jacoco-aggregate/jacoco.xml"
VITEST_SUMMARY = "frontend/coverage/coverage-summary.json"


def backend_coverage():
    root = ET.parse(JACOCO_XML).getroot()
    counters = {c.get("type"): c for c in root.findall("counter")}

    def pct(kind):
        c = counters[kind]
        missed, covered = int(c.get("missed")), int(c.get("covered"))
        return 100.0 * covered / (missed + covered)

    return {"Lines": pct("LINE"), "Branches": pct("BRANCH")}


def frontend_coverage():
    with open(VITEST_SUMMARY) as f:
        total = json.load(f)["total"]
    return {"Lines": total["lines"]["pct"], "Branches": total["branches"]["pct"]}


def main():
    measured = {}
    for side, read in (("Backend", backend_coverage), ("Frontend", frontend_coverage)):
        for metric, value in read().items():
            measured[(side, metric)] = value

    rows, failed = [], []
    for key, floor in FLOORS.items():
        value = measured[key]
        ok = value >= floor
        if not ok:
            failed.append(key)
        rows.append(f"| {key[0]} | {key[1]} | {value:.1f}% | {floor:.0f}% | {'pass' if ok else '**FAIL**'} |")

    stages = json.loads(os.environ.get("STAGES", "{}"))
    summary = ["## Quality gate", ""]
    if stages:
        summary += ["| Stage | Result |", "|---|---|"]
        summary += [f"| {name} | {result['result']} |" for name, result in stages.items()]
        summary.append("")
    summary += ["| Code | Coverage | Measured | Floor | |", "|---|---|---|---|---|", *rows, ""]
    summary.append(
        "**Failed:** coverage is below its floor for "
        + ", ".join(f"{side} {metric.lower()}" for side, metric in failed)
        + ". Add tests for the code this change adds or touches."
        if failed
        else "**Passed.**"
    )
    text = "\n".join(summary) + "\n"

    print(text)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a") as f:
            f.write(text)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
