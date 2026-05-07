# Train Ticket Platform — Architecture & Security Overview

This document captures the high-level architecture, component responsibilities, and the required security model you specified.

## Components
- API Gateway (reverse proxy, rate limiting, TLS termination)
- Auth Service (OAuth2 token endpoint, JWT issuance, user registration)
- Users Service (user profiles, KYC/CCCD storage)
- Trains Service (schedules, carriage layouts, seat maps)
- Bookings Service (reserve seats, issue tickets, QR + digital signature)
- Payments Service (VNPay / Momo integration, transaction logging)
- Admin Service (reports, revenue, system metrics)
- Frontend (React) + Mobile clients
- Database: PostgreSQL (primary), optional SQL Server support
- Crypto key store: private key for digital signatures (operator-held)

## Security Goals (by weight)
- Authentication (40%): OAuth2 flows + JWT access tokens (+ refresh tokens). Short-lived access tokens.
- Encryption (35%): TLS for all transport, AES-256 for sensitive data at rest, PCI-DSS compliance for payments.
- Access Control (25%): Role-based access control (RBAC), fine-grained permissions.

## Auth & Session
- Use OAuth2 Authorization Server pattern (Auth Service).
- Issue JWT access tokens signed with RS256 (private key on auth server).
- Use refresh tokens (stored securely, revocable).
- Support third-party Identity Providers via OAuth2 connectors.

## Data Protection
- All endpoints served over HTTPS/TLS; certificates handled at gateway / ingress.
- Passwords hashed with bcrypt.
- Payment data encrypted with AES-256 and only stored where necessary; follow PCI-DSS.

## Ticket Anti-Fraud (Digital Signature + QR)
- Ticket payload: TicketID | Seat | CarriageType | CCCD | Price
- Compute message digest (SHA-256).
- Sign digest with operator private RSA key → digital signature.
- Embed data + signature in QR payload (compact JSON/base64).
- Validator (mobile/handheld) has public key to verify signature.

## DB Choice
- Default: PostgreSQL (recommended). SQL Server supported; use separate migration scripts.
- Use Flyway or Liquibase for migrations.

## Dev/Deploy
- Containerize each service; orchestrate with Docker Compose for dev and Kubernetes for production.
- CI: build, unit tests, integration tests, container image push.

## Next Steps (scaffold plan)
1. Scaffold Spring Boot `auth` microservice with OAuth2/JWT + user registration/login.
2. Add PostgreSQL schema + Flyway migrations.
3. Scaffold placeholder services: `trains`, `bookings`, `payments`, `admin` (APIs only).
4. Create React skeleton with login and train list pages.
5. Add Dockerfiles and `docker-compose.yml` to run Postgres + auth + web.
6. Implement digital-signature helper (RSA) and QR generator in `bookings` later.

