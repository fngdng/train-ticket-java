Auth Service (Spring Boot)

Endpoints:
- POST /auth/register {username,password,fullName} -> {token}
- POST /auth/login {username,password} -> {token}

Env:
- spring.datasource.url (defaults to jdbc:postgresql://db:5432/train_ticket)
- spring.datasource.username
- spring.datasource.password

Run with Docker Compose in the parent folder.