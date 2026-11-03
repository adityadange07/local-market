# Database Design

## Modeling rules

PostgreSQL 16 with PostGIS; UUID primary keys; UTC timestamps; Flyway migrations; Hibernate validates schema in deployed environments. Use foreign keys and unique/check constraints to encode durable invariants. Add `created_at`, `updated_at` to mutable records; add `created_by`/`updated_by` where attributable. Use `deleted_at` for recoverable user-facing entities where retention policy permits. Financial and audit history is append-only rather than soft-deleted.

`tenant_id` is required for tenant-owned seller data and is immutable after creation. Derive tenant identity from membership, set transaction-local RLS context, and retain service-level checks. Customer identities may interact with many shops; customer addresses and carts are user-owned. Orders carry both customer identity and the owning shop tenant so seller scope and indexes are explicit.

## Entity relationship overview

```text
users ─< user_roles >─ roles ─< role_permissions >─ permissions
  ├─< tenant_members >─ tenants ─1 stores ─< store_addresses
  │                                  ├─< store_hours
  │                                  ├─< products ─< product_images
  │                                  │             ├─< product_variants ─1 inventory
  │                                  │             └─< inventory ─< stock_movements
  │                                  └─< subscriptions >─ subscription_plans
  │                                       └─< subscription_invoices ─< payment_attempts
  ├─< customer_addresses
  ├─< otp_challenges (phone/purpose/expiry/attempt-bound authentication challenges)
  ├─< auth_sessions ─< refresh_tokens (rotating token family)
  ├─0..1 admin_mfa_credentials ─< admin_mfa_recovery_codes
  │                         └─< admin_mfa_events (restricted, append-only)
  ├─< carts ─< cart_items >─ product_variants
  ├─< orders ─< order_items; orders ─< order_status_history
  │      ├─< payment_records
  │      └─< reviews
  ├─< device_tokens; users ─< notifications
  └─< audit_logs
webhook_events are linked to gateway/payment context and deduplicated by provider event ID.
```

## Tables and ownership

