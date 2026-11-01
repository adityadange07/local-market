# Backend test packages

Organize tests by purpose: unit tests for domain rules, repository/database tests against PostgreSQL, integration tests for transactions/security, and API tests for HTTP contracts. Tenant isolation and payment/checkout invariants require real PostgreSQL behavior through Testcontainers; see [TEST_STRATEGY.md](../../../../../../docs/testing/TEST_STRATEGY.md).
