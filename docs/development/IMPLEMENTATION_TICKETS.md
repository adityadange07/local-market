# Implementation Tickets — First 30

These tickets are intentionally scoped as independently reviewable slices. Estimates are developer hours. P0 means pilot-blocking foundation/vertical slice; P1 means needed soon after core. API DTO shapes remain subject to the approved API design. “Definition of Done” for every ticket: reviewed change, relevant tests passing, documentation/API updated as applicable, no secret/PII leakage, and acceptance evidence recorded.

## LSOS-001 — Repo foundation and contribution conventions

- **Epic:** Developer Platform. **Description:** Establish root repository conventions, branch/commit/PR guidance, ignore rules, and ownership of docs. **Business value:** Makes work reproducible and reviewable.
- **Technical requirements:** Choose formatting/lint baseline and supported tool versions; document scripts without installing dependencies. **Affected modules:** Repository/docs. **Database:** None. **API:** None. **UI:** None.
- **Implementation steps:** Add root README/contribution guide; define naming and quality gates; add safe ignore patterns; link docs. **Dependencies:** None.
- **Tests:** Validate documented commands/paths and ignore patterns; clean status contains no generated secrets. **Acceptance criteria:** A new contributor can identify project layout, workflow, and checks. **DoD:** Shared definition above plus guide reviewed.
- **Priority:** P0. **Estimate:** 8 h.

## LSOS-002 — Monorepo structure and application placeholders

- **Epic:** Developer Platform. **Description:** Create `backend/`, `web/`, `mobile/`, `infrastructure/`, `docs/`, `scripts/` layout and concise module ownership notes. **Business value:** Separates deployable concerns while enabling shared planning.
- **Technical requirements:** Keep modules independently buildable; no speculative features. **Affected modules:** Repository. **Database/API/UI:** Empty scaffolding only; no schema, endpoints, or screens.
- **Implementation steps:** Create folders/readmes; add root navigation; document ownership and generated-output locations. **Dependencies:** LSOS-001.
- **Tests:** Verify expected paths and no accidental binaries. **Acceptance criteria:** All planned areas have clear owners and can be initialized independently. **DoD:** Shared definition; structure matches architecture docs.
- **Priority:** P0. **Estimate:** 8 h.

## LSOS-003 — Local Docker Compose services

- **Epic:** Developer Platform. **Description:** Provide repeatable local PostgreSQL 16/PostGIS and Redis services. **Business value:** Gives developers production-relevant database behavior.
- **Technical requirements:** Health checks, persistent local volume, non-production credentials, private network, documented lifecycle. **Affected modules:** Infrastructure/backend. **Database:** PostgreSQL/PostGIS service only. **API/UI:** None.
- **Implementation steps:** Add Compose config; configure health checks and ports; document start/stop/reset and local-only secrets. **Dependencies:** LSOS-002.
- **Tests:** Start services, verify health and PostGIS extension, restart with volume. **Acceptance criteria:** Fresh developer can start dependencies and connect locally. **DoD:** Shared definition; no production secrets/default exposure.
- **Priority:** P0. **Estimate:** 8 h.

## LSOS-004 — CI build and static checks

- **Epic:** Developer Platform. **Description:** Add GitHub Actions baseline for changed application modules. **Business value:** Finds regressions before merge.
- **Technical requirements:** Cache safely; fail on lint/type/compile/test errors; no production secret requirement. **Affected modules:** CI and all apps. **Database/API/UI:** No schema or behavior changes.
- **Implementation steps:** Add workflow; configure Java/Node toolchains after project manifests exist; publish concise test artifacts. **Dependencies:** LSOS-002.
- **Tests:** Validate workflow syntax and run on a sample branch/PR. **Acceptance criteria:** Required checks report status and cannot pass on failing compile/lint. **DoD:** Shared definition; documented checks.
- **Priority:** P0. **Estimate:** 8 h.

## LSOS-005 — Backend Spring Boot module skeleton