| Table | Ownership / important relationships | Core constraints and indexes |
|---|---|---|
| `users` | Global identity, referenced by all actor records | unique normalized phone; status index; timestamps |
| `otp_challenges` | Global auth challenge bound to phone and purpose | keyed HMAC only; expiry, bounded attempts, delivery/consume state; phone/time, expiry, retention indexes; 30-day terminal retention target |
| `otp_rate_limits` | HMAC-keyed request counters for phone, IP, and device windows | composite dimension/hash key; atomic upsert; stale bucket cleanup after one day |
| `auth_sessions` | User/device refresh session family with fixed expiry and revocation state | user/expiry index; device ID stored as keyed hash; family is revoked on replay/logout |
| `refresh_tokens` | One-time token hashes belonging to an auth session | unique SHA-256 hash; consumed/replacement linkage; session/expiry indexes; no raw token persistence |
| `admin_mfa_credentials` | Optional platform-admin TOTP seed and verification state | AES-GCM encrypted seed; pending enrollment expiry; monotonic verified step and bounded lockout fields; self-only RLS |
| `admin_mfa_recovery_codes` | One-time high-entropy recovery credentials | SHA-256 only; unique per user; used timestamp; self-only RLS |
| `admin_mfa_events` | MFA enrollment, success, recovery, and failure events | append-only; runtime insert-only; self actor RLS; user/time index |
| `audit_logs` | Authenticated actor, scope, action/object, correlation ID, and redacted change summary | JSONB object; append-only runtime grants; forced RLS; actor/tenant/object/time indexes; reads require MFA permission |
| `roles`, `permissions` | Global RBAC definitions with explicit role scope | unique role code; composite `(id, scope)` key for scoped FKs |
| `user_roles` | Global-only user grants (`CUSTOMER`, support, platform admin) | unique user/role; scope check/FK requires `GLOBAL`; assignment audit fields |
| `role_permissions` | Global permission mapping | unique role/permission |
| `tenants` | Shop tenant; onboarding/approval lifecycle, review actor and reason | status/creation index; review actor FK; actor/time pairing check |
| `tenant_members` | Tenant + user membership; one tenant-scoped role per membership | unique tenant/user; scope check/FK requires `TENANT`; joined-at and status checks; user/status index |
| `stores` | `tenant_id`, one MVP store per tenant | unique tenant; unique normalized public slug; `(tenant_id,id)` composite key; status/update index |
| `store_addresses` | `tenant_id`, store FK | one address per store; store/tenant composite ownership; PostGIS geography point with GiST index; six-digit Indian postal code |
| `store_hours` | `tenant_id`, store FK | unique store/ISO-weekday; same-day open/close check; seven-day schedule validated by service |
| `store_fulfillment_settings` | `tenant_id`, store FK | at least one of pickup/delivery; bounded delivery radius; nonnegative minimum order; IANA timezone |
| `categories` | Platform-managed taxonomy; optional parent | unique slug; parent FK; active/sort index |
| `products` | `tenant_id`, store/category FKs | unique tenant/SKU when SKU supplied; tenant/status/category indexes; checks for price/state |
| `product_images` | `tenant_id`, product FK | tenant/product composite ownership; ordered display index; storage key unique as appropriate |
| `product_variants` | `tenant_id`, product FK | unique tenant/product/variant key; tenant/product index |
| `inventory` | `tenant_id`, one row per stocked variant | unique tenant/variant; nonnegative quantity policy; tenant index |
| `stock_movements` | `tenant_id`, inventory/variant, actor/reference | append-only; tenant/variant/time and reference indexes |
| `customer_addresses` | `user_id` owner | user index; optional one default per user via partial unique index; coordinates if stored |
| `carts` | `user_id`, optional selected shop/tenant | unique active cart per user/shop policy; status/updated index |
| `cart_items` | cart and product variant, owning shop tenant | unique cart/variant; quantity check; validate same shop/tenant at service and constraint design |
| `orders` | Customer `user_id`, owning `tenant_id`, store FK | unique human order number; tenant/status/created, customer/created indexes; immutable shop ownership |
| `order_items` | order, product/variant references and snapshot | FK to order; quantity/price checks; preserve purchased name/price snapshot |
| `order_status_history` | order + tenant + actor | append-only; order/time index |
| `payment_records` | order + tenant; method/status/reference | order/tenant ownership; status index; no card/UPI secret storage |
| `subscription_plans` | Global admin-managed plan | unique code; active/effective-date indexes; amount/currency/interval checks |
| `subscriptions` | `tenant_id`, plan FK | tenant/status index; one current subscription policy to be finalized; gateway ref unique when present |
| `subscription_invoices` | `tenant_id`, subscription FK | unique invoice number; tenant/status/due-date index; immutable amount/tax snapshot |
| `payment_attempts` | invoice/subscription, gateway or manual record | provider reference/idempotency key uniqueness; status/time index |
| `webhook_events` | Provider event and processing state | unique provider/event ID; received/status index; restricted payload retention |
| `notifications` | Recipient user; optional tenant/context | user/read/created index; delivery state |
| `device_tokens` | User/device | unique provider token; user/active index; token protected from logs |
| `reviews` | Customer, tenant/store/product, completed order | unique order/customer/target policy; moderation/status indexes |
| `audit_logs` | actor, optional tenant, action/object | append-only; tenant/time and actor/time indexes; sensitive values redacted |

## Composite ownership and RLS

V4 applies RLS to user identities, global user-role grants, tenants, memberships, and stores. Static `roles`, `permissions`, and `role_permissions` reference data remains outside RLS and is read-only to the runtime group. The `localshop_runtime` database group receives explicit table privileges for identity reads/inserts, self customer-role provisioning, and tenant-scoped CRUD. Production must provision the same non-owner `NOBYPASSRLS` group and a separate migration principal before applying V4. The migration principal may have `BYPASSRLS` for controlled schema/data migrations but must never be used by API processes. The production release pipeline applies migrations with that principal, then starts the API with `DB_MIGRATIONS_ENABLED=false` and runtime-only credentials.

V4 also applies RLS to global identity/grant tables and grants the `localshop_runtime` database group only explicit table permissions: identity reads/inserts, self customer-role provisioning, and tenant-scoped CRUD. Production must provision the same non-owner `NOBYPASSRLS` group and a separate migration principal before applying V4. The production release pipeline applies migrations with the migration principal, then starts the API with `DB_MIGRATIONS_ENABLED=false` and runtime-only credentials.

Role scopes, transaction context keys, customer-order access, public read policy, and the migration/runtime role split are specified in [IDENTITY_TENANCY_DECISIONS.md](IDENTITY_TENANCY_DECISIONS.md). In summary, `user_roles` is global-only; tenant roles belong to `tenant_members`. Tenant policies use transaction-local context and fail closed when context is absent. Customer-owned order/review policies permit only the authenticated customer across shop tenants; public policies are read-only and limited to approved/published catalogue data. Runtime credentials must not own protected tables or have `BYPASSRLS`. Any earlier reference to an admin “bypass” means an explicit admin-scoped policy, never the PostgreSQL `BYPASSRLS` privilege. Shared-role custom GUCs are defense in depth, not a boundary against arbitrary SQL under that role.

