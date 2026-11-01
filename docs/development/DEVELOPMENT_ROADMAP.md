# Development Roadmap

## Delivery strategy

Ship a thin, end-to-end pilot slice early: approved shop → published catalogue → customer discovery → single-shop checkout → seller-managed order status. Add subscription collection and required operational controls before onboarding pilot shops. Build seller/admin web and customer Android against stable API contracts; do not block public storefront on the Android release. Protect the plan from scope growth by gating each capability against the pilot acceptance criteria.

## Dependency order

Product/domain decisions → repository/environment → backend/migrations/API conventions → identity and tenant boundary → shop onboarding → catalogue/inventory → marketplace → cart/checkout/orders → subscription billing and notifications → seller/admin web and customer Android integration → E2E/security/performance → production readiness → Nagpur pilot.

Seller and admin web foundations can progress in parallel after auth/API contracts. Customer mobile foundation can progress alongside marketplace backend after API DTOs stabilize. Shared database/domain changes, authentication, and checkout remain sequential and require integration ownership.

## Sprint roadmap

Assume one primary developer, two-week sprints, approximately 60–70 focused build hours per sprint after review/integration overhead. This is a planning baseline; recalculate after Sprint 1.

| Sprint | Primary outcome | Exit evidence |
|---|---|---|
| 1 | Product/domain decisions and repo/CI foundation | Approved domain/API/security docs; clean local setup and CI |
| 2 | Backend skeleton, PostgreSQL/PostGIS, Flyway and API foundation | Clean database migrates; API contract is published |
| 3 | Identity/RBAC schema, tenancy model, RLS test foundation | Tenant model reviewed; adversarial test fixtures run |
| 4 | OTP, JWT refresh rotation, authorization and audit foundation | Auth integration and cross-tenant denial tests pass |
| 5 | Seller application, store profile, admin approval | Applicant-to-approved-shop flow is auditable |
| 6 | Categories, products and variants | Seller can manage a publishable catalogue |
| 7 | Inventory, image flow and concurrency behavior | Stock changes are auditable; upload rules enforced |
| 8 | Marketplace APIs and location/search behavior | Approved shop/product discoverable in Nagpur test data |
| 9 | Responsive public storefront and marketplace web | Shareable shop URL supports browse-to-product detail |
| 10 | Customer address/cart APIs and Android foundation | Customer cart remains scoped to one shop |
| 11 | Checkout, UPI handoff/COD, order and status management | Complete order flow is retry safe and stock protected |
| 12 | Subscription plans, invoices, Razorpay/manual verification | Payment events are idempotent; manual activation gated |
| 13 | Notification foundation and seller dashboard core | Seller sees and updates incoming orders |
| 14 | Seller dashboard completion and reports | Seller operational workflows are usable and tenant-scoped |
| 15 | Admin dashboard, plans/payment/support workflows | Admin operations are permission-gated and auditable |
| 16 | Android customer discovery, storefront and cart | Android browse/cart slice integrated against API |
| 17 | Android checkout, order/profile and client polish | Android customer journey works on representative devices |
| 18 | Cross-client integration and E2E | Pilot journeys pass across Android, web, seller, admin |
| 19 | Security/load, backup/restore and deployment rehearsal | Release gates demonstrated in staging |
| 20 | Production launch preparation and Nagpur pilot onboarding | Initial cohort can be supported; runbook/feedback loop active |

The roadmap totals roughly 1,248 developer hours, or 31 full-time weeks at 40 hours/week. At 60–70 focused hours per two-week sprint, plan about 19–21 sprints before contingency; this roadmap uses 20. Add a 20–25% contingency (roughly 4–5 additional sprints at this capacity) and reforecast from observed velocity.

## Milestones

1. **Architecture baseline:** product decisions, ER model, API contracts, threat model, repo and CI.
2. **Backend vertical slice:** identity, tenant isolation, approved store, catalogue, discovery, checkout.
3. **Operational MVP:** subscriptions, web dashboards, Android client, notifications, audit and support.
4. **Pilot ready:** security/performance evidence, staging rehearsal, recovery/rollback, shop onboarding materials.
5. **Nagpur pilot:** progressive shop onboarding; weekly metrics and incident/feedback review; expansion only after pilot exit criteria are met.

## Parallel work policy

Parallelize UI shells, reusable design components, documentation, test fixtures, and client integration once DTOs are approved. Keep migration ownership, auth/tenant foundation, inventory transaction semantics, checkout, and payment/webhook transitions serialized or explicitly coordinated. Every parallel ticket declares API/schema dependencies; avoid independent edits to shared contracts without review.