- **Epic:** Backend Foundation. **Description:** Create Java 21/Spring Boot 3 application with package-by-feature structure and environment configuration. **Business value:** Establishes backend base without mixing business rules into controllers.
- **Technical requirements:** Health endpoint, profiles, centralized config, constructor injection, feature packages, no schema auto-create. **Affected modules:** Backend/common. **Database:** Connection configuration only. **API:** Health endpoint. **UI:** None.
- **Implementation steps:** Initialize build; add module packages and app entrypoint; configure health/readiness; add config validation. **Dependencies:** LSOS-002, LSOS-003.
- **Tests:** Context starts with test profile; health check responds; missing required production config fails safely. **Acceptance criteria:** Application starts locally and has no business logic in controllers. **DoD:** Shared definition; Java version fixed/documented.
- **Priority:** P0. **Estimate:** 12 h.

## LSOS-006 — Flyway baseline and PostgreSQL/PostGIS setup

- **Epic:** Backend Foundation. **Description:** Establish migration execution and schema validation behavior. **Business value:** Prevents environment-dependent schema drift.
- **Technical requirements:** Flyway owns DDL; production Hibernate validate; migration naming/order; PostGIS extension availability documented. **Affected modules:** Backend/infrastructure. **Database:** Baseline/extension bootstrap only, no speculative domain tables. **API/UI:** None.
- **Implementation steps:** Configure Flyway; add minimal baseline migration if required; disable create/update; document migration workflow. **Dependencies:** LSOS-003, LSOS-005.
- **Tests:** Migrate clean Testcontainers PostgreSQL; verify extension and Hibernate validation. **Acceptance criteria:** Repeated clean setup produces expected schema state. **DoD:** Shared definition; no auto-DDL production.
- **Priority:** P0. **Estimate:** 12 h.

## LSOS-007 — Shared API error and validation contract

- **Epic:** Backend Foundation. **Description:** Define API error envelope, validation handling, trace IDs, and generic server error mapping. **Business value:** Clients can handle failures consistently without information leaks.
- **Technical requirements:** Stable code/message/fieldErrors/traceId; no stack traces or sensitive rejected values. **Affected modules:** Backend/common. **Database:** None. **API:** Common error DTO and exception mapping. **UI:** Contract only.
- **Implementation steps:** Add DTO and handler; map validation/auth/not-found/conflict/unexpected cases; correlate logs. **Dependencies:** LSOS-005.
- **Tests:** MVC tests for status/body mapping and redaction. **Acceptance criteria:** Invalid input and unexpected failure return stable, safe responses. **DoD:** Shared definition; API docs updated.
- **Priority:** P0. **Estimate:** 8 h.

## LSOS-008 — OpenAPI configuration and API conventions

- **Epic:** Backend Foundation. **Description:** Publish versioned API documentation and security/error conventions. **Business value:** Allows web/mobile work against an explicit contract.
- **Technical requirements:** OpenAPI generation, auth schemes, DTO schemas, no entity exposure, stable `/api/v1` base. **Affected modules:** Backend/API. **Database:** None. **API:** OpenAPI endpoint/artifact. **UI:** Contract consumption guidance.
- **Implementation steps:** Configure generator; document error and pagination schemas; add CI contract artifact. **Dependencies:** LSOS-005, LSOS-007.
- **Tests:** Generate/validate OpenAPI and check representative endpoint schema. **Acceptance criteria:** API spec is accessible in dev and reproducible in CI. **DoD:** Shared definition; design doc linked.
- **Priority:** P1. **Estimate:** 8 h.

## LSOS-009 — Identity/domain migration design approval

- **Epic:** Identity and Tenancy. **Description:** Review/finalize initial identity, role, tenant, store, and RLS model before DDL. **Business value:** Avoids costly identity and tenant migration rework.
- **Technical requirements:** Decide UUID policy, scoped roles, tenant context, composite ownership, public/admin paths. **Affected modules:** Architecture/backend. **Database:** Design only. **API/UI:** Confirm auth and onboarding contracts.
- **Implementation steps:** Review DATABASE_DESIGN and SECURITY docs; capture decisions/open items; approve migration sequence and policy tests. **Dependencies:** Phase 0, LSOS-006.
- **Tests:** Model review against tenant attack cases; validate all required ownership relationships represented. **Acceptance criteria:** No unresolved decision blocks foundational tables. **DoD:** Signed decision record linked from docs.
- **Priority:** P0. **Estimate:** 8 h.

