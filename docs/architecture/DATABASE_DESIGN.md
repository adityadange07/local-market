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
| `roles`, `permissions` | Global RBAC definitions | unique role/permission key |
| `user_roles` | Global or explicitly scoped role grant | unique user/role/scope tuple; FK constraints |
| `role_permissions` | Global permission mapping | unique role/permission |
| `tenants` | Shop tenant; lifecycle/status | unique internal slug/key; status index |
| `tenant_members` | Tenant + user membership | unique tenant/user; tenant and user indexes; role/status |
| `stores` | `tenant_id`, one MVP store per tenant | unique tenant; unique normalized public slug; published/status indexes |
| `store_addresses` | `tenant_id`, store FK | store/tenant composite ownership; PostGIS geography index; address purpose |
| `store_hours` | `tenant_id`, store FK | unique store/day/time-window as domain allows; tenant/store index |
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

For tenant-owned references, use composite unique keys/FKs where practical (for example `(tenant_id, id)` on store/product/variant and matching child FK) so a child cannot refer across tenants. Apply RLS to tenant-owned tables: `USING` and `WITH CHECK` compare row tenant to transaction-local tenant context. Public read paths expose only approved/published rows through a controlled query/service policy. Admin bypass is explicit, permission-gated, and audited. Ensure pooled connections reset context transactionally and test missing context, wrong tenant, forged IDs, job execution, and rollback.

## Indexing and spatial data

Use GiST on store address geography; query radius/order by distance with bounded results and approved/public filters. Add B-tree indexes for tenant, state/status, foreign keys, normalized lookup keys, and common `(tenant_id, status, created_at)` or `(user_id, created_at)` access patterns. Use `EXPLAIN ANALYZE` against realistic pilot-shaped data before tuning. Avoid speculative indexes without query needs.

## Migrations and unresolved details

Create migrations in dependency order: identity/RBAC; tenancy/shop; taxonomy/catalog; inventory; customer/cart/order; payment; subscription/webhook; notification/review/audit. Do not generate every table in one initial migration. Finalize cancellation/restock semantics, cart ownership policy, subscription uniqueness/grace rules, invoice/tax columns, retention, review target rules, and RLS role design in domain review before corresponding migrations. Use expand/contract changes for deployed schema evolution; never use automatic schema creation in production.
