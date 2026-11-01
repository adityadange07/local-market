# Acceptance Criteria

## Product-level pilot acceptance

1. Admin can review and approve/reject a seller application; decisions and reasons are authorized and auditable.
2. An approved shop can configure its public profile, hours, fulfillment choices, catalogue, prices, images, and inventory; another tenant cannot access those records.
3. Customers can find approved shops by location/search, open a shareable storefront on web, inspect products, manage addresses, and build a cart containing one shop only.
4. Checkout validates current availability/prices and creates one order without overselling under concurrent attempts. Customer chooses seller UPI handoff or COD; platform does not collect customer order funds.
5. Seller manages order acceptance and status according to defined transition policy; customer sees history. Seller controls delivery/pickup and fulfilment.
6. Shop subscription supports monthly/annual plan configuration, Razorpay payment event handling, invoice history, and admin-verified manual payment. A browser redirect or unverified seller assertion cannot activate a subscription.
7. OTP, session rotation/revocation, RBAC, admin MFA, tenant isolation, webhook signature/idempotency, and audit events pass their security tests.
8. Customer Android and responsive public storefront support the critical browse-to-order journey with loading, empty, validation, failure, and retry states.
9. Seller/admin web workflows cover required pilot operations and are responsive at target desktop/tablet widths.
10. Deployment, health monitoring, backups, restore, incident response, and rollback have been rehearsed in staging; production secrets are not in repository or logs.

## Phase exit criteria

Phase exits are defined in `DEVELOPMENT_PHASES.md`; each ticket adds executable test cases and observable acceptance checks. Product decisions that affect behavior (cancellations, taxes, subscription grace, retention) must be resolved before their dependent release gate, not silently treated as accepted requirements.

## Pilot exit / expansion decision

After the initial 10–20 shops, review active shop usage, catalogue readiness, order success and fulfilment, repeat customer use, subscription collections, support burden, performance, and security incidents. Establish thresholds with the business before pilot commencement. Expand across Nagpur only after critical incidents are resolved, support/onboarding load is understood, and operational capacity is demonstrated.
