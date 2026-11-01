# Deployment Plan

## Environments

- **Local:** Docker Compose for PostgreSQL 16/PostGIS, Redis, backend, and optional local file adapter. Synthetic fixtures only.
- **CI:** ephemeral test databases/Testcontainers, isolated test secrets, dependency and container checks.
- **Staging:** production-like Ubuntu/container topology with separate domain/secrets/data and Razorpay test mode; run migrations, smoke, E2E, webhook, restore and rollback rehearsal.
- **Production:** Ubuntu 24.04 VPS for the initial pilot, Nginx TLS reverse proxy, containers, restricted database network, secret management, off-host encrypted backups, Prometheus/Grafana and structured logs. Decide whether PostgreSQL runs on the VPS or a managed database based on budget/operations before provisioning; prefer managed DB if viable.

## Release sequence

1. Build immutable versioned images in CI; scan and record commit/tag.
2. Back up production database and confirm recent successful restore evidence.
3. Deploy backward-compatible expand migration, then application image; run health/readiness and smoke checks.
4. Verify authentication, tenant boundary, public storefront, checkout, seller order, admin and subscription webhook paths with controlled accounts.
5. Observe error rate, latency, DB capacity, webhook backlog, and logs; roll back application image if checks fail. Database rollback uses forward repair/restore plan, not assumed destructive reverse migration.
6. Announce completion and record release, migration, owner, and evidence.

## Network and host controls

Expose only required HTTPS (and tightly controlled SSH/admin access); firewall database/Redis from public ingress. Nginx terminates TLS, sets forwarding/security headers, limits request sizes, and routes API/web assets. Configure CORS to production origins. Run containers as non-root with minimal permissions and resource limits. Rotate keys, patch Ubuntu/container base images, and restrict operator access.

## Data recovery and continuity

Automate encrypted off-host backups, retention, and backup-failure alerting. Define RPO/RTO with the business before pilot; periodically restore into isolated staging and verify row counts, migrations, media references, and application startup. Document point-in-time recovery options based on chosen database hosting. Back up object storage separately. A backup that has not been restored is not accepted as proven.

## Observability and operations

Expose health/readiness endpoints and Prometheus metrics for availability, latency, errors, JVM, database pool, job queue, OTP limits, checkout outcomes, webhook processing, notification failures, and subscription reconciliation. Grafana dashboards cover application health and business operations. Centralize structured logs with correlation IDs and retention controls; redact credentials and sensitive PII. Alert owners and response actions are defined in the runbook.

## Rollback, migrations, and launch

Use expand/contract schema changes and forward-compatible application releases. Avoid destructive migrations in the same release that removes old app assumptions. Feature flags can gate risky workflows. Keep the prior image and rollback steps available. Reconcile payment/invoice data after incidents; do not manually edit production rows without an audited procedure. Pilot launch is gated by security, backup restore, webhook test, seller onboarding, support contact, and rollback rehearsal.
