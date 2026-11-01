# Sprint Plan

## Capacity and planning assumptions

Plan 20 two-week sprints at about 60–70 focused implementation hours each for one primary developer, using Codex for assistance and retaining time for review, integration, and context switching. Phase estimates total 1,248 hours, so the calendar is a target, not a guarantee; add 20–25% contingency (about 4–5 sprints at this capacity) and reforecast after Sprint 1. External SMS/gateway/account approvals are not developer capacity.

## Sprint goals

| Sprint | Goal and likely ticket range | Demonstration / exit gate |
|---|---|---|
| 1 | Product decisions and LSOS-001–008 repo/API foundations | Fresh local setup and CI; domain model decision review |
| 2 | LSOS-009–013 schema and tenant boundary groundwork | Migrations and RLS test fixture; no cross-tenant query path accepted without tests |
| 3 | LSOS-014–019 auth/session/RBAC/audit | OTP-to-token flow; refresh replay and role checks demonstrated |
| 4 | LSOS-020–021 seller onboarding and store data | Admin approves applicant; public visibility follows approval |
| 5 | LSOS-022 catalogue APIs and seller web start | Seller creates and edits product/variant |
| 6 | LSOS-023–024 inventory/media | Stock adjustment history and upload validations demonstrated |
| 7 | LSOS-025 marketplace and public storefront | Search/nearby and public shop journey on web |
| 8 | LSOS-026 customer cart/address, Android foundation | Customer cart remains scoped to one shop |
| 9 | LSOS-027–028 checkout/order operations | End-to-end order, retry safety, inventory concurrency |
| 10 | LSOS-029–030 subscription billing foundation | Manual verification and Razorpay event processing tested |
| 11 | Notifications and seller order dashboard | Seller can process order and customer receives status notification |
| 12 | Seller dashboard completion and admin workflows | Admin approval/payment audit trail and seller subscription view |
| 13 | Customer Android discovery and checkout | Android app completes browse-to-order thin slice |
| 14 | Mobile order/history, integration, E2E | Full pilot journey across Android/web/admin passes |
| 15 | Security/performance and production rehearsal | Critical findings closed; backup restore and rollback rehearsed |
| 16 | Customer Android discovery and browse-to-cart | Android browse/cart slice works against API |
| 17 | Android checkout/order/profile | Browse-to-order and order history work on representative devices |
| 18 | Cross-client integration and E2E | Full pilot journey passes across Android/web/admin |
| 19 | Security/performance and production rehearsal | Critical findings closed; restore and rollback rehearsed |
| 20 | Pilot onboarding and launch support | Initial Nagpur cohort onboarded; metrics and issue triage active |

## Sprint operating cadence

Each sprint selects a goal, explicit acceptance examples, and dependencies; implementation tickets are small enough to complete and review independently. Maintain a decision log and API/schema change log. Demo integrated behavior at sprint end. Carry work only with a reason, updated estimate, and dependency impact. Reserve capacity for defects and security findings; pilot launch is gated by readiness, not by a calendar date.

## Release checkpoints

- **End Sprint 3:** auth/tenant boundary review.
- **End Sprint 6:** catalogue and image security review.
- **End Sprint 9:** checkout/inventory/payment-handoff sign-off.
- **End Sprint 10:** subscription/webhook reconciliation sign-off.
- **End Sprint 18:** feature-complete pilot candidate.
- **End Sprint 19:** release readiness approval based on documented evidence.
