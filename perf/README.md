# Hola Performance Test Assets

This directory contains repeatable performance-test assets for Hola.

## First Slice

The first implemented flow is the recommendation feed:

```text
GET /api/recommendations/videos?size=20
```

The purpose is to capture before/after evidence for portfolio-quality performance work:

- deterministic seed data
- k6 latency and error-rate results
- PostgreSQL `EXPLAIN ANALYZE` reports
- Grafana and Cloud Run screenshots
- code-state evidence

## Evidence Rule

A performance claim is incomplete unless it has both raw output and screenshots.

For recommendation feed, use:

```text
perf/results/recommendation-feed/before/
perf/results/recommendation-feed/after/
perf/results/recommendation-feed/local-baseline/
```

Each run directory should contain:

```text
k6-summary.json
k6-summary.txt
row-counts-and-sizes.txt
recommendation-feed-explain.txt
recommendation-feed-explain.json
code-state.txt
screenshots/
```

Screenshot names use this pattern:

```text
01-before-recommendation-feed-k6-summary.png
02-before-recommendation-feed-sql-plan.png
03-before-recommendation-feed-grafana-http-latency.png
```
