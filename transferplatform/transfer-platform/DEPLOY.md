# Deploying the Transfer Platform

Both services are containerized (each has a `Dockerfile`) and read config from the environment,
so they run on any container host. The only cross-service wiring is `ACCOUNT_SERVICE_URL`, which
transfer-service uses to reach account-service. Each app already honors the host-injected `PORT`.

## Option A — Render (`render.yaml` blueprint)

1. Push this repo to GitHub.
2. On [render.com](https://render.com): **New → Blueprint** → select this repo. Render reads
   `render.yaml` and creates both services from their Dockerfiles.
3. When prompted, set transfer-service's `ACCOUNT_SERVICE_URL` to the account-service URL Render
   assigns (e.g. `https://account-service-xxxx.onrender.com`).
4. transfer-service is your public entry point — Swagger UI at `<transfer-url>/swagger-ui.html`.

## Option B — Google Cloud Run (GCP)

Requires the `gcloud` CLI and a GCP project. Cloud Run builds each service from its `Dockerfile`
and injects `PORT` automatically.

```bash
# 1) Deploy account-service; note the URL it prints.
gcloud run deploy account-service \
  --source ./account-service --region us-central1 --allow-unauthenticated

# 2) Deploy transfer-service, pointed at the account-service URL from step 1.
gcloud run deploy transfer-service \
  --source ./transfer-service --region us-central1 --allow-unauthenticated \
  --set-env-vars ACCOUNT_SERVICE_URL=https://account-service-xxxx.run.app
```

transfer-service is your public URL; account-service can be locked down to internal ingress.

## Note on data

Both services use in-memory H2, so data resets on restart and there's nothing to provision —
ideal for a free-tier demo. For persistence, point each service at a managed Postgres via the
standard Spring datasource environment variables (no code change needed).
