# Test Strategy

## Quality goals

Demonstrate business invariants and security boundaries, especially tenant isolation, single-shop orders, stock consistency, and subscription payment integrity. Test at the cheapest layer that can prove a rule, then retain end-to-end coverage for critical user journeys. Tests use synthetic data and never real customer/payment credentials.

## Test layers and tools

- **Unit:** JUnit 5/Mockito for domain decisions, validation, state transitions, price/stock rules, and idempotency logic.
- **Repository/database:** Spring Data tests and Testcontainers PostgreSQL 16 with PostGIS; apply real Flyway migrations, constraints, indexes, and RLS. Avoid H2 as a substitute for PostgreSQL behavior.
- **Integration/API:** Spring Boot integration tests with real security filters, transactions, tenant context, error mapping, and mocked external providers. Contract-check OpenAPI.
- **Web:** React Testing Library/Vitest (or selected Vite-compatible equivalent) for forms, permissions, responsive states, API errors, empty/loading states.
- **Mobile:** React Native Testing Library/Jest or Expo-compatible tooling for navigation, secure session lifecycle, permission prompts, and API failure behavior.
- **E2E:** Browser automation for public/seller/admin web and Android emulator/device automation for customer flows. Use deterministic provider fakes and seeded data.
- **Performance/security:** k6 or equivalent for discovery/checkout; dependency/image scans and focused security tests; staging penetration review before launch.

## Required scenarios

### Authentication and authorization

Valid/invalid/expired/replayed OTP; resend and per-phone/IP throttles; refresh rotation, replay-family revocation, logout/device revoke; disabled user/member; role denial; admin MFA gate; generic enumeration-resistant response; safe token storage and log redaction.

### Tenant isolation

Tenant A seller reads/updates/deletes Tenant B store/product/order/inventory/subscription; forged tenant ID in URL/body/header; customer attempts another user's address/cart/order; missing tenant context; tenant context reset across pooled connections and rollback; unauthorized admin cross-tenant access; permitted admin action is audited; background job scopes tenants correctly.

### Catalogue and discovery

Only approved/published shop and active products are public; PostGIS radius/distance edge cases; inactive/out-of-stock filtering; pagination tie-breaking; invalid/bounded radius; image MIME/signature/size spoofing; query performance with pilot-shaped data.

### Cart, checkout, orders

Attempt mixed-shop cart; stale price and unavailable product; duplicate idempotency key; two buyers checkout final unit concurrently; seller disables fulfilment before checkout; invalid/unowned address; UPI/COD method recording; order status transition invalid/duplicate/concurrent; cancellation/restock cases once policy is decided; customer and seller ownership; notification failure does not roll back order.

### Subscription and webhooks

Razorpay valid/invalid signature; duplicate event; same payload under different event ID; out-of-order and delayed event; processing crash/retry; client redirect without webhook; manual payment pending until admin verifies; unauthorized/repeated verification; amount/currency mismatch; invoice immutability; reconciliation mismatch alert.

### UI and E2E journeys

Applicant submits shop → admin approves → seller publishes product → customer discovers and orders → seller updates fulfilment/status → customer sees status and reviews. Include UPI handoff and COD paths, empty shop/catalogue, network timeout/retry, invalid form, permission denied, offline app recovery, deep link to storefront/order, subscription success/failure/manual verification.

## Concurrency and performance

Run parallel checkout against limited stock and assert no oversell or duplicate order effects. Load nearby discovery/search and common storefront reads using realistic Nagpur coordinates/catalogue cardinality; measure p95 latency, database connections, query plans, cache hit behavior, and error rate. Establish explicit launch SLO targets after baseline measurement rather than inventing unsupported numbers.

## CI and release gates

CI runs formatting/lint/type checks, backend unit tests, frontend tests, migration validation, API contract generation/check, and container build. Integration/Testcontainers and E2E can be separated into required CI stages if runtime is high, but must gate release. No critical/high tenant, auth, payment, or data-loss defect may ship. Save test report and release candidate commit; never use production credentials in CI.
