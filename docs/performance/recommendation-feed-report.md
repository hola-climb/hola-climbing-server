# Recommendation Feed Performance Report

## Summary

Target API:

```text
GET /api/recommendations/videos?size=20
```

Goal:

```text
Measure baseline p95/p99, identify PostgreSQL bottlenecks with EXPLAIN ANALYZE,
apply query or index improvements, and compare before/after evidence.
```

## Environment

| item | local baseline | before | after |
|---|---:|---:|---:|
| git commit |  |  |  |
| database | hola_perf | hola_perf | hola_perf |
| service | local Spring Boot | hola-backend-perf | hola-backend-perf |
| Cloud Run max instances | n/a | 1 | 1, then 2..3 |
| seed users | 10,000 | 10,000 | 10,000 |
| seed gyms | 1,000 | 1,000 | 1,000 |
| seed videos | 100,000 | 100,000 | 100,000 |
| seed follows | 300,000 | 300,000 | 300,000 |

## Local Baseline Evidence

### Code State

![local baseline code state](../../perf/results/recommendation-feed/local-baseline/screenshots/01-local-recommendation-feed-code-state.png)

### k6 Summary

![local baseline k6 summary](../../perf/results/recommendation-feed/local-baseline/screenshots/02-local-recommendation-feed-k6-summary.png)

### SQL Plan

![local baseline sql plan](../../perf/results/recommendation-feed/local-baseline/screenshots/03-local-recommendation-feed-sql-plan.png)

### k6 Scenario Code

![local baseline k6 scenario code](../../perf/results/recommendation-feed/local-baseline/screenshots/04-local-recommendation-feed-k6-script.png)

## Before Evidence

### Code State

![before code state](../../perf/results/recommendation-feed/before/screenshots/01-before-recommendation-feed-code-state.png)

### Code Snapshot

![before code snapshot](../../perf/results/recommendation-feed/before/screenshots/06-before-recommendation-feed-code.png)

### k6 Summary

![before k6 summary](../../perf/results/recommendation-feed/before/screenshots/02-before-recommendation-feed-k6-summary.png)

### SQL Plan

![before sql plan](../../perf/results/recommendation-feed/before/screenshots/03-before-recommendation-feed-sql-plan.png)

### Grafana HTTP Latency

![before grafana http latency](../../perf/results/recommendation-feed/before/screenshots/04-before-recommendation-feed-grafana-http-latency.png)

### Cloud Run Metrics

![before cloud run metrics](../../perf/results/recommendation-feed/before/screenshots/05-before-recommendation-feed-cloud-run-metrics.png)

## After Evidence

### Code State

![after code state](../../perf/results/recommendation-feed/after/screenshots/11-after-recommendation-feed-code-state.png)

### Code Change

![after code change](../../perf/results/recommendation-feed/after/screenshots/16-after-recommendation-feed-code-change.png)

### k6 Summary

![after k6 summary](../../perf/results/recommendation-feed/after/screenshots/12-after-recommendation-feed-k6-summary.png)

### SQL Plan

![after sql plan](../../perf/results/recommendation-feed/after/screenshots/13-after-recommendation-feed-sql-plan.png)

### Grafana HTTP Latency

![after grafana http latency](../../perf/results/recommendation-feed/after/screenshots/14-after-recommendation-feed-grafana-http-latency.png)

### Cloud Run Metrics

![after cloud run metrics](../../perf/results/recommendation-feed/after/screenshots/15-after-recommendation-feed-cloud-run-metrics.png)

## Result Table

| metric | local baseline | before | after | change |
|---|---:|---:|---:|---:|
| k6 p50 |  |  |  |  |
| k6 p95 |  |  |  |  |
| k6 p99 |  |  |  |  |
| error rate |  |  |  |  |
| total requests |  |  |  |  |
| SQL execution time |  |  |  |  |
| shared read buffers |  |  |  |  |

## Bottleneck

Describe the concrete SQL plan finding and link the screenshot next to the claim.

## Change

Describe the query, index, or application change and link the code screenshot next to the claim.

## Interpretation

Explain why the result changed and what remains risky.

## Evidence Files

Raw evidence directories:

```text
perf/results/recommendation-feed/local-baseline/
perf/results/recommendation-feed/before/
perf/results/recommendation-feed/after/
```

Every performance claim must link raw output and screenshots.
