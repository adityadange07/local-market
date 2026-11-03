# Security Architecture

## Identity and sessions

Use SMS OTP through a provider interface. Store only a keyed hash of OTP material (never plaintext), bind it to normalized phone, purpose, expiry, and attempt state; compare safely; enforce single use. Use configurable short expiry, resend cooldown, per-phone/IP/device throttles, abuse monitoring, and generic responses to reduce account enumeration. Provider credentials are secret-managed. OTP vendor and numeric limits are operational decisions to confirm with provider and risk review.

LSOS-014 adds an SMS provider port and a PostgreSQL challenge record with a keyed HMAC-SHA-256 digest, purpose, expiry, bounded attempts, and delivery/consumption state. The HMAC key is supplied as Base64 `OTP_HMAC_SECRET` (at least 32 random bytes); it is required at application startup and must come from a secret manager in deployed environments. Terminal challenge rows are retained for at most 30 days before scheduled purge; the purge job is part of the scheduled-jobs work. OTPs are generated uniformly from a six-digit code space, and a challenge is consumed atomically after constant-time digest comparison. The provider implementation is not selected yet.

Issue short-lived signed access JWTs with issuer, audience, subject, expiry, token ID, and minimal claims. Store refresh token hashes server-side; rotate on every refresh, revoke token families on reuse, support logout/device revocation, and avoid long-lived browser storage. Select secure, HttpOnly, SameSite cookies for web refresh tokens where deployment topology supports it; mobile refresh material uses OS secure storage. Admin login requires MFA (TOTP or equivalent selected during implementation) and reauthentication for high-risk actions.

LSOS-016 implements HS256 access JWTs (10-minute default) with only `iss`, `aud`, `sub`, `iat`, `exp`, and `jti`; JWT signing material is a separate required Base64 `JWT_SIGNING_SECRET` of at least 32 random bytes. The resource server validates signature, issuer, audience, and timestamps. Refresh secrets are 256-bit random opaque values; only SHA-256 digests are stored. A session has a fixed 30-day default expiry. Rotation and replay detection lock the presented token row; replay revokes the complete session family in the same transaction. Logout is idempotent. Signing-key rotation invalidates outstanding access JWTs, while refresh sessions remain independent.

LSOS-018 uses TOTP (30-second step, six digits, SHA-1) for platform administrators. The 160-bit seed is encrypted with AES-256-GCM using the separately managed Base64 `MFA_ENCRYPTION_KEY`; pending enrollment expires after ten minutes. Successful enrollment returns ten 96-bit random recovery codes once; only SHA-256 hashes are stored and each code can be consumed once. MFA verification issues a five-minute step-up access token with `amr` containing `totp`; admin business operations must use `hasAdminPermission`, which checks both this marker and the current database permission grant. TOTP steps cannot be replayed. MFA lifecycle and recovery events are written to a restricted event table. Losing the encryption key requires controlled MFA reset and reenrollment.

## Authorization and tenant boundaries

V4 applies RLS to users, global user-role grants, tenants, memberships, and stores. Static role/permission reference tables are read-only to the runtime group and are not RLS-protected. The local app login is a member of `localshop_runtime`, which is neither a table owner nor `BYPASSRLS`; it receives only the grants needed by the API. In production, run Flyway as a separate release migration step with a restricted migration principal (which may have `BYPASSRLS` for controlled migration work), then disable app-startup migrations and use runtime-only credentials.

V4 applies `ENABLE` and `FORCE ROW LEVEL SECURITY` to the initial identity/grant, tenant, membership, and store tables. The local runtime login is a member of `localshop_runtime`, which is neither a table owner nor `BYPASSRLS`; it receives only the grants needed by the API. In production, run Flyway as a separate release migration step with a migration principal, then disable app-startup migrations and use runtime-only credentials.

The MVP identity and authorization model is finalized in [IDENTITY_TENANCY_DECISIONS.md](IDENTITY_TENANCY_DECISIONS.md): global roles use `user_roles`; each tenant membership has one tenant-scoped role. The database context is derived by the backend and set transaction-locally. RLS protects tenant-owned reads/writes, while customer-owned order/review policies and public approved-store reads are narrowly defined. RLS context variables do not protect against arbitrary SQL executed by the shared runtime role, so parameterized queries and least privilege remain required.

LSOS-017 enables Spring method security and adds `PermissionAuthorization`, which resolves permission codes from the current `user_roles` or active `tenant_members` grant joined to `role_permissions`. It validates that the JWT subject is a UUID, the user is active, and (for tenant checks) membership is active. Role and permission claims are not accepted from the token or request. Use `@PreAuthorize` on both route and service entry points; new business operations must remain unavailable until an explicit permission is declared. The evaluator installs transaction-local RLS context before resolving grants and fails closed for missing/malformed identities or unknown permissions. Every tenant resource access also checks object ownership; never trust a path/body `tenant_id` by itself. Admin cross-tenant access remains explicit, MFA-gated, audited, and never granted to normal seller/customer roles.

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

LSOS-019 adds `audit_logs` as a runtime insert/select-only table with forced RLS. Audit writes must join the business mutation transaction and derive actor/scope from the authenticated and installed backend contexts. `AuditRedactor` recursively removes secrets, credentials, and common PII, truncates long values, and replaces oversized summaries. Audit reads are service-guarded by `platform.audit.read` and require an MFA step-up token. Sensitive actions without an implementation surface (shop approval, payment verification, member changes, stock/order overrides) are instrumented when those domain tickets are built; raw requests and credentials are never audit summaries.

Shop application submission installs the backend-generated tenant ID and `tenant_onboarding` scope for the initial tenant/store/owner inserts and the `ONBOARDING` to `PENDING_APPROVAL` transition. Sellers can read only tenants/stores where their membership grants visibility. Admin approval and rejection use `platform.shops.review`, require MFA, serialize on the tenant row, allow only `PENDING_APPROVAL` transitions, and record the decision in the same transaction. Approval does not publish the store.

Store profile writes first resolve the authenticated user's active tenant membership and `tenant.store.manage` permission for the supplied tenant selector; the selector alone never grants access. Store address, hours, and fulfillment tables use forced RLS and tenant/store composite foreign keys. Public reads require both an approved tenant and published store. Store publication is a transaction that verifies an address, all seven weekday entries, at least one open day, and a valid fulfillment method; the customer storefront returns no rows before that state.

Product image uploads are limited to 5 MiB and JPEG/PNG/WebP magic signatures; file names and client-declared MIME types do not select storage keys or response types. UUID keys are tenant-prefixed and storage paths are normalized under a configured root. The default scanner marks uploads pending, and forced RLS/public download rules expose only clean images for published products and stores of approved tenants. Production must configure object storage and a malware scanner; pending media stays private.

## Security acceptance tests

Test role denial, tenant A to tenant B object access, forged tenant IDs, missing/stale tenant context, pooled-connection context leakage, disabled memberships, refresh replay, OTP expiry/replay/throttle, admin MFA gate, upload spoof/oversize, CORS/CSRF configuration, webhook invalid signature/duplicate/replay, sensitive log redaction, and audited manual payment verification. External penetration testing and compliance/legal review remain pre-launch activities.
