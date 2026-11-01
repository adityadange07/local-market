# Local Compose services

This directory provides PostgreSQL 16 with PostGIS and Redis for local development only. Ports bind to loopback and use non-production credentials; the services share a private Compose network. Named volumes preserve data across container recreation.

## Start

```sh
cd infrastructure/compose
cp .env.example .env
docker compose -f docker-compose.yml up -d
docker compose -f docker-compose.yml ps
```

The default host ports are PostgreSQL `25433` and Redis `26380` to avoid common local service ports. Override them in `.env` if occupied. PostgreSQL credentials and Redis password are read from `.env`; the defaults are disposable local-development values only. Never reuse them in staging or production.

Confirm PostGIS is available:

```sh
docker compose -f docker-compose.yml exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT PostGIS_Full_Version();"'
```

PostgreSQL health indicates server readiness; the SQL check confirms the extension.

## Stop and reset

```sh
docker compose -f docker-compose.yml stop
docker compose -f docker-compose.yml down
```

`down` retains named volumes. Removing volumes destroys local database/cache data and requires an explicit reset decision; do not use `down -v` casually. No application containers are defined until their build contexts exist.
