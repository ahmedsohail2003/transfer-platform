# Transfer Platform

[![CI](https://github.com/ahmedsohail2003/transfer-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ahmedsohail2003/transfer-platform/actions/workflows/ci.yml)

An **e-Transfer-style money-movement platform** split into **two Spring Boot microservices**
that communicate over REST. It started as a single service and was decomposed to practice
distributed backend design: independent services, service-to-service calls, and **saga-style
compensation** when a cross-service operation partially fails.

## Architecture

```
                 ┌──────────────────────┐         REST          ┌──────────────────────┐
   client  ───▶  │   transfer-service   │  ─────────────────▶   │   account-service    │
   (HTTP)        │       (:8080)        │   debit / credit /    │       (:8081)        │
                 │  owns Transfers      │   get account         │  owns Accounts +     │
                 │  orchestrates moves  │ ◀─────────────────    │  the money invariant │
                 └──────────┬───────────┘                       └──────────┬───────────┘
                            ▼                                              ▼
                     H2 (transfers)                                 H2 (accounts)
```

Each service is an independent Spring Boot app with its **own database, own deployable jar,
and own tests** — they share no code, only the REST contract.

| Service | Port | Owns | Responsibilities |
|---|---|---|---|
| **account-service** | 8081 | Accounts + balances | Open accounts, read balances, `debit`/`credit` with the no-overdraft invariant (returns `422` on insufficient funds) |
| **transfer-service** | 8080 | Transfers | Orchestrates a transfer by calling account-service; records each transfer; serves transfer history |

## The transfer flow (and why compensation matters)

Because money now moves **across two services**, a transfer is no longer a single database
transaction. `transfer-service` runs a small saga and **compensates** on partial failure:

1. Reject same-account transfers (`400`).
2. Fetch both accounts from account-service (`404` if either is missing).
3. Reject currency mismatches (`400`).
4. **Debit** the sender via account-service (`422` if insufficient funds — abort, nothing moved).
5. **Credit** the receiver. **If the credit fails, compensate** by crediting the sender back
   (a refund), record the transfer as `FAILED`, and return `502`.
6. On success, record the transfer as `COMPLETED` and return `201`.

## Tech stack

Java 21 · Spring Boot 3 (Web, Data JPA, Validation, Actuator) · Spring `RestClient` for
service-to-service calls · H2 · springdoc/OpenAPI · JUnit 5 + Mockito + MockMvc · Docker ·
Docker Compose · GitHub Actions.

## Run with Docker Compose

```bash
docker compose up --build
```

- transfer-service → `http://localhost:8080`
- account-service → `http://localhost:8081`

Compose injects `ACCOUNT_SERVICE_URL=http://account-service:8081` so transfer-service can
reach account-service over the shared network.

## Run locally without Docker

Two terminals (JDK 21 required):

```bash
# Terminal 1 — account-service on :8081
cd account-service && mvn spring-boot:run

# Terminal 2 — transfer-service on :8080, pointed at account-service
cd transfer-service && ACCOUNT_SERVICE_URL=http://localhost:8081 mvn spring-boot:run
```

Example end-to-end session:

```bash
# Open two accounts (on account-service)
curl -s -X POST localhost:8081/api/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","email":"alice@example.com","openingBalance":100.00,"currency":"CAD"}'
curl -s -X POST localhost:8081/api/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Bob","email":"bob@example.com","openingBalance":0.00,"currency":"CAD"}'

# Send $40 Alice -> Bob (on transfer-service, which calls account-service)
curl -s -X POST localhost:8080/api/transfers -H 'Content-Type: application/json' \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":40.00,"memo":"lunch"}'

# Balances now reflect the move (on account-service)
curl -s localhost:8081/api/accounts/1/balance   # 60.00
curl -s localhost:8081/api/accounts/2/balance   # 40.00
```

## API documentation

Each service self-documents via OpenAPI/Swagger once running:

- transfer-service → `http://localhost:8080/swagger-ui.html`
- account-service → `http://localhost:8081/swagger-ui.html`

## Kubernetes

Production-shaped manifests live in [`k8s/`](k8s/): deployments with liveness/readiness probes
(`/actuator/health`) and resource requests/limits, a dedicated namespace, and a Kustomize
entrypoint. **account-service is a `ClusterIP`** (internal only); **transfer-service is a
`LoadBalancer`** (the single public entry point). The manifests are schema-validated with
`kubeconform`.

```bash
kubectl apply -k k8s/
kubectl -n transfer-platform get pods,svc
```

## Deploy to the cloud

Both services are containerized and cloud-ready — see [`DEPLOY.md`](DEPLOY.md) for step-by-step
instructions for **Render** (via the `render.yaml` blueprint) and **Google Cloud Run**. Each
service binds to the platform-injected `PORT`, and transfer-service reads `ACCOUNT_SERVICE_URL`
to locate account-service.

## Testing & CI

```bash
cd account-service && mvn test     # account-service suite
cd transfer-service && mvn test    # transfer-service suite (account-service mocked)
```

GitHub Actions (`.github/workflows/ci.yml`) builds and tests **both** services on every
push and pull request via a matrix job. CI also validates the Docker Compose configuration
and Kubernetes manifests.

## Scope and limitations

This is an educational portfolio system, not production banking infrastructure. It uses
in-memory H2 databases and a synchronous REST-orchestrated compensation flow. A production
payments system would additionally require durable messaging, idempotency keys, authentication
and authorization, persistent databases, distributed tracing, reconciliation tooling, and
formal security and compliance controls.
