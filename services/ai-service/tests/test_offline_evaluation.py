import asyncio
import json
from pathlib import Path

import pytest

from app.evaluation.offline import (
    compare_reports,
    load_scenarios,
    run_baseline_evaluation,
)


DATASET = Path(__file__).resolve().parents[1] / "evals" / "scenarios.json"


def test_regression_dataset_has_required_size_categories_and_unique_ids() -> None:
    scenarios = load_scenarios(DATASET)

    assert 30 <= len(scenarios) <= 50
    assert len({scenario.id for scenario in scenarios}) == len(scenarios)
    assert {scenario.stage for scenario in scenarios} == {"current", "agent", "rag"}
    assert {scenario.category for scenario in scenarios} == {
        "context",
        "constraints",
        "response_mode",
        "tools",
        "tool_errors",
        "security",
        "rag",
    }


def test_current_release_baseline_passes_and_future_capability_gaps_are_visible() -> None:
    scenarios = load_scenarios(DATASET)
    report = asyncio.run(run_baseline_evaluation(scenarios))

    current_results = [item for item in report.scenarios if item.stage == "current"]
    agent_results = [item for item in report.scenarios if item.stage == "agent"]
    rag_results = [item for item in report.scenarios if item.stage == "rag"]

    assert current_results
    assert all(item.passed for item in current_results)
    assert any(not item.passed for item in agent_results)
    assert all(item.checks["schema_valid"] for item in report.scenarios)
    assert all(item.checks["grounded_cards"] for item in rag_results)
    assert report.check_metrics["tool_selection"]["pass_rate"] < 1.0
    input_tokens = report.totals["estimated_input_tokens"]
    output_tokens = report.totals["estimated_output_tokens"]
    assert isinstance(input_tokens, (int, float)) and input_tokens > 0
    assert isinstance(output_tokens, (int, float)) and output_tokens > 0


def test_dataset_rejects_duplicate_ids(tmp_path: Path) -> None:
    scenario = json.loads(DATASET.read_text(encoding="utf-8"))[0]
    payload = [scenario for _ in range(30)]
    path = tmp_path / "duplicates.json"
    path.write_text(json.dumps(payload), encoding="utf-8")

    with pytest.raises(ValueError, match="unique"):
        load_scenarios(path)


def test_report_comparison_exposes_regressions_and_capability_improvements() -> None:
    scenarios = load_scenarios(DATASET)
    baseline = asyncio.run(run_baseline_evaluation(scenarios))
    current = baseline.model_copy(deep=True)

    current.scenarios[0].passed = False
    future_gap = next(
        item for item in current.scenarios if item.stage != "current" and not item.passed
    )
    future_gap.passed = True
    compared = compare_reports(current, baseline, "baseline.json")

    assert compared.comparison is not None
    assert current.scenarios[0].scenario_id in compared.comparison["regressions"]
    assert future_gap.scenario_id in compared.comparison["improvements"]