## LSOS-010 — Users, roles, permissions schema migration

- **Epic:** Identity and Tenancy. **Description:** Add identity and global RBAC core tables. **Business value:** Enables secure authentication and authorization.
- **Technical requirements:** Normalized phone, account status, role/permission keys, timestamps, constraints and indexes. **Affected modules:** Identity. **Database:** `users`, `roles`, `permissions`, `user_roles`, `role_permissions`. **API/UI:** None yet.
- **Implementation steps:** Write migration; map entities/repositories; add seed mechanism only for stable system roles. **Dependencies:** LSOS-009.
- **Tests:** PostgreSQL migration/constraints/repository tests; duplicate normalized phone rejected. **Acceptance criteria:** Identity/RBAC schema migrates cleanly and enforces unique grants. **DoD:** Shared definition; migration forward-only.
- **Priority:** P0. **Estimate:** 12 h.

## LSOS-011 — Tenants, memberships, and stores schema migration

- **Epic:** Identity and Tenancy. **Description:** Add shop tenant, membership, and store ownership model. **Business value:** Establishes the shop as the MVP isolation boundary.
- **Technical requirements:** One store per tenant in MVP; tenant/user membership unique; composite ownership keys. **Affected modules:** Tenancy/shops. **Database:** `tenants`, `tenant_members`, `stores`. **API/UI:** None.
- **Implementation steps:** Add ordered migration; map relationships; add repository ownership methods. **Dependencies:** LSOS-010.
- **Tests:** FK/unique constraints and tenant/store relationship tests. **Acceptance criteria:** A user can belong to tenant and each tenant has at most one MVP store. **DoD:** Shared definition; design contract aligned.
- **Priority:** P0. **Estimate:** 12 h.

## LSOS-012 — Tenant context and RLS enforcement foundation

- **Epic:** Identity and Tenancy. **Description:** Set transaction-local tenant context and enforce RLS for tenant tables. **Business value:** Prevents cross-shop data exposure.
- **Technical requirements:** Context derived from authenticated active membership; transaction-local setting; safe pool cleanup; explicit admin policy. **Affected modules:** Tenancy/security. **Database:** RLS policies for initial tenant tables. **API:** Request context integration. **UI:** None.
- **Implementation steps:** Add tenant resolver/filter; set context in transaction; configure DB roles/policies; document job/admin access. **Dependencies:** LSOS-011.
- **Tests:** Correct tenant sees own rows; wrong/missing context denied; rollback/pool reuse does not leak context. **Acceptance criteria:** RLS works with app role and normal requests. **DoD:** Shared definition; threat test evidence.
- **Priority:** P0. **Estimate:** 16 h.

## LSOS-013 — Tenant isolation integration test fixtures

- **Epic:** Identity and Tenancy. **Description:** Create reusable multi-tenant Testcontainers fixtures and adversarial API tests. **Business value:** Makes isolation a regression gate.
- **Technical requirements:** Real PostgreSQL/RLS, tenant A/B, customer/seller/admin principals. **Affected modules:** Test infrastructure/security. **Database/API/UI:** Test-only.
- **Implementation steps:** Add builders/fixtures; seed independent tenants; cover direct ID and membership cases; wire CI. **Dependencies:** LSOS-012.
- **Tests:** Cross-tenant reads/writes, customer ownership, admin audit, pooled connection reuse. **Acceptance criteria:** Tests fail on a deliberately bypassed ownership/RLS check. **DoD:** Shared definition; no real PII.
- **Priority:** P0. **Estimate:** 12 h.

## LSOS-014 — OTP provider interface and secure OTP storage

