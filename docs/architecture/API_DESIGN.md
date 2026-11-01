# API Design

## Conventions

Base path `/api/v1`; JSON request/response DTOs; UUID resource identifiers; ISO-8601 timestamps in UTC; currency represented as ISO currency code plus integer minor units; decimal quantities only if product requirements require weighted goods. Do not expose persistence entities. Return a consistent error body with `code`, `message`, `fieldErrors`, and `traceId`; return the same server-generated trace identifier in `X-Trace-Id`. Never leak stack traces or secrets. Map validation failures to field-level errors, malformed input to a generic client error, and unexpected failures to a generic 500. Use cursor or bounded page pagination for list endpoints, documented stable sort keys, and explicit filter allowlists. Idempotency keys are required for checkout and payment-affecting mutations where retry could duplicate effects.

OpenAPI is generated from controller contracts and security annotations, reviewed as a compatibility artifact. The backend groups version-one endpoints matching `/api/v1/**`; in local development, JSON is served at `/v3/api-docs/v1` and Swagger UI at `/swagger-ui`. Documentation endpoints are disabled by default outside the local profile. Apply bearer security per operation as controllers are implemented rather than globally, because authentication and public marketplace routes differ. Breaking changes require a new API version or a documented migration path.

## Route groups and representative contracts

| Group | Representative endpoints | Auth/authorization |
|---|---|---|
| `/auth` | `POST /otp/request`, `POST /otp/verify`, `POST /tokens/refresh`, `POST /tokens/revoke`, `POST /admin/mfa/verify` | OTP throttled; refresh token rotation; admin MFA gate |
| `/customer` | `GET/PATCH /profile`, CRUD `/addresses`, `GET/PUT /cart`, `POST /checkout`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/reviews`, device token registration | customer identity; order/address owner; checkout one-shop invariant |
| `/marketplace` | `GET /shops/nearby`, `GET /shops`, `GET /shops/{slug}`, `GET /shops/{slug}/products`, `GET /products/search`, `GET /products/{id}` | public approved/published data; bounded geo/search queries |
| `/seller` | `POST /applications`, `GET/PATCH /store`, `/store/hours`, product/category assignment CRUD, image upload, inventory adjustment, `GET /orders`, `PATCH /orders/{id}/status`, reports, team membership | active tenant membership and permission; object tenant ownership |
| `/admin` | dashboard, shop review/approve/reject, customer search, category/plan CRUD, manual payment verify, invoice/support/analytics/audit/config | admin role plus MFA; every sensitive write audited |
| `/subscriptions` | `GET /plans`, `GET /current`, `POST /`, change/cancel, `GET /invoices`, `POST /invoices/{id}/manual-payments`, `POST /webhooks/razorpay` | seller subscription scoped to tenant; admin verify; gateway signature on webhook |

## Representative DTOs

- OTP request accepts `phone`, `purpose`, and a stable client `deviceId`; normalize Indian local numbers to E.164 and return HTTP 202 with an opaque `challengeId` and account-neutral message. Verify accepts `challengeId`, `phone`, `purpose`, six-digit `code`, and `deviceId`; invalid, expired, replayed, locked, and unknown challenges share one generic 401 response. Successful verification resolves/creates the identity according to purpose and returns a 10-minute JWT, opaque refresh token, and next action. `POST /api/v1/auth/tokens/refresh` accepts the current refresh token and rotates it; `POST /api/v1/auth/tokens/revoke` revokes its complete session family and returns 204. Replayed refresh tokens revoke that family. Never return OTP material or internal provider detail.
- JWTs use HS256 with required `iss`, `aud`, `sub`, `iat`, `exp`, and `jti` claims only. The issuer and audience are validated by the resource server; role and tenant claims are deliberately omitted and will be resolved from current database membership/permissions.
- `ShopSummary(id, slug, name, distance, fulfillmentOptions, categorySummary)`; `Storefront(store, address/publicLocation, hours, cataloguePage)`.
- `CartView(shop, items[{variantId, quantity, priceSnapshot, availability}], totals)`; server recalculates all prices/stock at checkout.
- `CheckoutRequest(addressId, fulfillmentMode, paymentMethod, idempotencyKey, customerNote)`; response `OrderReceipt(orderId, displayNumber, status, paymentInstruction, createdAt)`. Seller UPI details are returned only for the selected shop and are informational; no payment secret is collected.
- `OrderStatusUpdate(status, sellerNote)`; server validates allowed transition and records actor/history.
- `ManualSubscriptionPayment(amountMinor, currency, paidAt, reference, evidenceMetadata)`; pending until authorized admin verification.
- `Page<T>(items, nextCursor)` and common `ApiError` envelope.

## Validation, errors, and ownership

Validate phone normalization, identifiers, lengths, enum values, image metadata, quantities, price ranges, pagination bounds, allowed status transitions, and location radius. Checkout verifies address ownership, shop publication/approval, item ownership, current price, fulfilment support, and stock in one transaction. Seller endpoints derive tenant from session/membership and verify each resource belongs to it. Customer endpoints verify ownership of addresses, carts, orders, reviews, and device tokens. Public endpoints return only approved, published data. Admin endpoints require explicit permission; sensitive changes require reason and audit event.

OTP request limits are stored centrally with HMAC-keyed phone/IP/device subjects: one request per phone per minute, five per phone per 15 minutes, 30 per IP per 15 minutes, and 10 per device per 15 minutes. Verification has corresponding per-phone/IP/device 15-minute limits, in addition to the challenge attempt limit. All applicable bucket increments are transactional; a denied request rolls back partial bucket consumption. Resolve `X-Real-IP` only when the socket peer is in `OTP_TRUSTED_PROXY_ADDRESSES`; reverse proxies must overwrite that header. The API should not be directly reachable around the trusted proxy in production.

Refresh tokens contain a non-secret session identifier and 256-bit random secret; only SHA-256 hashes are stored. Every refresh locks the presented token/session, consumes it, and stores a replacement atomically. Reuse of a consumed token revokes all tokens in that session family. Access JWTs are stateless and may remain usable until their short expiry after logout or session revocation.

Typical status mapping: 400 invalid input; 401 missing/invalid authentication; 403 role/tenant denial; 404 absent or intentionally concealed resource; 409 stale stock/state/idempotency conflict; 422 valid shape but business rule failure; 429 rate limit; 5xx generic traceable failure. Avoid leaking whether another tenant owns an object.

## Pagination, filtering, and consistency

Bound page size (default 20, maximum 100 unless a route has a smaller cap); define deterministic ordering with a unique tie-breaker. Search filters are allowlisted; do not accept arbitrary SQL fields. Nearby queries require coordinates and bounded radius and return distance. Order lists filter by status and date within ownership scope. Return current order state plus history page. Use optimistic versioning or conditional updates for seller status changes where concurrent actors are possible.

## Webhooks

`POST /api/v1/subscriptions/webhooks/razorpay` reads raw bytes, verifies signature before accepting, persists provider event ID/payload metadata, deduplicates, responds promptly, and processes asynchronously/retryably. Store only necessary payload data with a retention policy. Browser redirects are not authoritative payment confirmation.
