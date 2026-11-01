# Product Requirements — Local Shop OS

## Purpose

Local Shop OS is a subscription SaaS marketplace that gives independent shops in India a digital storefront, catalogue, and order management workflow. The initial pilot targets 10–20 shops in Nagpur, Maharashtra. The platform earns subscription revenue and charges no commission on customer orders.

## Users and roles

- **Customer:** discovers shops/products, maintains addresses, places a shop-specific order, pays the shop by seller-managed UPI or COD, tracks status, and reviews completed purchases.
- **Seller/member:** one or more users belong to a shop tenant; members manage the shop and fulfil its orders according to assigned permissions.
- **Platform admin/support:** approves shops, manages customers, categories, plans, manual subscription payments, support, and platform settings. Admin actions are audited; admin accounts require MFA.
- **Platform operator:** maintains infrastructure, credentials, scheduled work, and operational monitoring. This is an operational responsibility, not a marketplace role.

## Product principles

1. One order belongs to exactly one shop. A cart cannot silently combine shops.
2. Customer order funds go directly to the seller; the platform does not custody or settle them.
3. Sellers decide whether they provide delivery, pickup, or both, and manage fulfilment themselves.
4. A shop has a stable, shareable public storefront URL and is also discoverable in a shared marketplace.
5. A tenant is one shop in MVP. Tenant ownership is enforced in application services and reinforced by PostgreSQL RLS.
6. Build a modular monolith first; do not introduce microservices without evidence that independent scaling or ownership requires them.
7. Prioritize a complete pilot journey over broad, shallow feature coverage.

## MVP capabilities

### Customer

- SMS OTP registration/login; profile and multiple saved addresses.
- Nearby shop discovery and shop/product search; public shop storefront and product detail.
- One-shop cart, checkout, UPI Direct seller handoff or COD, order history/status, notifications, and ratings/reviews.

### Seller

- OTP login, shop application, admin approval, store profile/address/hours, and team membership foundation.
- Category/product/variant/image management, price and offer management, inventory and stock movement history.
- Order acceptance/status management and seller-defined delivery/pickup options.
- Subscription view, invoices, payment history, and basic operational reports.

### Admin

- MFA-protected authentication, shop/seller approval, customer lookup, category and plan management.
- Razorpay subscription payment visibility and webhook state; manual payment recording/verification.
- Invoice and support workflows, basic analytics, configuration, and audit log.

## Non-functional requirements

- Correct tenant isolation is a release blocker. Authorization is checked on every object access; client-supplied tenant identifiers are never trusted as authority.
- Use TLS in production; keep secrets out of source control and logs. Minimize stored personal/payment information.
- APIs are versioned under `/api/v1`, validate input, return stable error envelopes, and publish OpenAPI.
- Checkout must preserve inventory consistency under concurrent purchases and make retries safe.
- Webhooks and payment state transitions must be idempotent and auditable.
- Backups, restore procedures, health checks, logs, metrics, alerting, and rollback instructions are required before pilot.
- Web and mobile flows provide loading, empty, validation, failure, and retry states; web layouts are responsive and customer storefronts are shareable.

## Success measures

For the pilot, measure approved/active shops, catalogue completeness, customer discovery-to-order conversion, successful order creation, seller response/fulfilment time, repeat orders, subscription payment success, support volume, and critical tenant/security incidents. Establish numeric targets with the pilot operator before launch; no baseline is available yet.

## Open product decisions

Resolve before the relevant implementation tickets: subscription grace period/expiry behavior and renewals; plan prices/benefits and annual discount; GST and statutory invoice treatment with an accountant; order cancellation, returns, and UPI payment proof/confirmation rules; review eligibility/moderation; customer/shop data retention and deletion; address/location consent wording; support response commitments; and the SMS vendor after DLT, pricing, and deliverability checks. Do not encode legal/tax assumptions in code before review.
