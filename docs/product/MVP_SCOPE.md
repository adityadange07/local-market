# MVP Scope

## Pilot outcome

Enable 10–20 approved Nagpur shops to publish a storefront and catalogue, receive single-shop orders, and manage their own fulfilment. Customers can discover shops on the marketplace or via a shared storefront, order through Android or responsive web, and pay the shop directly by UPI or COD. Shops pay Local Shop OS subscriptions.

## In scope

- Customer/seller SMS OTP with provider adapter; admin login with MFA.
- Roles, shop tenant membership, onboarding and approval.
- Shop profile, address/geolocation, hours, delivery/pickup options, categories, products, variants, images, price/offers, stock.
- Nearby discovery, shop/product search, public storefront, customer addresses, one-shop cart, checkout, order history/status, notifications, reviews.
- Subscription plans/invoices, Razorpay subscription payment integration, manual payment recording and admin verification.
- Seller and admin responsive web dashboards; Expo Android customer app.
- Audit logging, tenant RLS, monitoring, backups, deployment/rollback process, and pilot support.

## Explicitly out of scope for pilot

Marketplace fund collection/settlement, commission, multi-shop orders, platform delivery fleet, iOS launch commitment, microservices, advanced personalization, loyalty, advertising, complex analytics/BI, broad integrations, and automated tax/legal conclusions.

## Vertical-slice release gates

1. An applicant can create a shop and an admin can approve or reject it with an auditable reason.
2. An approved seller can create a discoverable shop, manage products/stock and fulfilment modes.
3. A customer can find a shop/product, create a cart for one shop, complete checkout, and receive an order reference.
4. Seller can manage order status; customer can see status. UPI handoff/COD payment state is clear and seller-controlled.
5. Subscription can be paid through Razorpay or manually recorded and admin-verified; duplicate callbacks cannot duplicate activation.
6. Automated tests demonstrate cross-tenant access denial, checkout inventory safety, authorization, and webhook signature/idempotency.
7. Operators can deploy, monitor, back up, restore, and roll back using documented procedures.

## MVP definition choices

Plans are configurable; first plan names/prices/benefits require business input. Responsive web is required for public discovery/storefront in addition to Android. Seller/admin web dashboards share a component system but distinct authorization and navigation. “Order tracking” means platform order status history, not live driver GPS. Reviews are tied to completed orders, subject to moderation policy decision.
