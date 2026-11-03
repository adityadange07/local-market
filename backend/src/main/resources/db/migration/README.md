# Flyway migrations

Place versioned, forward-only Flyway SQL migrations here. `V1` verifies PostGIS is provisioned; `V2` creates global identity/RBAC tables and seeds immutable MVP role and permission codes; `V3` creates tenants, memberships, and stores; `V4` enables and forces RLS with fail-closed tenant/user context policies; `V5` creates purpose-bound OTP challenge storage with a keyed digest and bounded verification state; `V6` stores HMAC-keyed distributed request-limit counters; `V7` creates refresh-token session families and revocation state; `V8` stores encrypted admin TOTP credentials, hashed recovery codes, and restricted MFA events; `V9` creates append-only, redacted authorization audit events; `V10` adds shop application permissions and onboarding visibility/transition policies; `V11` adds tenant-scoped store addresses, hours, fulfillment settings, and public-read RLS policies. Hibernate validates mappings only. Migration sequencing and RLS expectations are in [DATABASE_DESIGN.md](../../../../../docs/architecture/DATABASE_DESIGN.md) and [IDENTITY_TENANCY_DECISIONS.md](../../../../../../docs/architecture/IDENTITY_TENANCY_DECISIONS.md).
# Version 12 — Catalog categories and products

`V12__catalog_categories_and_products.sql` seeds the global active category taxonomy and creates tenant/store-owned products. Product prices use integer INR minor units; SKUs and slugs are unique within a tenant. Product lifecycle is `DRAFT`, `PUBLISHED`, or `ARCHIVED`. Row-level security permits tenant-scoped seller access and exposes published products only for approved tenants with published stores. The migration grants the runtime role the minimum catalog table privileges.

# Version 13 — Product variants and inventory

`V13__product_variants_and_inventory.sql` adds tenant-owned product variants, nonnegative on-hand inventory, and append-only stock movements. Adjustments lock the inventory row and insert one attributable movement in the same transaction. Checkout reservations and order-driven stock changes are intentionally deferred to LSOS-027.

# Version 14 — Product image metadata

`V14__product_images.sql` records generated object keys, verified image type, size, digest, scan state, ordering, and actor. Forced RLS restricts seller reads/writes to their tenant; public reads require a clean scan result and a published product/store under an approved tenant. Object bytes live behind the `FileStorage` adapter, not in PostgreSQL.
