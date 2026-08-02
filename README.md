# Sagas Example

A minimal **orchestrated saga** demo with three Spring Boot services. One service
(`orders`) coordinates a multi-step business transaction across service boundaries,
and compensates (undoes) already-committed steps when a later step fails.

| Service | Port | Role |
|---|---|---|
| `orders` | 8081 | Saga orchestrator: catalog + orders + compensation |
| `payments` | 8082 | Fake payment gateway |
| `events` | 8083 | Append-only event log |

## Saga steps

```
1. create order            → order CREATED, invoice PENDING
2. add items / reserve stock
3. submit                  → order PROCESSING
4. charge payment          → payments service (own DB)
5. COMPLETED  (payment ok)     or
   COMPENSATE (payment failed) → restore stock, invoice VOID, order FAILED
```

## Happy path (payment succeeds)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant O as orders (:8081)
    participant P as payments (:8082)
    participant E as events (:8083)

    C->>O: POST /orders {customerName}
    O->>O: save order CREATED + invoice PENDING
    O->>E: ORDER_CREATED
    C->>O: POST /orders/{id}/items {productId, quantity}
    O->>O: reserve stock (atomic UPDATE)
    C->>O: POST /orders/{id}/submit
    O->>O: status = PROCESSING
    O->>E: ORDER_PROCESSING
    O->>P: POST /payments/invoices {orderId, total, items}
    P->>E: PAYMENT_SUCCEEDED
    P-->>O: {status: SUCCESS, paymentId}
    O->>O: status = COMPLETED, invoice = PAID
    O->>E: ORDER_COMPLETED
```

## Compensation path (payment fails)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant O as orders (:8081)
    participant P as payments (:8082)
    participant E as events (:8083)

    C->>O: POST /orders {customerName}
    O->>O: save order CREATED + invoice PENDING
    C->>O: POST /orders/{id}/items {productId, quantity}
    O->>O: reserve stock
    C->>O: POST /orders/{id}/submit
    O->>O: status = PROCESSING
    O->>P: POST /payments/invoices
    P->>E: PAYMENT_FAILED
    P-->>O: {status: FAILED}
    O->>O: compensate: restore stock, invoice VOID, status FAILED
    O->>E: ORDER_FAILED
```

## Order state machine

```mermaid
stateDiagram-v2
    [*] --> CREATED: POST /orders
    CREATED --> CREATED: add item (reserve stock)
    CREATED --> PROCESSING: POST /submit
    PROCESSING --> COMPLETED: payment SUCCESS
    PROCESSING --> FAILED: payment FAILED (compensated)
    FAILED --> [*]
    COMPLETED --> [*]
```

## Payment failure rule

Payment fails when the invoice total is `> 1000.0` (`app.payment.fail-threshold`)
or `fail-mode` is `always` (toggled via `POST /payments/fail-mode`).

## Quick start

```bash
docker compose up -d --build   # build + start all three services
```

Swagger UI: `http://localhost:8081/swagger-ui.html` (also 8082, 8083).
