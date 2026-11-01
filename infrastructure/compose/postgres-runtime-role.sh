#!/bin/sh
set -eu

: "${POSTGRES_USER:?POSTGRES_USER must be set}"
: "${POSTGRES_DB:?POSTGRES_DB must be set}"
: "${APP_DB_USERNAME:=localshop_app}"
: "${APP_DB_PASSWORD:=localshop_app_dev_password}"

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 \
    --set=app_db_user="$APP_DB_USERNAME" \
    --set=app_db_password="$APP_DB_PASSWORD" \
    --set=bootstrap_user="$POSTGRES_USER" \
    --set=target_db="$POSTGRES_DB" <<'SQL'
SELECT format(
    'CREATE ROLE %I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS',
    'localshop_runtime'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'localshop_runtime')
\gexec

ALTER ROLE localshop_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;

SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
    :'app_db_user', :'app_db_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_db_user')
\gexec

SELECT format(
    'ALTER ROLE %I WITH LOGIN PASSWORD %L INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
    :'app_db_user', :'app_db_password'
)
\gexec

SELECT format('GRANT localshop_runtime TO %I', :'app_db_user')
\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO localshop_runtime', :'target_db')
\gexec
GRANT USAGE ON SCHEMA public TO localshop_runtime;

GRANT SELECT ON ALL TABLES IN SCHEMA public TO localshop_runtime;

SELECT format(
    'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT ON TABLES TO localshop_runtime',
    :'bootstrap_user'
)
\gexec
SQL
