# Security Self-Assessment — Transfer Platform

A threat model and security-control review of this two-service money-movement
system, mapped to the **NIST Cybersecurity Framework (CSF) 2.0** functions with
a STRIDE-based threat analysis of the transfer flow.

> **Scope and honesty note:** this is a self-assessment of an educational
> portfolio system, written to practice the discipline of controls analysis —
> it is not an audit, and the platform is not production banking
> infrastructure. Controls listed as *implemented* cite the exact code that
> implements them; everything else is listed openly as a **gap** with a
> proposed remediation. The gap list is intentionally the longest section.

## 1. System overview and trust boundaries

```
                        TRUST BOUNDARY 1: public edge
  Internet ──────────────────────────────────────────────────────────
      │  (k8s: LoadBalancer — the ONLY externally exposed service)
      ▼
  transfer-service (:8080)      owns Transfer records; orchestrates moves
      │
      │  TRUST BOUNDARY 2: internal service-to-service (REST)
      ▼  (k8s: ClusterIP — unreachable from outside the cluster)
  account-service (:8081)       owns Accounts and the money invariant
      │
      ▼
  H2 in-memory databases (one per service; no shared data store)
```

Assets at risk: account balances (integrity), transfer history (integrity /
auditability), service availability. There is no real money and no real PII in
this system; the assessment treats the assets as if there were.

## 2. Threat model (STRIDE on the transfer flow)

| Threat | Applies here as | Current control | Residual risk / gap |
|---|---|---|---|
| **S**poofing | Any caller can claim to be any account holder | **None — no authentication exists** | Open. Highest-priority gap (see §4, G-1) |
| **T**ampering | Malformed or manipulated amounts; concurrent balance corruption | Bean Validation on every DTO (`@DecimalMin("0.01")`, `@Digits(integer=17, fraction=2)` in `TransferRequest` / `AmountRequest`); money as `BigDecimal`, never floating point; optimistic locking via `@Version` on `Account` prevents lost updates from concurrent transfers | Transport is plain HTTP in dev/compose; TLS must terminate at the edge in any real deployment (G-2) |
| **R**epudiation | "That transfer never happened" / "I never authorized it" | Every transfer that reaches the money-movement step is recorded as a `Transfer` row with status `COMPLETED` or `FAILED` (failed rows carry a `failureReason`) and a `createdAt` timestamp — including the double-failure path where the compensating refund itself fails, whose row is flagged `REQUIRES_RECONCILIATION` and persisted *before* the error propagates; debits, credits, compensation, and refund failures are logged (`TransferService`, `AccountService`) | Without authentication there is no *actor* identity to bind to the record, so attribution is incomplete until G-1 is closed |
| **I**nformation disclosure | Stack traces, internals, or data leaking through errors and endpoints | `GlobalExceptionHandler` returns a uniform `ApiError` JSON shape and maps unexpected errors to a generic 500 with no internal detail; Actuator exposure is restricted to `/actuator/health` only (`application.properties`) | Swagger UI is open (acceptable for a demo; gate or disable in production); H2 uses default `sa` credentials (G-3) |
| **D**enial of service | Request floods; resource exhaustion | Kubernetes resource requests/limits (cpu 250m–500m, mem 256–512Mi) bound per-pod blast radius; liveness/readiness probes restart unhealthy pods | No rate limiting or request throttling at the API layer (G-4) |
| **E**levation of privilege | Any caller performing admin-shaped actions (open accounts, credit arbitrarily) | **None — there are no roles or permissions** | Open; same root cause as G-1 |

A note on the failure path: if a credit fails after a debit, `TransferService`
compensates by refunding the sender and records the attempt as `FAILED` with a
`failureReason`. If the *refund itself* fails, the code logs at `CRITICAL`
level and persists the `FAILED` row — flagged `REQUIRES_RECONCILIATION` —
*before* rethrowing, so the worst-case outcome (sender debited, refund failed)
reaches the audit trail even though the request errors out; if even that audit
write fails, the persistence error is attached to the refund failure as a
suppressed exception rather than masking it. Unit tests pin this behavior by
failing the credit and the refund together (`TransferServiceTest`) — a small
example of designing for the incident-response path, not just the happy path.

## 3. Control mapping — NIST CSF 2.0

| CSF function | Implemented in this repo | Evidence |
|---|---|---|
| **Govern (GV)** | Documented scope and risk posture: the README's "Scope and limitations" section explicitly states what this system is and is not, and names the controls a production payments system would require | `README.md` |
| **Identify (ID)** | Asset and data ownership is explicit: each service owns its own database and entities; the REST contract is the only shared surface; this document records the risk assessment (ID.RA) | Repo structure; this file |
| **Protect (PR)** | *Data security (PR.DS):* strict input validation on all DTOs; `BigDecimal` money; the no-overdraft invariant enforced inside the `Account` entity itself. *Identity (PR.AA):* — **gap, none**. *Platform security (PR.PS):* multi-stage Docker builds running a slim JRE (smaller attack surface); network segmentation via `ClusterIP` so account-service is never internet-reachable; config and service URLs injected via environment, never hardcoded | `AmountRequest.java`, `Account.debit()`, `Dockerfile`s, `k8s/*.yaml` |
| **Detect (DE)** | Structured logging of every money movement (debit, credit, compensation) with account IDs and amounts; health probes as basic liveness monitoring; CI runs the full test suite and validates compose + k8s manifests on every push | `TransferService.java`, `k8s/*.yaml`, `.github/workflows/ci.yml` |
| **Respond (RS)** | The compensation path is a designed failure response: refund the sender, persist a `FAILED` audit row, surface a `502` to the caller; a refund-failure is still persisted as a `FAILED` row flagged `REQUIRES_RECONCILIATION` before the error propagates, and escalates loudly for manual reconciliation instead of masking the incident | `TransferService.transfer()` / `compensate()` |
| **Recover (RC)** | **Largely a gap by design:** H2 in-memory storage means no durability and no restore path. The deployment docs note the intended remediation (managed Postgres via standard Spring datasource env vars, no code change) | `DEPLOY.md` (G-5) |

## 4. Gap assessment and remediation roadmap (prioritized)

| # | Gap | CSF ref | Proposed remediation |
|---|---|---|---|
| G-1 | No authentication or authorization on any endpoint | PR.AA | Spring Security with OAuth2/JWT; bind the authenticated principal to each `Transfer` row to complete the audit trail |
| G-2 | No TLS in the dev/compose path | PR.DS-02 | Terminate TLS at the ingress/load balancer; enforce HTTPS-only in production profiles |
| G-3 | Default H2 credentials (`sa`, blank password) | PR.AA-01 | Replace with managed Postgres and per-service least-privilege DB users, credentials from a secret store |
| G-4 | No rate limiting / abuse controls | PR.IR / DE.CM | Gateway-level rate limiting (e.g. per-client token bucket) in front of transfer-service |
| G-5 | No data durability or backup/restore | RC.RP | Managed Postgres with automated backups; document RTO/RPO targets |
| G-6 | No idempotency keys on transfer creation | PR.DS (integrity) | Client-supplied idempotency key persisted with the transfer to make retries safe |
| G-7 | No distributed tracing / centralized log aggregation | DE.CM | OpenTelemetry traces across the two services; ship logs to a central store so the compensation path is observable end to end |

## 5. References

- NIST Cybersecurity Framework 2.0 (NIST CSWP 29), function and category
  identifiers used above (GV, ID, PR, DE, RS, RC).
- STRIDE threat-modeling categories (spoofing, tampering, repudiation,
  information disclosure, denial of service, elevation of privilege).
