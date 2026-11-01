# GitHub Actions

The initial CI entrypoint is [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml). It validates the repository documentation, tracked-file whitespace, and local Compose configuration. Backend/web/mobile jobs activate when their build manifests exist. The backend job starts local PostgreSQL/PostGIS, verifies the application, starts it with the local profile, and uploads the generated version-one OpenAPI JSON artifact.

The backend job expects Java 21 and Maven, and runs `mvn --batch-mode --no-transfer-progress verify`. Web and mobile jobs expect Node.js 22, committed npm lockfiles, and `lint`, `typecheck`, `test`, and `build` scripts. Keep these scripts deterministic and runnable without production credentials. Adjust this contract only alongside the relevant project setup ticket.

When enabling required branch checks, require the repository check plus each application job that is active for the repository. Review dependency and action updates through normal pull requests.
