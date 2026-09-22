# Aora Equipment Management System

Spring Boot REST API for equipment, users, worksites, stores, transfers, maintenance, and audit logs.

## Stack

- Java 25
- Spring Boot
- Spring Security with OAuth2 Resource Server
- Spring Data JPA
- PostgreSQL
- Flyway

## Run

Configure PostgreSQL in `src/main/resources/application.yml`, then run:

```bash
mvn spring-boot:run
```

The API base path is `/api/v1`.
