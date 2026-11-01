# Backend placeholder

This directory is reserved for the Java 21 / Spring Boot 3 modular monolith. No application build or source code has been initialized yet. Backend setup is tracked in LSOS-005 and LSOS-006.

## Intended layout

```text
src/main/java/in/localshop/os/
  identity/ tenancy/ shops/ catalog/ inventory/ marketplace/
  orders/ subscriptions/ notifications/ files/ reviews/ audit/ admin/ reporting/
  common/
src/main/resources/
  db/migration/
src/test/java/in/localshop/os/
  unit/ repository/ integration/ api/
```

Each feature owns its application/domain logic and exposes deliberate interfaces. Controllers map HTTP to DTOs; JPA entities remain internal. Flyway owns schema changes, and production Hibernate schema generation stays disabled. See [system architecture](../docs/architecture/SYSTEM_ARCHITECTURE.md), [database design](../docs/architecture/DATABASE_DESIGN.md), and [API design](../docs/architecture/API_DESIGN.md).
