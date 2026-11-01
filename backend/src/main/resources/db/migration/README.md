# Flyway migrations

Place versioned, forward-only Flyway SQL migrations here. `V1` verifies PostGIS is provisioned; `V2` creates global identity/RBAC tables and seeds immutable MVP role and permission codes; `V3` creates tenants, memberships, and stores; `V4` enables and forces RLS with fail-closed tenant/user context policies. Hibernate validates mappings only. Migration sequencing and RLS expectations are in [DATABASE_DESIGN.md](../../../../../docs/architecture/DATABASE_DESIGN.md) and [IDENTITY_TENANCY_DECISIONS.md](../../../../../../docs/architecture/IDENTITY_TENANCY_DECISIONS.md).
