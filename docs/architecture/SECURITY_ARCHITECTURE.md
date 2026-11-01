# Security Architecture

## Identity and sessions

Use SMS OTP through a provider interface. Store only a keyed hash of OTP material (never plaintext), bind it to normalized phone, purpose, expiry, and attempt state; compare safely; enforce single use. Use configurable short expiry, resend cooldown, per-phone/IP/device throttles, abuse monitoring, and generic responses to reduce account enumeration. Provider credentials are secret-managed. OTP vendor and numeric limits are operational decisions to confirm with provider and risk review.

Issue short-lived signed access JWTs with issuer, audience, subject, expiry, token ID, and minimal claims. Store refresh token hashes server-side; rotate on every refresh, revoke token families on reuse, support logout/device revocation, and avoid long-lived browser storage. Select secure, HttpOnly, SameSite cookies for web refresh tokens where deployment topology supports it; mobile refresh material uses OS secure storage. Admin login requires MFA (TOTP or equivalent selected during implementation) and reauthentication for high-risk actions.

## Authorization and tenant boundaries

Use deny-by-default role/permission checks at route and service layers. Membership is resolved server-side and checked for active status. Every tenant resource access checks tenant ownership; never trust a path/body `tenant_id`. Apply PostgreSQL RLS to tenant data using transaction-local context and `USING`/`WITH CHECK`. Connection pool cleanup, asynchronous jobs, migrations, and admin paths require dedicated tests. Admin cross-tenant access is explicit, narrow, logged, and never granted to normal seller/customer roles.

## Data protection

- TLS between clients/Nginx and API; secure database connections when remote.
- Encrypt disks/backups and use managed secret storage or tightly permissioned host secrets.
- Minimize PII, redact phone, tokens, UPI details, webhook secrets, and OTP from logs/traces.
- Do not store payment card data or UPI PINs. Subscription gateway references are not credentials.
- Define retention, deletion, access/export, backup expiry, and incident response with legal/business review before launch.
- Keep public product media separate from private evidence/document storage; use signed access for private objects.

## API and application defenses

Validate and normalize all input; use parameterized persistence queries; bound search, pagination, upload size, and request body. Configure CORS to the required production origins, CSRF protection when cookie-authenticated mutations are used, secure headers, content type checks, and rate limits. Prevent IDOR via ownership checks and non-disclosing errors. Avoid mass assignment by mapping explicit DTO fields. Use dependency scanning and secret scanning in CI; pin and review dependencies.

## OTP, uploads, and webhooks

OTP endpoints are rate-limited and monitored; lockout behavior avoids easy denial-of-service and is tuned with abuse data. Uploads enforce allowlisted MIME/signature, dimensions/size, generated object keys, authorization, and malware scanning where available; never trust filename/extension. Razorpay webhook signatures are verified over raw body before event acceptance; deduplicate provider event ID, store processing state, make transitions idempotent, and reconcile failed processing. Never activate subscriptions from a client redirect.

## Audit, monitoring, and response

Append audit records for approval, plan/payment changes, role/member changes, stock adjustments, order state overrides, and platform configuration. Include actor, tenant, action, object, timestamp, correlation ID, and redacted before/after data as policy permits. Restrict audit read access. Alert on OTP abuse, repeated auth failures, RLS denials, webhook failures/lag, payment reconciliation mismatch, backup failure, and availability/resource thresholds. Document incident triage, credential rotation, evidence preservation, and notification responsibilities.

## Security acceptance tests

Test role denial, tenant A to tenant B object access, forged tenant IDs, missing/stale tenant context, pooled-connection context leakage, disabled memberships, refresh replay, OTP expiry/replay/throttle, admin MFA gate, upload spoof/oversize, CORS/CSRF configuration, webhook invalid signature/duplicate/replay, sensitive log redaction, and audited manual payment verification. External penetration testing and compliance/legal review remain pre-launch activities.
