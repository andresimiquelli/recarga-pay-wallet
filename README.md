# Wallet Service

## Overview

The Wallet Service is a backend application responsible for managing digital wallets and processing financial transactions such as deposits, withdrawals, and transfers.

This service is designed as part of a distributed system, focusing exclusively on wallet and transaction management. Concerns such as authentication, authorization, and security are expected to be handled by other services in a production environment.

---

## Technologies

- Java 21 (LTS)
- Spring Boot
- PostgreSQL
- Docker & Docker Compose
- Testcontainers

---

## Summary
* [Getting started](#getting-started)
* [Configuration](#configuration)
* [Running tests](#running-tests)
* [Design decisions](#design-decisions)
* [API Endpoints](#api-endpoints)

---

## Getting started

### Prerequisites

- Docker
- Docker Compose

---

### Steps

1. Clone the repository

2. Make sure Docker is installed and running

3. Run the application:

```bash
docker-compose up --build
```

4. Access the API at:

http://localhost:8080

See also [API endpoints](#api-endpoints)

---

## Configuration

In a production or staging environment, environment variables would typically not be committed to the repository. However, for convenience and ease of testing, a `.env` file is provided with preconfigured environment variables.

---

## Running Tests

Tests are executed using Docker containers. This ensures that critical processes involving data persistence are validated against a database environment similar to production.

### Linux / macOS

```bash
./gradlew test
```

### Windows (PowerShell or CMD)

```bash
.\gradlew test
```

---

## Design Decisions

### Infrastructure: Java 21 + Virtual Threads

**Choice:** Enabled virtual threads with `spring.threads.virtual.enabled=true`.

**Reasoning:**  
Allows the application to handle thousands of concurrent transactions without exhausting OS thread pools, improving scalability.

---

### Database: PostgreSQL

**Choice:** PostgreSQL as the primary database.

**Reasoning:**
- Strong ACID compliance ensures financial consistency.
- Supports pessimistic locking (`SELECT FOR UPDATE`) to prevent double-spending.
- `NUMERIC` type ensures precise decimal calculations.
- Extensible with `JSONB` for audit-related data.

---

### Data Architecture: Double-Entry Bookkeeping

**Choice:** Implementation of a ledger system where each transaction generates two immutable records (debit and credit), linked by `related_transaction_id`.

**Reasoning:**
- Ensures financial integrity.
- Provides full traceability for auditing purposes.

---

### Concurrency Control: Pessimistic Locking

**Choice:** Use of `@Lock(PESSIMISTIC_WRITE)` on the Wallet entity.

**Reasoning:**
- Prevents "lost update" issues in concurrent operations.
- Guarantees accurate final balances.

---

### Idempotency

**Choice:** Use of `Idempotency-Key` header.

**Reasoning:**
- Prevents duplicate transactions caused by network failures.
- Ensures safe retry mechanisms.

---

### Testing Strategy: High-Fidelity Integration Tests

**Choice:** Use of Testcontainers instead of in-memory databases.

**Benefits:**
- **Environment Parity:** Tests run against a real PostgreSQL instance.
- **Concurrency Validation:** Proper testing of pessimistic locks and race conditions.
- **Determinism:** Each test suite runs in an isolated, clean environment.

---

## Trade-offs & Compromises

### Synchronous Processing

**Decision:** Transactions are processed synchronously within a single database transaction.

**Trade-off:**  
In large-scale systems, asynchronous processing (Kafka/RabbitMQ) could improve scalability, but synchronous processing guarantees immediate ACID consistency.

---

### Microservice Boundaries

**Decision:** Wallet and transaction logic are implemented in the same service.

**Trade-off:**  
A fully distributed architecture would separate these concerns, but a modular monolith reduces complexity and ensures faster delivery.

---

### Observability

**Decision:** Basic logging and health checks via Spring Actuator.

**Trade-off:**  
Advanced observability tools (Prometheus, Grafana, Zipkin, Jaeger) were not implemented to prioritize business logic and testing.

---

### Running Balance Strategy

**Decision:** Each transaction stores the resulting balance after execution.

**Benefits:**
- Fast balance queries.
- Efficient historical balance retrieval.

**Trade-off:**  
Requires pessimistic locking to ensure atomic updates, introducing slight write latency in exchange for read performance and audit reliability.

---

## API Endpoints

### Create Wallet

POST /api/wallets  
Header: Idempotency-Key

**Request Body:**
```json
{
  "userId": "e3edac8e-1484-435a-87dd-9adb90b4fc40",
  "alias": "joao@gmail.com"
}
```

---

### Deposit Funds

POST /api/wallets/{walletId}/deposits  
Header: Idempotency-Key

**Request Body:**
```json
{
  "amount": 200.00
}
```

---

### Withdraw Funds

POST /api/wallets/{walletId}/withdrawals  
Header: Idempotency-Key

**Request Body:**
```json
{
  "amount": 100.00
}
```

---

### Transfer Funds Between Wallets

POST /api/wallets/{walletId}/transfers  
Header: Idempotency-Key

**Request Body:**
```json
{
  "destinationWalletId": "677abf2f-0bff-47bb-8b1c-194400557ddc",
  "amount": 50.00
}
```

---

### Get Current Balance

GET /api/wallets/{walletId}/balance

---

### Get Balance at a Specific Time

GET /api/wallets/{walletId}/balance?balanceAt=ISO_DATE_TIME

**Example:**
2026-03-20T03:00:00Z