For tenant-owned references, use composite unique keys/FKs where practical (for example `(tenant_id, id)` on store/product/variant and matching child FK) so a child cannot refer across tenants. Apply RLS to tenant-owned tables: `USING` and `WITH CHECK` compare row tenant to transaction-local tenant context. Public read paths expose only approved/published rows through a controlled query/service policy. Admin bypass is explicit, permission-gated, and audited. Ensure pooled connections reset context transactionally and test missing context, wrong tenant, forged IDs, job execution, and rollback.

## Indexing and spatial data

Use GiST on store address geography; query radius/order by distance with bounded results and approved/public filters. Add B-tree indexes for tenant, state/status, foreign keys, normalized lookup keys, and common `(tenant_id, status, created_at)` or `(user_id, created_at)` access patterns. Use `EXPLAIN ANALYZE` against realistic pilot-shaped data before tuning. Avoid speculative indexes without query needs.

## Migrations and unresolved details

Create migrations in dependency order: identity/RBAC; tenancy/shop; RLS; OTP challenge and rate-limit storage; auth sessions and refresh tokens; taxonomy/catalog; inventory; customer/cart/order; payment; subscription/webhook; notification/review/audit. Do not generate every table in one initial migration. Identity UUID, role scope, and tenant boundary choices are finalized in [IDENTITY_TENANCY_DECISIONS.md](IDENTITY_TENANCY_DECISIONS.md). OTP terminal rows have a 30-day retention target, rate-limit buckets have a one-day cleanup target, and expired auth session/token rows have a 90-day cleanup target; scheduled cleanup remains pending. Cancellation/restock semantics, cart ownership policy, subscription uniqueness/grace rules, invoice/tax columns, and review target rules remain open for their dependent domain migrations. Use expand/contract changes for deployed schema evolution; never use automatic schema creation in production.
# Catalog implementation note (V12)

The global `categories` taxonomy is seeded with stable UUIDs and active slugs. `products` belongs to a tenant and store, references a category, and stores price and optional compare-at price as integer INR minor units. A partial unique index enforces non-null SKU uniqueness per tenant; a tenant-scoped slug constraint supports shop product URLs. Product status is `DRAFT`, `PUBLISHED`, or `ARCHIVED`; archive timestamps support lifecycle tracking. RLS allows sellers to see and manage only their tenant's rows, while public reads are restricted to published products in published stores belonging to approved tenants. Category reads are global; category administration remains a platform operation.

## V13: Variants and stock

`product_variants` belongs to one product and tenant; variant codes are unique per product and optional SKUs per tenant. Variant price is an optional INR minor-unit override. Each variant has one `inventory` row with a nonnegative on-hand quantity. Each adjustment writes a `stock_movements` record with actor, reason, delta, before/after quantities, and timestamp. The inventory row is locked during adjustment; the balance update and movement insert share a transaction. Movement rows are immutable by database trigger. RLS limits variant, inventory, and movement access to the active tenant seller context. Reservations and decrement on order acceptance are deferred to checkout design in LSOS-027.

## V14: Product image metadata

`product_images` stores product/tenant ownership, generated storage key, signature-verified content type, bounded byte size, SHA-256 digest, scan state, order, and creator. Object data is kept in the configured storage adapter. The default scanner leaves files `PENDING_SCAN`; only `CLEAN` files of published products on published stores of approved tenants are public under RLS. Rejected/unscanned files remain unavailable publicly.

## Marketplace discovery queries (LSOS-025)

Nearby discovery uses the existing GiST index on `store_addresses.location` with `ST_DWithin` over geography and computes `ST_Distance` for ordering. Search joins only published stores with approved tenants and configured fulfillment/address data; product search also filters published products and active categories. Existing tenant/status/category/product and public storefront indexes support the joins. Measure query plans on pilot data before adding text-search extensions or cache layers.

## V15–V16: Customer addresses and carts

`customer_addresses` is user-owned rather than tenant-owned; it supports soft deletion, optional PostGIS location, and at most one active default address per customer. `carts` references one tenant/store and one customer; a partial unique index permits only one `ACTIVE` cart per customer. `cart_items` references variants through a tenant-composite FK and caps quantity at 100. Advisory transaction locks serialize per-customer cart/default-address mutations; RLS enforces the customer identity on every read/write. Prices are read from current product/variant records and recomputed for cart responses; checkout must revalidate prices, publication, address, and stock.