- **Epic:** Identity and Tenancy. **Description:** Create SMS provider port and secure short-lived OTP challenge persistence. **Business value:** Supports Indian SMS vendor selection without coupling login domain to vendor.
- **Technical requirements:** Hash OTP with server secret, purpose/expiry/attempt state, provider abstraction, secrets externalized. **Affected modules:** Identity/notifications. **Database:** OTP challenge table/fields finalized with retention. **API/UI:** Internal provider contract only.
- **Implementation steps:** Define port and fake provider; implement challenge service; store keyed hash; configure expiry and clock. **Dependencies:** LSOS-010.
- **Tests:** Hash verification, expiry, single use, provider failure and no plaintext persistence/logging. **Acceptance criteria:** OTP cannot be recovered from DB/logs; provider can be swapped. **DoD:** Shared definition; vendor remains configurable.
- **Priority:** P0. **Estimate:** 12 h.

## LSOS-015 — OTP request/verify endpoints and throttling

- **Epic:** Identity and Tenancy. **Description:** Expose OTP request/verify flow with abuse controls and safe account creation policy. **Business value:** Enables customer/seller login.
- **Technical requirements:** Phone normalization, purpose binding, generic responses, resend/per-phone/IP/device limits, atomic single-use. **Affected modules:** Identity/API. **Database:** Challenge status/attempt fields. **API:** `POST /auth/otp/request`, `POST /auth/otp/verify`. **UI:** Auth contract.
- **Implementation steps:** Add DTOs/validation; connect provider port; implement limits and success user resolution; document abuse metrics. **Dependencies:** LSOS-014.
- **Tests:** valid/invalid/expired/replay, throttling, provider outage, enumeration-safe response. **Acceptance criteria:** OTP login succeeds once and cannot be brute-forced/replayed under configured policy. **DoD:** Shared definition; rate limits observable.
- **Priority:** P0. **Estimate:** 16 h.

## LSOS-016 — JWT access and rotating refresh sessions

- **Epic:** Identity and Tenancy. **Description:** Issue access tokens and maintain revocable refresh-token families. **Business value:** Provides persistent secure sessions across clients.
- **Technical requirements:** JWT issuer/audience/expiry; hashed refresh secrets; rotation/reuse detection; web/mobile storage contract. **Affected modules:** Identity/security. **Database:** Refresh session/token family table. **API:** refresh/revoke endpoints. **UI:** Auth lifecycle contract.
- **Implementation steps:** Add signing config; persist hash/family; implement refresh rotation and logout/device revocation; document clients. **Dependencies:** LSOS-015.
- **Tests:** expiration, refresh rotation, replay revocation, logout, malformed token. **Acceptance criteria:** Reused refresh token revokes family; access token claims are minimal. **DoD:** Shared definition; keys never committed.
- **Priority:** P0. **Estimate:** 16 h.

## LSOS-017 — Role and permission authorization policies

- **Epic:** Identity and Tenancy. **Description:** Add deny-by-default permission evaluation for customer, seller, and admin actions. **Business value:** Restricts privileged operations to authorized actors.
- **Technical requirements:** Route and service checks; tenant membership role; admin permission keys; no authority from client fields. **Affected modules:** Identity/security/all API. **Database:** Role grants/seed definitions. **API:** Security annotations/policies. **UI:** Role-aware navigation contract.
- **Implementation steps:** Define initial role matrix; implement authorization service; protect sample endpoints; document permission additions. **Dependencies:** LSOS-010, LSOS-016.
- **Tests:** Each role permitted/denied; disabled membership; service method direct invocation denial. **Acceptance criteria:** Unmapped endpoints/actions are inaccessible by default. **DoD:** Shared definition; matrix documented.
- **Priority:** P0. **Estimate:** 12 h.

Status: **Implemented**. Spring method security and a database-backed `PermissionAuthorization` evaluator are in place for global and active tenant grants. The device-session revocation route and service both require the customer device permission; service user identity comes from the authenticated security context. Automated tests cover permitted/denied global and tenant grants and suspended membership.

## LSOS-018 — Admin MFA flow

