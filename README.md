Train Ticket Java scaffold

Quickstart (requires Docker):

1. Build and run services:

```bash
cd train-ticket-java
docker-compose up --build
```

2. Open the demo UI: http://localhost:8081
3. Auth API: http://localhost:8080/auth

Notes:
- Flyway runs migrations on startup to create the `users` table.
- For production: replace the generated JWT secret with a safe keystore and configure TLS at the gateway.
