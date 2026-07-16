import json
from pathlib import Path

import yaml  # type: ignore[import-untyped]


ROOT = Path(__file__).resolve().parents[3]


def test_prometheus_scrapes_both_services_and_loads_ai_alerts() -> None:
    config = yaml.safe_load((ROOT / "infra/prometheus.yml").read_text())
    jobs = {item["job_name"]: item for item in config["scrape_configs"]}

    assert jobs["game-recommender-backend"]["metrics_path"] == (
        "/actuator/prometheus"
    )
    assert jobs["ai-service"]["metrics_path"] == "/metrics"
    assert jobs["ai-service"]["static_configs"][0]["targets"] == [
        "ai-service:8000"
    ]
    assert "/etc/prometheus/prometheus-alerts.yml" in config["rule_files"]

    rules = yaml.safe_load((ROOT / "infra/prometheus-alerts.yml").read_text())
    alert_names = {
        rule["alert"]
        for group in rules["groups"]
        for rule in group["rules"]
    }
    assert {
        "AIServiceMetricsMissing",
        "JavaToPythonHighErrorRatio",
        "PythonToJavaToolsHighErrorRatio",
        "AIAgentP95LatencyHigh",
        "AIAgentSafetyLimitTriggered",
        "AIMockFallbackUsed",
    }.issubset(alert_names)


def test_grafana_ai_dashboard_covers_each_cross_service_boundary() -> None:
    path = ROOT / "infra/grafana/provisioned/ai-agent-observability.json"
    dashboard = json.loads(path.read_text())
    panels = {panel["title"]: panel for panel in dashboard["panels"]}

    assert dashboard["uid"] == "playcure-ai-agent"
    assert {
        "Java → Python latency",
        "Java → Python outcomes",
        "Python LLM latency",
        "Python → Java tool latency",
        "Tool calls by outcome",
        "Output guard decisions",
        "Agent limits (1h)",
        "Mock fallbacks (1h)",
        "AI metrics target up",
    } == set(panels)

    expressions = "\n".join(
        target["expr"]
        for panel in dashboard["panels"]
        for target in panel.get("targets", [])
    )
    assert "ai_service_latency_seconds" in expressions
    assert "llm_latency_seconds" in expressions
    assert "tool_latency_seconds" in expressions
    assert "request_id" not in expressions
    assert "chat_id" not in expressions
    assert "user_id" not in expressions
