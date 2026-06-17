# Production Observability

Runs Prometheus and Grafana on `hola-climbing-server`.

Ports bind to `127.0.0.1` only. Access them through an SSH tunnel:

```bash
gcloud compute ssh hola-climbing-server \
  --project=hola-climbing-log \
  --zone=asia-northeast3-a \
  -- -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090
```

Then open:

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`

Required runtime files on the VM:

- `.env` with `GRAFANA_ADMIN_PASSWORD`
- `secrets/metrics-token` with the same value as GCP Secret Manager `hola-metrics-token`