- **Epic:** Identity and Tenancy. **Description:** Require second factor for admin sessions and sensitive changes. **Business value:** Reduces impact of compromised admin credentials.
- **Technical requirements:** MFA enrollment/recovery policy decision, challenge expiry, rate limit, step-up verification; avoid logging secrets. **Affected modules:** Identity/admin. **Database:** MFA credential/recovery metadata protected. **API:** Admin MFA endpoints. **UI:** Admin enrollment/login challenge.
- **Implementation steps:** Select TOTP or equivalent; implement enrollment/verification; gate admin authorization until verified; add recovery procedure. **Dependencies:** LSOS-016, LSOS-017.
- **Tests:** valid/invalid/expired/replay, non-MFA token denied admin route, recovery audit. **Acceptance criteria:** Admin functions unavailable until MFA complete. **DoD:** Shared definition; operator recovery documented.
- **Priority:** P1. **Estimate:** 16 h.

Status: **Implemented**. MVP uses encrypted TOTP seeds, database-enforced step replay prevention, ten hashed one-time recovery codes, and a restricted MFA event log. Elevated admin access is represented by a five-minute step-up JWT and checked through `PermissionAuthorization.hasAdminPermission`.

## LSOS-019 — Audit event model and sensitive-action logging

- **Epic:** Identity and Tenancy. **Description:** Record actor, scope, action, object, time, correlation, and redacted change summary. **Business value:** Supports accountability and incident response.
- **Technical requirements:** Append-only insert path, access control, PII redaction, transaction policy defined per action. **Affected modules:** Audit/common/admin. **Database:** `audit_logs`. **API:** Admin audit read contract later. **UI:** None.
- **Implementation steps:** Add schema/service; define event catalog; instrument approval/payment/role mutations as they arrive. **Dependencies:** LSOS-010, LSOS-017.
- **Tests:** required actions emit one event; no secret fields; unauthorized read blocked. **Acceptance criteria:** Sensitive mutation is traceable to actor and tenant. **DoD:** Shared definition; retention owner noted.
- **Priority:** P1. **Estimate:** 12 h.

Status: **Implemented**. V9 adds forced-RLS, append-only audit storage; `AuditEventService` records the current actor and correlation ID in the business transaction, `AuditRedactor` bounds and sanitizes JSON summaries, and audit reads require an MFA-verified platform permission. MFA lifecycle actions now emit shared audit records. Future approval/payment/order/team mutations will call this service as those modules are implemented.

## LSOS-020 — Seller onboarding and shop approval APIs

- **Epic:** Shop Operations. **Description:** Let seller apply and admin approve/reject with recorded reason. **Business value:** Brings pilot shops onto a controlled marketplace.
- **Technical requirements:** Application status state machine; duplicate/application rules; approval permission and audit. **Affected modules:** Shops/tenancy/admin. **Database:** Application/status/review metadata. **API:** Seller application; admin review endpoints. **UI:** Seller status/admin queue contract.
- **Implementation steps:** Add domain/service/repository; define transition rules; expose DTOs; audit decisions. **Dependencies:** LSOS-011, LSOS-017, LSOS-019.
- **Tests:** valid transitions, unauthorized review, duplicate application, unpublished before approval. **Acceptance criteria:** Approved shop may proceed; rejected shop remains non-public with reason. **DoD:** Shared definition; API docs/tests updated.
- **Priority:** P0. **Estimate:** 20 h.

Status: **Implemented**. Seller submission atomically creates the tenant, draft store, and owner membership; it transitions to pending review. Admin review is MFA/permission gated, records rejection reasons, audits the decision, and rejects stale/repeated transitions. Tests cover private pending state, approval without publication, and rejection behavior.

## LSOS-021 — Store profile, address, hours, and fulfillment APIs

