# Payment Gateway

A production-inspired payment gateway built using **Java 21**, **Spring Boot**, and a **modular microservices architecture**. The project demonstrates the complete payment lifecycle, including Payment Intents, Checkout Sessions, Payment Processing, Webhooks, and Reconciliation.

> **Note**
> This is a personal learning project built from scratch for educational purposes. It is not affiliated with or derived from any proprietary payment gateway implementation.

---

## Features

- Payment Intent creation
- Checkout Session generation
- Secure checkout flow
- Payment authorization and capture
- Stripe integration
- Razorpay integration
- Webhook processing
- Kafka-based event publishing
- Payment reconciliation
- API Key authentication for merchant APIs
- Bean Validation and centralized exception handling
- Modular Maven architecture

---

## Tech Stack

- Java 21
- Spring Boot 3
- Spring MVC
- Spring Data JPA
- Spring Security
- Maven
- Apache Kafka
- MySQL
- Stripe SDK
- Razorpay SDK
- Lombok

---

## Project Structure

```
PaymentGateway
│
├── api                     # REST APIs
├── common                  # Shared DTOs, enums, exceptions
├── domain                  # JPA entities
├── intent-service          # Payment Intent management
├── session-service         # Checkout Session management
├── processor-service       # Payment processor integrations
├── payment-service         # Payment business logic
├── webhook-service         # Webhook delivery
└── reconciliation-service  # Payment reconciliation
```

---

## Payment Flow

```
Merchant
    │
    ▼
Create Payment Intent
    │
    ▼
Create Checkout Session
    │
    ▼
Customer Checkout
    │
    ▼
Payment Processor
(Stripe / Razorpay)
    │
    ▼
Payment Result
    │
    ├── Update Payment Status
    ├── Publish Kafka Event
    └── Trigger Merchant Webhook
```

---

## REST APIs

### Payment Intent

```
POST /v1/payment-intents
GET  /v1/payment-intents/{id}
```

Creates and retrieves payment intents.

---

### Checkout Session

```
POST /v1/payment-sessions
```

Creates a checkout session associated with a payment intent.

---

### Checkout

```
POST /v1/checkout/{sessionId}/pay
```

Processes a customer payment through the configured payment processor.

---

## Architecture Highlights

- Modular Maven project
- Clear separation of API, domain, and business logic
- Event-driven communication using Kafka
- Provider abstraction for payment processors
- Centralized exception handling
- Validation using Jakarta Bean Validation
- Extensible architecture for adding new payment providers

---

## Supported Payment Providers

- Stripe
- Razorpay

The processor layer is designed to support additional providers with minimal changes.

---

## Getting Started

### Clone

```bash
git clone https://github.com/<your-username>/PaymentGateway.git
```

### Build

```bash
mvn clean install
```

### Run

```bash
mvn spring-boot:run
```

---

## Future Enhancements

- Refund APIs
- Partial captures
- Payment retries
- Idempotency support
- Redis caching
- Docker & Docker Compose
- Kubernetes deployment
- Prometheus & Grafana monitoring
- CI/CD with GitHub Actions
- Integration tests using Testcontainers

---

## Learning Objectives

This project was built to explore:

- Payment gateway architecture
- Microservices design
- Payment lifecycle
- Event-driven systems
- Third-party payment integrations
- Webhook processing
- Modular application design
- Spring Boot best practices

---

## License

This project is intended for educational and demonstration purposes.