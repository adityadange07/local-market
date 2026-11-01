# Production Readiness

## Release checklist

### Product and operations

- [ ] Pilot plan prices, entitlements, renewal/grace rules, cancellation/refund and support procedures approved.
- [ ] Shop approval checklist, seller onboarding guide, UPI handoff instructions, and delivery/pickup responsibilities published.
- [ ] GST/invoice and privacy/retention decisions reviewed by qualified advisers; customer/seller terms and consent reviewed.
- [ ] Support contact, escalation owner, incident severity definitions, and operating hours set.

### Security and data

- [ ] Admin MFA enforced; least privilege and role matrix reviewed.
- [ ] Tenant IDOR/RLS, pooled-connection, refresh replay, OTP abuse, upload, and webhook tests pass.
- [ ] Secrets external to repository/images; rotation owners and process documented.
- [ ] TLS/CORS/security headers, firewall, database/Redis network restrictions verified.
- [ ] Logs/traces redact OTP, tokens, payment secrets, and unnecessary PII; retention configured.
- [ ] Data retention/deletion and access process agreed; backup encryption and access restricted.

### Reliability and release

- [ ] Flyway migrations tested from empty database and staging upgrade path; production Hibernate DDL generation disabled.
- [ ] Monitoring, dashboards, alerts, health checks, and on-call/response owner tested.
- [ ] Encrypted off-host database and media backups succeed; restore drill meets agreed RPO/RTO.
- [ ] Container/image scans and dependency checks have no unresolved critical/high findings.
- [ ] Load test and query plans meet agreed pilot SLOs; concurrency checkout test passes.
- [ ] Razorpay test/live configuration reviewed; signature, duplicate event, retry, and reconciliation paths verified.
- [ ] Deployment, smoke test, rollback, and payment reconciliation runbooks rehearsed.
- [ ] Android release signing/distribution, privacy disclosures, and support contact ready.

## Go/no-go

No-go for any unresolved critical/high security or data-loss issue, tenant leakage, unverified subscription activation, untested restore, missing admin MFA, failed checkout stock-concurrency test, or unavailable incident owner. Record evidence and accountable sign-off for each gate. Business/legal decisions are explicit launch dependencies, not technical defaults.