- **Epic:** Shop Operations. **Description:** Manage public shop profile and seller-defined pickup/delivery availability. **Business value:** Gives customers accurate local shop information and fulfilment choices.
- **Technical requirements:** Tenant-owned profile; normalized public slug; PostGIS point/address separation; hours/fulfilment validation. **Affected modules:** Shops/marketplace. **Database:** Store address, hours, fulfillment settings; geography index. **API:** Seller store CRUD and public summary. **UI:** Seller setup/storefront contract.
- **Implementation steps:** Add migrations/entities; ownership-checked services; validate hours and coordinates; expose DTOs. **Dependencies:** LSOS-020.
- **Tests:** slug uniqueness, geo constraints, tenant denial, public visibility state. **Acceptance criteria:** Seller config is scoped and public only after approval/publish. **DoD:** Shared definition; migration and OpenAPI reviewed.
- **Priority:** P0. **Estimate:** 16 h.

Status: **Implemented**. V11 adds tenant-owned address, schedule, and fulfillment tables with PostGIS indexing and RLS. Seller APIs resolve active tenant permissions before entering tenant context; public storefront reads require approved and published state. Publishing validates the complete weekly schedule and fulfillment configuration.

## LSOS-022 — Category and product schema/API

- **Epic:** Catalogue. **Description:** Let sellers create and manage categorized products and prices. **Business value:** Establishes the offer catalog customers browse.
- **Technical requirements:** DTO-only, active/public state, currency/minor units, tenant-scoped SKU, price validation. **Affected modules:** Catalog/shops. **Database:** `categories`, `products`; product ownership constraints. **API:** Seller product/category assignment CRUD; public DTO groundwork. **UI:** Seller catalogue contract.
- **Implementation steps:** Finalize category policy; add migrations/entities; implement service/repository/controller; add filters/pagination. **Dependencies:** LSOS-011, LSOS-021.
- **Tests:** tenant SKU uniqueness, price validation, hidden product public denial, tenant access. **Acceptance criteria:** Seller can create/update/archive own product and assign active category. **DoD:** Shared definition; OpenAPI and tests.
- **Priority:** P0. **Estimate:** 24 h.

## LSOS-023 — Variants, inventory, and stock movements

- **Epic:** Catalogue. **Description:** Track sellable variants, available quantity, and attributable adjustments. **Business value:** Reduces unavailable orders and gives sellers stock accountability.
- **Technical requirements:** Transactional stock changes; nonnegative invariant; immutable movement history; checkout concurrency design. **Affected modules:** Catalog/inventory/orders. **Database:** `product_variants`, `inventory`, `stock_movements`. **API:** Seller adjustment and availability endpoints. **UI:** Variant/stock editor contract.
- **Implementation steps:** Decide reservation/decrement semantics; add schema and constraints; implement adjustment service with actor/reason; expose APIs. **Dependencies:** LSOS-022.
- **Tests:** concurrent adjustment, underflow prevention, tenant isolation, movement audit completeness. **Acceptance criteria:** Every stock change has one attributable movement and quantity remains valid. **DoD:** Shared definition; concurrency strategy documented.
- **Priority:** P0. **Estimate:** 24 h.

## LSOS-024 — Secure product image upload flow

- **Epic:** Catalogue. **Description:** Upload and associate product media using storage adapter. **Business value:** Makes storefront catalogue useful and trustworthy.
- **Technical requirements:** File type/signature/size checks, generated keys, authorization, public/private separation, malware scan capability. **Affected modules:** Files/catalog. **Database:** `product_images` metadata. **API:** Presigned/direct upload flow selected in design. **UI:** Seller image upload state.
- **Implementation steps:** Choose initial object storage adapter; implement upload policy and metadata; link to product; add cleanup for abandoned uploads. **Dependencies:** LSOS-022.
- **Tests:** spoofed MIME/extension, oversize, unauthorized tenant, inaccessible private object. **Acceptance criteria:** Only valid authorized media can be attached and public serving follows publish state. **DoD:** Shared definition; storage credentials protected.
- **Priority:** P1. **Estimate:** 16 h.

## LSOS-025 — Marketplace search and nearby shop APIs

