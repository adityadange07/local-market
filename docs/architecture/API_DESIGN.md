# API Design

## Conventions

Base path `/api/v1`; JSON request/response DTOs; UUID resource identifiers; ISO-8601 timestamps in UTC; currency represented as ISO currency code plus integer minor units; decimal quantities only if product requirements require weighted goods. Do not expose persistence entities. Return a consistent error body with `code`, `message`, `fieldErrors`, and `traceId`; never leak stack traces or secrets. Use cursor or bounded page pagination for list endpoints, documented stable sort keys, and explicit filter allowlists. Idempotency keys are required for checkout and payment-affecting mutations where retry could duplicate effects.

OpenAPI is generated from controller contracts and security annotations, reviewed as a compatibility artifact. Breaking changes require a new API version or a documented migration path.

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

- `OtpRequest(phone, purpose)` and `OtpVerify(phone, purpose, code, deviceInfo)`; response contains access token, refresh token material/cookie strategy, expiry, user summary, and required next action. Do not return OTP or internal provider detail.
- `ShopSummary(id, slug, name, distance, fulfillmentOptions, categorySummary)`; `Storefront(store, address/publicLocation, hours, cataloguePage)`.
- `CartView(shop, items[{variantId, quantity, priceSnapshot, availability}], totals)`; server recalculates all prices/stock at checkout.
- `CheckoutRequest(addressId, fulfillmentMode, paymentMethod, idempotencyKey, customerNote)`; response `OrderReceipt(orderId, displayNumber, status, paymentInstruction, createdAt)`. Seller UPI details are returned only for the selected shop and are informational; no payment secret is collected.
- `OrderStatusUpdate(status, sellerNote)`; server validates allowed transition and records actor/history.
- `ManualSubscriptionPayment(amountMinor, currency, paidAt, reference, evidenceMetadata)`; pending until authorized admin verification.
- `Page<T>(items, nextCursor)` and common `ApiError` envelope.

## Validation, errors, and ownership

Validate phone normalization, identifiers, lengths, enum values, image metadata, quantities, price ranges, pagination bounds, allowed status transitions, and location radius. Checkout verifies address ownership, shop publication/approval, item ownership, current price, fulfilment support, and stock in one transaction. Seller endpoints derive tenant from session/membership and verify each resource belongs to it. Customer endpoints verify ownership of addresses, carts, orders, reviews, and device tokens. Public endpoints return only approved, published data. Admin endpoints require explicit permission; sensitive changes require reason and audit event.

Typical status mapping: 400 invalid input; 401 missing/invalid authentication; 403 role/tenant denial; 404 absent or intentionally concealed resource; 409 stale stock/state/idempotency conflict; 422 valid shape but business rule failure; 429 rate limit; 5xx generic traceable failure. Avoid leaking whether another tenant owns an object.

## Pagination, filtering, and consistency

Bound page size (default 20, maximum 100 unless a route has a smaller cap); define deterministic ordering with a unique tie-breaker. Search filters are allowlisted; do not accept arbitrary SQL fields. Nearby queries require coordinates and bounded radius and return distance. Order lists filter by status and date within ownership scope. Return current order state plus history page. Use optimistic versioning or conditional updates for seller status changes where concurrent actors are possible.

## Webhooks

`POST /api/v1/subscriptions/webhooks/razorpay` reads raw bytes, verifies signature before accepting, persists provider event ID/payload metadata, deduplicates, responds promptly, and processes asynchronously/retryably. Store only necessary payload data with a retention policy. Browser redirects are not authoritative payment confirmation.
