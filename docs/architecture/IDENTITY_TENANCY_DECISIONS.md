# Identity and Tenancy Decisions

Status: **Finalized for MVP schema implementation**. This decision record closes the identity, role scope, tenant ownership, UUID, and RLS questions needed for LSOS-010–013. It does not create database tables; see [DATABASE_DESIGN.md](DATABASE_DESIGN.md) for the migration model.

## Decisions

### Identity and identifiers

- A `users` row is the global person/account identity. A phone number is normalized to E.164 and unique; verification state is separate from the phone value. OTP challenges and refresh sessions are separate short-lived/revocable records and never store raw OTP or refresh secrets.
- Use PostgreSQL `UUID` primary keys. Runtime entities use application-generated Java `UUID.randomUUID()` (UUIDv4), which avoids adding a UUIDv7 dependency; pilot-scale random-key index cost is acceptable. Immutable Flyway-seeded role/permission reference rows use stable UUID literals so their IDs remain the same across environments. Public order numbers are separate display identifiers and do not expose sequential database IDs.
- Deactivate identities and preserve referenced/audit history. Do not hard-delete an identity as part of normal account closure; implement retention and deletion/anonymization after legal/product policy is approved.

### Roles and grants

Roles have immutable unique codes and an explicit scope (`GLOBAL` or `TENANT`). Permissions have immutable unique codes; `role_permissions` maps permissions to roles. Initial role/permission rows are seeded by Flyway and changed through reviewed migrations, not arbitrary seller/admin CRUD.

| Scope | Role | Intended authority |
|---|---|---|
| Global | `CUSTOMER` | Own profile, addresses, carts, orders, device tokens, and eligible reviews; public marketplace reads |
| Global | `PLATFORM_SUPPORT` | Support lookup and specifically granted low-risk support actions; no plan/payment verification or role administration by default |
| Global | `PLATFORM_ADMIN` | Platform operations, shop decisions, plan management, and payment verification; MFA and audited actions required |
| Tenant | `TENANT_OWNER` | All operations for that shop, team administration, and subscription/invoice management |
| Tenant | `TENANT_MANAGER` | Store, catalogue, inventory, order, fulfilment, and reporting operations; no owner transfer or platform billing authority |
| Tenant | `TENANT_STAFF` | Only assigned store/catalogue/inventory/order tasks; no team, billing, or platform administration |

`user_roles` stores **global-only** assignments (`CUSTOMER`, `PLATFORM_SUPPORT`, `PLATFORM_ADMIN`). Its role-scope check and composite FK must require `GLOBAL`. Tenant roles are stored on `tenant_members.role_id`, whose scope check and composite FK require `TENANT`; there is one role per membership in MVP. A person may have global roles and membership in multiple tenants. Multiple owners per tenant are allowed; the service must prevent removal/demotion of the last active owner in a transaction. Fine-grained per-user permission overrides and multiple roles per membership are deferred.

Admin and support grants are provisioned through a controlled operator process and audited. Never grant platform roles through public OTP registration, seller onboarding, or a client-supplied role value. Require MFA for platform admin sessions; support role access is separately permission-checked.

### Tenant and store ownership

- One `tenants` row represents one shop account in MVP; `stores.tenant_id` is unique, so each tenant owns at most one store.
- A tenant member links one user to one tenant and has its own active/invited/suspended status and a tenant-scoped role. Users can belong to multiple tenant memberships; every seller request resolves the selected active membership server-side.
- Seller onboarding creates the draft tenant, store, and initial active owner membership atomically. Shop approval/publishing is a separate admin action. Public discovery returns only approved and published shops/products.
- Tenant approval status is `ONBOARDING`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `SUSPENDED`, or `CLOSED`; storefront publication is independently `DRAFT`, `PUBLISHED`, or `ARCHIVED`. Public visibility requires both an `APPROVED` tenant and `PUBLISHED` store.
- A tenant/store's identity is not reassigned to another user or tenant. Ownership transfer changes membership role/status and is audited; it cannot leave a tenant without an active owner.

### Tenant context and PostgreSQL RLS

- Tenant context is `app.tenant_id`, set using parameterized `set_config('app.tenant_id', ?, true)` inside the same transaction that accesses tenant rows. `true` makes the setting transaction-local; never use connection-session state. Missing or empty context denies tenant rows.
- Set `app.user_id` transaction-locally for user-owned policies and `app.actor_scope` to a backend-derived value (`customer`, `seller`, `platform_admin`, or a narrow service operation). These values are derived after authentication/authorization; clients cannot set them.
- Tenant policies use `USING` and `WITH CHECK`. Seller writes require the matching tenant context and active membership. Public-read policies on approved/published store/catalogue records are explicit and read-only. Customer order/review reads may match authenticated user ownership across tenants; seller access additionally requires the owning tenant context. Do not create a broad customer policy on arbitrary tenant data.
- Apply RLS to tenant-owned tables and `FORCE ROW LEVEL SECURITY` where supported. The runtime database role must not own protected tables and must not have `BYPASSRLS`. A separately controlled migration identity owns/changes schema; runtime credentials are least-privileged. This separates schema DDL from ordinary request access.
- Platform admin cross-tenant reads/writes require explicit backend permission, MFA, audit event, and an explicit admin-scoped RLS policy. No normal seller/customer role receives this scope. Support access is narrower and read-oriented by default.
- RLS session settings are defense in depth for a shared application database role, not protection against arbitrary SQL execution by compromised application code: a database principal that can issue SQL can set custom session parameters. Parameterized queries, authorization, least privilege, and SQL-injection prevention remain mandatory. Consider stronger tenant database separation only if the threat/compliance model later requires it.

## Schema constraints to implement in LSOS-010–012

- `users.id`, `roles.id`, `permissions.id`, `tenants.id`, and `stores.id` are native PostgreSQL UUIDs. Runtime aggregate identifiers are application-generated UUIDv4; Flyway seed identifiers for roles and permissions are stable UUID literals.
- `users.phone_e164` has a unique constraint; disabled users remain reserved until approved phone-change/retention policy is implemented.
- `roles` has unique `code`, `scope`, and a unique `(id, scope)` key for composite scope FKs. `user_roles` has unique `(user_id, role_id)`, a check requiring `GLOBAL`, and composite FK `(role_id, scope)`; `tenant_members` has unique `(tenant_id, user_id)`, a check requiring `TENANT`, and matching composite FK.
- `role_permissions` has primary key `(role_id, permission_id)`. Tenant-owned children reference `(tenant_id, id)` composite keys on their parent; `stores.tenant_id` is unique.
- Keep platform grants, membership changes, approval decisions, and owner transfer attributable with actor and timestamps plus audit events. Role/permission codes are not soft-deleted; retire codes without reusing them.
- Add indexes for normalized phone, role grant lookup, membership by user/status and tenant/status, and store public slug/status. Add partial/compound indexes only for demonstrated query patterns.

## RLS and authorization test cases

Test absent/empty tenant context, tenant A attempting tenant B read/write, suspended membership, customer reading own orders from multiple shops, customer reading another customer's order, seller reading own tenant order, public approved/published reads and unpublished denial, admin permission/MFA denial, audited authorized admin cross-tenant action, transaction rollback and pool reuse, and runtime-role ownership/BYPASSRLS grants. These are release-blocking tests for the identity/tenant foundation.

## Decisions deferred beyond identity foundation

Privacy/retention and account deletion/anonymization; support access duration and customer notification; detailed permission granularity; multi-store tenants; external identity providers; and stronger physical isolation for tenants are product/compliance or scale decisions and do not block LSOS-010–013.