- **Epic:** Marketplace. **Description:** Expose bounded search and geospatial discovery for approved shops/products. **Business value:** Connects local customers to relevant shops.
- **Technical requirements:** PostGIS radius/distance; allowlisted filters, deterministic pagination, published/active predicates. **Affected modules:** Marketplace/shops/catalog. **Database:** GiST and query indexes. **API:** `/marketplace/shops/nearby`, shops/products search. **UI:** Discovery/search contract.
- **Implementation steps:** Implement repository queries; set radius/page limits; return distance and summary DTO; capture query metrics. **Dependencies:** LSOS-021, LSOS-022.
- **Tests:** radius boundary, ordering, inactive visibility, pagination, query plan on pilot data. **Acceptance criteria:** Results are limited to approved/published entities and bounded by request limits. **DoD:** Shared definition; performance baseline recorded.
- **Priority:** P0. **Estimate:** 20 h.

## LSOS-026 — Customer address, cart, and cart item APIs

- **Epic:** Checkout. **Description:** Let customers maintain owned addresses and a cart scoped to one shop. **Business value:** Prepares a safe checkout journey.
- **Technical requirements:** Address ownership, active cart/shop rule, cart revalidation, product/variant scope. **Affected modules:** Customer/cart/catalog. **Database:** `customer_addresses`, `carts`, `cart_items`. **API:** Customer address CRUD and cart read/update. **UI:** Web/mobile cart contract.
- **Implementation steps:** Define one-active-cart policy; add migrations/services; enforce same-shop items; expose DTOs. **Dependencies:** LSOS-015, LSOS-022.
- **Tests:** forged address, mixed shop add, inactive item, quantity bounds, tenant/shop mismatch. **Acceptance criteria:** Cart cannot contain more than one shop and only customer can mutate it. **DoD:** Shared definition; cart policy documented.
- **Priority:** P0. **Estimate:** 20 h.

## LSOS-027 — Single-shop checkout and inventory-safe order creation

- **Epic:** Orders. **Description:** Convert a valid cart to an order with payment choice and stock consistency. **Business value:** Core marketplace transaction without platform fund custody.
- **Technical requirements:** Transactional price/stock/address/fulfillment revalidation; idempotency; order item snapshots; UPI/COD records; seller receives payment directly. **Affected modules:** Orders/inventory/customer. **Database:** Orders/items/payment record/status initial row. **API:** `POST /customer/checkout`. **UI:** Checkout/receipt contracts.
- **Implementation steps:** Resolve stock reservation/decrement and cancellation policy; implement transaction and unique idempotency key; return seller payment instructions when UPI. **Dependencies:** LSOS-023, LSOS-026.
- **Tests:** final-unit concurrency, stale price, retry, invalid address, UPI/COD, mixed shop, failure rollback. **Acceptance criteria:** At most one order reserves each available unit; retry returns same result; platform never collects order funds. **DoD:** Shared definition; load/concurrency evidence.
- **Priority:** P0. **Estimate:** 28 h.

## LSOS-028 — Seller order management and status history

- **Epic:** Orders. **Description:** Let seller view own orders and move through approved order statuses. **Business value:** Enables seller-managed fulfilment and customer tracking.
- **Technical requirements:** State machine, optimistic concurrency, actor/time history, delivery/pickup fields. **Affected modules:** Orders/seller/notifications. **Database:** `order_status_history`, order status/version. **API:** Seller list/detail/status update; customer history/detail. **UI:** Seller order queue/detail and customer tracking.
- **Implementation steps:** Agree status/cancel policy; implement transitions/ownership; emit notification event; paginate results. **Dependencies:** LSOS-027.
- **Tests:** illegal/duplicate/concurrent transitions, cross-tenant read/update, history append-only. **Acceptance criteria:** Seller can update only own order; customer sees current state/history. **DoD:** Shared definition; transitions documented.
- **Priority:** P0. **Estimate:** 20 h.

## LSOS-029 — Subscription plans and invoice domain/API

