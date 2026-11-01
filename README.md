# Local Shop OS

Subscription-based hyperlocal marketplace SaaS for local shops in India. The initial pilot targets 10–20 shops in Nagpur, Maharashtra.

## Product rules

- Shops pay a monthly or annual subscription; customer orders carry 0% platform commission.
- Customers pay shops directly by seller-managed UPI or COD. Local Shop OS does not hold or settle customer order funds.
- Sellers manage their own pickup and delivery.
- Each order belongs to one shop. Each shop has a shareable storefront URL and can appear in the shared marketplace.
- The MVP tenant boundary is one shop, with multiple users able to belong to a tenant.

## Project status

Planning documentation is established. Application source code and local development services have not yet been initialized. Do not assume a runnable app or invent setup commands; implementation tickets define the sequence.

## Technology direction

- Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, PostgreSQL 16/PostGIS, Redis, Flyway.
- Web: React, TypeScript, Vite, Material UI.
- Android: React Native, TypeScript, Expo.
- Operations: Docker/Compose, Nginx, Ubuntu 24.04, GitHub Actions, Prometheus, Grafana.

## Documentation

- [Product requirements](docs/product/PRODUCT_REQUIREMENTS.md), [business model](docs/product/BUSINESS_MODEL.md), and [MVP scope](docs/product/MVP_SCOPE.md)
- [System architecture](docs/architecture/SYSTEM_ARCHITECTURE.md), [database design](docs/architecture/DATABASE_DESIGN.md), [API design](docs/architecture/API_DESIGN.md), and [security architecture](docs/architecture/SECURITY_ARCHITECTURE.md)
- [Development roadmap](docs/development/DEVELOPMENT_ROADMAP.md), [phases](docs/development/DEVELOPMENT_PHASES.md), [implementation tickets](docs/development/IMPLEMENTATION_TICKETS.md), [sprint plan](docs/development/SPRINT_PLAN.md), and [Codex workflow](docs/development/CODEX_WORKFLOW.md)
- [Test strategy](docs/testing/TEST_STRATEGY.md), [acceptance criteria](docs/testing/ACCEPTANCE_CRITERIA.md), [deployment plan](docs/deployment/DEPLOYMENT_PLAN.md), and [production readiness](docs/deployment/PRODUCTION_READINESS.md)

## Starting implementation

Work from the first ready ticket in `docs/development/IMPLEMENTATION_TICKETS.md`. Resolve its dependencies and acceptance criteria before adding application code. Keep the initial architecture a modular monolith; use Flyway for database changes and DTOs at API boundaries.
