# Transfer Service

The **money-movement orchestrator** of the transfer platform, built with **Java 21 and
Spring Boot 3**. It records transfers and moves money by calling the **account-service**
over HTTP — it owns **no Account table** of its own.

A transfer debits the sender and credits the receiver across two separate HTTP calls.
Because those two legs are not one local database transaction, the credit is guarded by
**compensation**: if crediting the receiver fails after the sender has been debited, the
sender is refunded, the transfer is recorded as `FAILED`, and the caller gets a
`502 Bad Gateway`.

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 (Spring Web, Spring Data JPA, Bean Validation) |
| HTTP client | Spring `RestClient` (Spring Boot 3.3) |
| Persistence | Spring Data JPA over an embedded H2 database (Transfer rows only) |
| Testing | JUnit 5, Mockito, Spring MockMvc, `MockRestServiceServer`, AssertJ |

## Architecture

```
HTTP request
     │
     ▼
TransferController ── REST endpoints, request validation (@Valid)
     │
     ▼
TransferService    ── orchestration + compensation
     │                         │
     ▼                         ▼
TransferRepository      AccountClient ──HTTP──▶ account-service
     │
     ▼
H2 (transfers)
```

`@RestControllerAdvice` (`GlobalExceptionHandler`) maps domain exceptions to HTTP status
codes, so controllers stay thin and every error returns the same JSON shape.

## API

| Method | Path | Description | Success |
|---|---|---|---|
| `POST` | `/api/transfers` | Send money | `201 Created` |
| `GET` | `/api/transfers/{id}` | Get one transfer | `200 OK` |
| `GET` | `/api/accounts/{id}/transfers` | An account's transfer history (newest first) | `200 OK` |

### Error responses

| Situation | Status |
|---|---|
| Invalid request body (validation) | `400 Bad Request` |
| Same-account or cross-currency transfer | `400 Bad Request` |
| Account or transfer not found | `404 Not Found` |
| Insufficient funds (from account-service) | `422 Unprocessable Entity` |
| Receiver could not be credited (sender refunded) | `502 Bad Gateway` |

All errors share one JSON shape: `timestamp`, `status`, `error`, `message`, and (for
validation failures) a `fieldErrors` map.

## Configuration

| Property | Env var | Default |
|---|---|---|
| `server.port` | `PORT` | `8080` |
| `account-service.url` | `ACCOUNT_SERVICE_URL` | `http://localhost:8081` |

## Running locally

Requires JDK 21 and a running **account-service** (default `http://localhost:8081`).

```bash
mvn spring-boot:run
```

## API documentation

- **Swagger UI** — `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON** — `http://localhost:8080/v3/api-docs`

## Run with Docker

```bash
docker build -t transfer-service .
docker run -p 8080:8080 -e ACCOUNT_SERVICE_URL=http://account-service:8081 transfer-service
```

## Testing

```bash
mvn test
```

- **`TransferServiceTest`** — orchestration and compensation rules with a mocked
  `AccountClient`; no Spring context, no database, no running account-service.
- **`AccountClientTest`** — verifies HTTP-to-domain-exception mapping against a
  `MockRestServiceServer` (404 → not found, 422 → insufficient funds).
- **`TransferControllerTest`** — `@WebMvcTest` + MockMvc verifies status codes,
  validation, and exception-to-status mapping with the service mocked.
- **`TransferServiceApplicationTests` / `OpenApiDocsTest`** — context wiring and the
  served OpenAPI document.

## Design notes

- **Money is `BigDecimal`, never `double`** — floating point can't represent currency exactly.
- **No shared code between services** — this service owns its own DTOs; `AccountDto` is a
  read-only projection of account-service's response, tolerant of unknown fields.
- **Compensation, not distributed transactions** — the debit/credit legs live in different
  services, so a failed credit is undone by an explicit refund rather than a rollback.