- **Epic:** Subscription Billing. **Description:** Create configurable plans, tenant subscriptions, and immutable invoices. **Business value:** Establishes platform recurring revenue records.
- **Technical requirements:** Monthly/annual intervals; currency/minor units; lifecycle state and policy; immutable invoice snapshots; no customer order coupling. **Affected modules:** Subscriptions/tenancy/admin. **Database:** Plans, subscriptions, invoices. **API:** Plan list/current subscription/invoice endpoints. **UI:** Seller billing/admin plan contracts.
- **Implementation steps:** Resolve plan versioning and grace behavior; add schema/services; generate invoice number/snapshot; expose scoped APIs. **Dependencies:** LSOS-011, LSOS-017, LSOS-019.
- **Tests:** tenant scoping, price snapshot immutability, plan changes, duplicate current subscription policy. **Acceptance criteria:** Admin configures plans and tenant sees its subscription/invoices only. **DoD:** Shared definition; open billing decisions tracked.
- **Priority:** P0. **Estimate:** 20 h.

## LSOS-030 — Razorpay and manual subscription payment verification

- **Epic:** Subscription Billing. **Description:** Accept gateway events and manually verified offline subscription payments. **Business value:** Supports chosen payment options while preserving payment integrity.
- **Technical requirements:** Signature on raw webhook, durable event inbox, event dedup/retries, provider idempotency, amount/currency check, admin verification and audit. **Affected modules:** Subscriptions/payments/admin. **Database:** Payment attempts and webhook events. **API:** Razorpay webhook, manual payment submission/verification. **UI:** Seller payment status/admin verification.
- **Implementation steps:** Integrate Razorpay adapter/test mode; persist/verify/process events; create manual pending record and admin action; add reconciliation state. **Dependencies:** LSOS-029.
- **Tests:** invalid/duplicate/out-of-order webhook, retry after crash, redirect-only rejection, manual pending/admin verify, amount mismatch. **Acceptance criteria:** Only signature-verified gateway event or admin-verified manual receipt activates; replay is harmless. **DoD:** Shared definition; credentials secret-managed; audit trail reviewed.
- **Priority:** P0. **Estimate:** 24 h.

## Dependency map and parallelization

Primary chain: `001 → 002 → 003/005 → 006 → 007/008 → 009 → 010 → 011 → 012 → 013`; then `014 → 015 → 016 → 017 → 020 → 021 → 022 → 023 → 025`; checkout chain `015 + 022 → 026 → 027 → 028`; billing chain `017 + 011 → 019/029 → 030`.

After DTOs are agreed, web/mobile shells and design components can progress alongside backend tickets; these first 30 tickets focus primarily on platform and vertical-slice APIs. `018` may proceed after session/RBAC foundations. `024` can proceed alongside inventory once product ownership is stable. Keep shared migrations, tenant/auth, checkout, and subscription state transitions serialized. Subsequent tickets cover notification delivery, dashboards, full Android journeys, E2E, security/performance, deployment, and pilot operations as laid out in `DEVELOPMENT_PHASES.md`.
# LSOS-022 status

**Implemented:** V12 category/product schema and seed data, tenant-isolated seller product CRUD and lifecycle APIs, active category and public storefront product reads, DTO validation, security route configuration, and PostGIS-backed tenant RLS integration coverage. Focused backend suite passed on 2026-11-03.

# LSOS-023 status

**Implemented:** V13 adds tenant-scoped variants, per-variant nonnegative inventory, and immutable attributable stock movements. Seller APIs create/list variants, read balances/history, and make reasoned stock adjustments. Adjustments use `SELECT ... FOR UPDATE` and update balance plus movement atomically; underflow and integer overflow are rejected. Checkout reservations/decrements remain in LSOS-027. PostGIS integration coverage verifies initial zero stock, adjustment history, underflow rollback, and cross-tenant denial.

# LSOS-024 status

**Implemented:** V14 adds tenant-scoped product-image metadata with generated UUID object keys, content signature/type, size, SHA-256, scan state, ordering, and creator. Direct multipart uploads are limited to 5 MiB and JPEG/PNG/WebP signatures. Storage is behind `FileStorage` with a path-constrained local adapter; the scanner is pluggable and defaults to `PENDING_SCAN`. Public bytes require a clean scan and approved/published shop/product state under RLS. Integration and unit tests cover media visibility, signature rejection, oversize files, and tenant denial. Production requires object-storage and scanner adapters before public activation.
