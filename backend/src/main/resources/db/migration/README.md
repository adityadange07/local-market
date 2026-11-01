# Flyway migrations

Place versioned, forward-only Flyway SQL migrations here. `V1` is a minimal baseline that verifies the database administrator provisioned PostGIS; it does not create application tables or extensions. Finalize the domain model and table ownership before adding schema. Hibernate is configured for validation only, never auto-create/update. Migration sequencing and RLS expectations are in [DATABASE_DESIGN.md](../../../../../docs/architecture/DATABASE_DESIGN.md).
