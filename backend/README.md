# Backend placeholder

This directory contains the Java 21 / Spring Boot 3.5.16 modular monolith foundation. Spring Boot 3.5.16 is pinned to honor the requested 3.x line. Spring's published system requirements support Java 21, and its release notice identifies 3.5.16 as the final 3.5 OSS release; resolve commercial support or an upgrade path before production. See the [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) and [3.5.16 release notice](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/).

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

Each feature owns its application/domain logic and exposes deliberate interfaces. Controllers map HTTP to DTOs; JPA entities remain internal. Flyway owns schema changes, and production Hibernate schema generation stays disabled. This foundation has not yet added persistence or authentication.

## Local run

After LSOS-012, Compose provisions `localshop_runtime` plus the `localshop_app` login. The API uses `localshop_app` for runtime queries; Flyway uses the separate `localshop` migration login locally. In production, run migrations in the release pipeline and set `DB_MIGRATIONS_ENABLED=false` for API processes.

Use Java 21 and Maven 3.6.3 or later:

```sh
cd backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Start the local PostgreSQL/PostGIS service first using [Compose](../infrastructure/compose/README.md). The local profile connects as the non-owner `localshop_app` runtime role and runs Flyway with the separate local `localshop` migration role. Defaults match Compose; if you override Compose values, set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_MIGRATION_USERNAME`, and `DB_MIGRATION_PASSWORD` in the shell running Maven. Outside the local profile database credentials must be supplied explicitly; the application has no production credential defaults. Production runtime credentials must not own application tables or have `BYPASSRLS`.

The process listens on port 8080 by default. Health and readiness probes are available under `/actuator/health`; only the health actuator endpoint is exposed over HTTP. Flyway runs versioned migrations at startup, and Hibernate validates mappings without changing the schema. With the `local` profile, OpenAPI JSON is available at `/v3/api-docs/v1` and Swagger UI at `/swagger-ui`; these endpoints are disabled by default outside local development.

OpenAPI is grouped under `/api/v1/**` and defines the bearer JWT security scheme for authenticated API operations. Public, OTP, and seller/admin operations will be annotated individually as their controllers are added; the scheme is not applied globally.

## Tenant isolation integration tests

The RLS integration suite starts an isolated PostgreSQL 16/PostGIS container, applies every Flyway migration as the migration owner, and runs assertions through the restricted runtime login. Run it from the repository root with Docker available:

```sh
mvn -f backend/pom.xml -Dtest=TenantRlsIntegrationTest test
```

The suite covers public store visibility, seller cross-tenant access, onboarding ownership, identity role provisioning, platform administration, and transaction-local context cleanup.

Springdoc 2.10.0 is used with Spring Boot 3.5; its compatibility matrix lists Spring Boot 3.5 with springdoc 2.8.x–2.10.x ([compatibility matrix](https://springdoc.org/v2/)).

See [system architecture](../docs/architecture/SYSTEM_ARCHITECTURE.md), [database design](../docs/architecture/DATABASE_DESIGN.md), and [API design](../docs/architecture/API_DESIGN.md).
