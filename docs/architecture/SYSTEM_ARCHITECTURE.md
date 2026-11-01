# System Architecture

## Recommendation

Use a multi-tenant modular monolith with one deployable Spring Boot API, a shared PostgreSQL 16/PostGIS database, Redis for bounded ephemeral/cache workloads, a React web application, and an Expo React Native Android application. This is appropriate for a 10–20-shop pilot and keeps operations within reach of one primary developer. Modules have explicit ownership and interfaces so extraction remains possible without paying distributed-systems cost now.

## Logical components

```text
Customer Android (Expo) ─┐
Public storefront / Web ─┼─ HTTPS ─ Nginx ─ Spring Boot REST API
Seller/Admin Web ────────┘                      │
                                      ┌─────────┴─────────┐
                                      PostgreSQL + PostGIS  Redis
                                      │                   │
                              Flyway migrations      cache/rate limits
                                      │
                         Object storage adapter / SMS / Razorpay
```

External integrations are behind interfaces: OTP delivery, file storage, and subscription gateway. The initial storage implementation can be selected at deployment planning; public media must not expose private seller/admin documents. Razorpay is used for platform subscription billing only. Customer order UPI is seller-directed and never routed through the platform.

## Backend module boundaries

- `identity`: users, OTP/session lifecycle, roles/permissions, admin MFA.
- `tenancy`: tenant membership, request context, RLS transaction context.
- `shops`: onboarding, approval, storefront profile, addresses, hours, fulfilment options.
- `catalog`: categories, products, variants, images, prices/offers.
- `inventory`: stock levels, movements, reservation/checkout concurrency.
- `marketplace`: public search, geo discovery, storefront read APIs.
- `orders`: cart validation, single-shop checkout, status history, payment method/status.
- `subscriptions`: plans, subscriptions, invoices, payment attempts, gateway webhook processing.
- `notifications`: templates, in-app/push/SMS orchestration and device tokens.
- `files`: upload policy and storage adapter.
- `reviews`: eligibility, submission, moderation state.
- `audit`: append-only sensitive-action records.
- `admin`: privileged use cases composed over domain services; not a bypass around tenant ownership without explicit policy.
- `reporting`: read-oriented seller/admin summaries.

Modules may share DTOs only through deliberate contracts; no controller-to-controller calls. Domain services own transactions. Scheduled work is idempotent, observable, and uses the same authorization/data-scope rules as synchronous paths.

## Repository layout

```text
local-shop-os/
  backend/
    src/main/java/in/localshop/os/
      identity/ tenancy/ shops/ catalog/ inventory/ marketplace/
      orders/ subscriptions/ notifications/ files/ reviews/ audit/ admin/ reporting/
      common/  (error, config, security, observability)
    src/main/resources/db/migration/
    src/test/java/in/localshop/os/ (unit, repository, integration, api)
  web/
    src/app/ src/features/{auth,marketplace,seller,admin}/ src/shared/
    src/api/ src/theme/ src/routes/
  mobile/
    app/ src/features/{auth,discovery,shop,cart,orders,profile}/ src/shared/ src/api/
  infrastructure/{compose,nginx,docker,monitoring,github-actions}/
  docs/{product,architecture,development,testing,deployment}/
  scripts/
```

Use package-by-feature and keep controllers thin. JPA entities never cross the API boundary. Flyway owns schema evolution; production Hibernate schema mode is validation only.

## Request and tenant flow

1. Authenticate access token and resolve user identity/roles.
2. For seller operations, resolve active tenant membership server-side; reject missing/ambiguous membership or unauthorized tenant selection.
3. Begin transaction and set transaction-local PostgreSQL tenant context before tenant-table access.
4. Apply service-level ownership checks and RLS policies. Public marketplace queries use explicit approved/public visibility and do not receive seller authority.
5. Record audit event for sensitive mutations; return DTOs.

Admin cross-tenant operations use explicit permission checks and audited service paths. Background jobs must set tenant context per unit of work or use narrowly scoped administrative policies; connection-pool session state must never leak between requests.

## Data and integrations

PostgreSQL is authoritative for identity, catalog, stock, orders, and billing state. Redis is non-authoritative and has expiry for rate-limit/cache entries. Files use an adapter and database metadata; uploads are size/type checked, access controlled, and preferably scanned before public use. Razorpay webhooks are persisted before processing, signature-verified against raw request bytes, deduplicated, and retried safely. Notifications are best effort and cannot roll back a committed order.

## Environments and operations

Local: Docker Compose services for PostgreSQL/PostGIS, Redis, and backend dependencies. CI: compile/lint/test, migration checks, frontend checks, and container build. Production: Ubuntu 24.04 VPS, Nginx TLS reverse proxy, containerized app and managed/secured database strategy decided in deployment plan, encrypted off-host backups, least-privilege secrets, health/readiness probes, metrics, structured logs, and a documented rollback. Keep production and staging credentials/data separate.

## Scale and extraction signals

Do not split services for the pilot. Revisit extraction only when measured bottlenecks, distinct scaling needs, compliance boundaries, or independent team ownership justify it. Search, notifications, and reporting are the most plausible future candidates; their current interfaces should remain module-local and explicit.
