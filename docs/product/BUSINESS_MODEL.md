# Business Model

## Revenue and value exchange

Local Shop OS charges shops a recurring monthly or annual subscription for digital storefront, catalogue, discovery, order management, and related seller tools. Plans, prices, included features, and availability are platform-configurable. The platform charges 0% commission on customer orders.

Customers pay shops directly. Supported MVP methods are seller-managed UPI Direct and cash on delivery (COD). The platform records payment method and seller-reported status for order operations; it does not hold customer order funds, provide seller settlement, or promise payment verification from a payment gateway.

Subscription fees are separate platform-to-seller payments. MVP supports Razorpay plus offline/manual payment records. A manually reported subscription payment remains pending until an authorized admin verifies receipt. Razorpay subscription state changes only after verified, idempotently processed gateway events or authoritative reconciliation.

## Operating model

- The pilot begins with 10–20 shops in Nagpur and one tenant per shop.
- Sellers manage their own pickup/delivery availability, fulfilment, customer communication, and order handoff.
- Admins review and approve a shop before public discovery and ordering are enabled.
- Customer orders are shop-specific. The platform provides discovery and order status visibility, not a delivery fleet.
- Shared marketplace discovery complements each shop's shareable public storefront URL.

## Subscription lifecycle (provisional)

Represent subscription status and invoices explicitly; do not infer paid status from a browser redirect. The implementation should support pending, active, past-due/suspended, cancelled, and expired states, but transition timing and grace-period behavior remain an open business decision. Keep Razorpay and manual payment attempts linked to an invoice and record actor, amount, currency, timestamps, external references, and verification outcome. Prevent duplicate activation on repeated webhooks.

## Accounting and tax

Invoices and payment records need immutable identifiers and an audit trail. GST registration, tax calculation, invoice wording, credit notes, and record-retention requirements must be reviewed with a qualified Indian accountant/legal adviser before production billing. The application design must keep tax fields configurable/evolvable and must not claim compliance until reviewed.

## MVP exclusions

- Platform order commission, escrow, seller settlements, or payment splitting.
- Platform-operated delivery, driver dispatch, or logistics tracking.
- Multi-shop checkout, cross-shop cart, or multi-tenant seller account ownership beyond the MVP tenant model.
- Loyalty points, advertising auctions, lending, complex ERP/accounting integrations, and microservices.

## Assumptions and decisions to close

Confirmed: subscription revenue; 0% order commission; UPI Direct/COD; seller-managed fulfilment; Razorpay plus admin-verified offline subscription payments. Open: plan price/entitlements, renewal/grace policy, refunds/cancellation, applicable taxes and invoice format, payment evidence for UPI orders, and subscription collection operating procedures.
