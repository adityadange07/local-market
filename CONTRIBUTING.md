# Contributing

## Before starting

1. Read the relevant product, architecture, security, and testing documents linked from the root README.
2. Select a ticket and confirm its dependencies and acceptance criteria.
3. Check the working tree before editing so existing user changes remain untouched.
4. If an implementation decision materially changes product behavior and is not settled in the docs, record the open question instead of silently choosing a policy.

## Implementation rules

- Keep the backend a modular monolith, organized by business feature.
- Keep controllers thin. Put business rules in services and use request/response DTOs; never expose JPA entities.
- Use Flyway migrations for schema changes. Production Hibernate schema generation must remain disabled.
- Derive tenant access from authenticated membership. Enforce ownership in application logic and PostgreSQL RLS; add regression coverage for cross-tenant access.
- Treat checkout and payment/webhook operations as transactional, retry-safe state transitions.
- Do not store OTP plaintext, refresh tokens in plaintext, card data, or UPI PINs. Do not log credentials or unnecessary personal data.
- Web and mobile flows must include validation plus loading, empty, and error states.
- Update API/OpenAPI and documentation when externally visible behavior changes.

## Changes and review

- Keep each change focused on one ticket and avoid unrelated formatting or generated artifacts.
- Use clear, imperative commit subjects, for example `Add tenant-scoped store model`.
- Include ticket ID, behavior summary, migration/API/UI impact, and test evidence in a pull request description.
- Review authorization, tenant ownership, transaction/race behavior, migration safety, error leakage, logs, and operational impact.
- Never include secrets, production data, credentials, build output, or local environment files in a commit.

## Verification

Run the checks defined by the affected application once its development tooling has been initialized. Report commands and outcomes accurately; do not claim a check passed unless it ran. For database changes, verify migrations against PostgreSQL rather than relying on an in-memory substitute. See [CODEX_WORKFLOW.md](docs/development/CODEX_WORKFLOW.md) and [TEST_STRATEGY.md](docs/testing/TEST_STRATEGY.md).

## Definition of done

A change meets its ticket acceptance criteria, has reviewed implementation and relevant automated coverage, includes safe migrations and updated contracts/docs where needed, and has no unresolved critical tenant, authentication, payment, or data-loss issue.
