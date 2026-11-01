# Codex Development Workflow

## Working agreement

Work in small, reviewable tickets. Read the relevant product, architecture, API, security, and test documents before changing a domain. Confirm the ticket’s dependencies and acceptance criteria; ask for a product decision only when repository evidence and documented defaults cannot resolve a material ambiguity. Record any chosen assumption in the decision log rather than silently changing scope.

## Ticket execution

1. Start from a clean branch and identify changed files and existing user changes.
2. Read module guidance and affected contracts/migrations.
3. Implement the smallest complete behavior; keep controllers thin, use DTOs, and put business rules in services.
4. For schema changes, add forward-only Flyway migrations and test from an empty database and prior migration state.
5. Add/update focused tests for acceptance criteria, especially tenant isolation, authorization, retries, and failure behavior.
6. Update OpenAPI, docs, and operational notes when behavior changes.
7. Run relevant checks; report exact commands/results, remaining risk, and changed files.
8. Do not deploy, publish, or alter external systems without explicit authorization.

## Engineering rules

- Java 21/Spring Boot 3 modular monolith; package by feature and expose explicit module interfaces.
- Never return JPA entities. Validate request DTOs and return stable error contracts.
- Flyway owns schema; production schema generation is disabled.
- Derive tenant from authenticated membership, enforce ownership in service logic and RLS, and test pooled connection context.
- Treat payment/webhook and checkout operations as retryable/idempotent state transitions.
- Keep secrets/OTP/refresh tokens/payment credentials out of logs and fixtures.
- Frontends use typed API clients, explicit auth lifecycle, form validation, accessible feedback, and loading/empty/error states.
- Prefer focused tests at unit, repository, integration, API, UI, and E2E layers according to risk; do not claim a check passed unless it ran.

## Review checklist

- Does behavior meet ticket acceptance and preserve business rules?
- Are tenant ownership and role checks enforced for every object path?
- Are constraints, transactions, race/retry behavior, and migration safety addressed?
- Are error responses useful without leaking sensitive existence/data?
- Are audit, metrics, logs, and failure recovery appropriate?
- Are APIs, docs, tests, and clients consistent?
- Are generated/build artifacts and unrelated files excluded?

## Definition of done

Reviewed implementation; applicable tests/checks pass; migrations are safe and documented; API/docs are updated; security and tenant tests are present; operational implications are recorded; acceptance criteria are demonstrated. A ticket is not done merely because code compiles.
