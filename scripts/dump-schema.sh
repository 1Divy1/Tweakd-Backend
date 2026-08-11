#!/usr/bin/env bash
# Dumps the live Supabase schema (public schema: tables, functions, triggers, indexes)
# into src/test/resources/db/schema.sql for use by Testcontainers-based tests.
#
# Run from the repo root whenever the Supabase schema changes:
#   ./scripts/dump-schema.sh
#
# Uses pg_dump from the postgres:17 Docker image so the client version is always >= the
# server version (the locally installed pg_dump may be too old).
#
# The raw dump is post-processed for a standalone test database:
#   - RLS policies and GRANT/OWNER statements are dropped (the backend connects as the
#     table owner; RLS is a Supabase-client concern, irrelevant to JPA tests)
#   - a minimal stub of auth.users is prepended (profiles.id references it; we don't dump
#     Supabase's auth schema)
#   - required extensions are created up front

set -euo pipefail

cd "$(dirname "$0")/.."

OUT=src/test/resources/db/schema.sql
RAW=$(mktemp)
trap 'rm -f "$RAW"' EXIT

# --- read connection info from .env ------------------------------------------------
[ -f .env ] || { echo "ERROR: .env not found at repo root" >&2; exit 1; }

env_var() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '"' ; }

JDBC_URL=$(env_var SUPABASE_DB_URL)
DB_USER=$(env_var SUPABASE_USER)
DB_PASSWORD=$(env_var SUPABASE_DB_PASSWORD)

# jdbc:postgresql://HOST:PORT/DBNAME[?params] -> host / port / dbname
HOSTPORT_DB=${JDBC_URL#jdbc:postgresql://}
HOST=${HOSTPORT_DB%%[:/]*}
REST=${HOSTPORT_DB#"$HOST"}
if [[ $REST == :* ]]; then
  PORT=${REST#:}; PORT=${PORT%%/*}
else
  PORT=5432
fi
DBNAME=${REST#*/}; DBNAME=${DBNAME%%\?*}

echo "Dumping schema 'public' from $HOST:$PORT/$DBNAME ..."

# --- dump ---------------------------------------------------------------------------
docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:17-alpine \
  pg_dump -h "$HOST" -p "$PORT" -U "$DB_USER" -d "$DBNAME" \
    --schema=public --schema-only --no-owner --no-privileges \
  > "$RAW"

# --- post-process -------------------------------------------------------------------
mkdir -p "$(dirname "$OUT")"

{
  cat <<'PREAMBLE'
-- GENERATED FILE - do not edit by hand. Regenerate with ./scripts/dump-schema.sh
-- Schema dump of the Supabase 'public' schema for Testcontainers-based tests,
-- plus a minimal stub of the Supabase-managed bits the public schema references.

-- PostGIS is installed in the public schema on the live Supabase project, so the dump
-- references public.geography — install it the same way here.
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;

-- Minimal stub of Supabase's auth schema (only what public-schema FKs/functions touch).
-- Test fixtures insert into auth.users + profiles directly; the real handle_new_user
-- trigger on auth.users is NOT recreated here.
CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE auth.users (
    id uuid PRIMARY KEY,
    email text,
    raw_app_meta_data jsonb DEFAULT '{}'::jsonb,
    raw_user_meta_data jsonb DEFAULT '{}'::jsonb,
    created_at timestamptz DEFAULT now()
);
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid
    LANGUAGE sql STABLE AS $$ SELECT NULL::uuid $$;
CREATE OR REPLACE FUNCTION auth.role() RETURNS text
    LANGUAGE sql STABLE AS $$ SELECT NULL::text $$;

PREAMBLE

  # Strip statements that don't apply to a standalone test DB:
  #   - RLS enablement + policies (reference Supabase roles/claims; may span lines)
  #   - psql meta settings pg_dump emits that need superuser
  #   - \restrict / \unrestrict guards (pg_dump >= 17.6) that older psql versions,
  #     like the one in the postgis/postgis test image, don't understand
  awk '
    /^(CREATE|ALTER|DROP) POLICY/ { skip = 1 }
    skip { if (/;[[:space:]]*$/) skip = 0; next }
    { print }
  ' "$RAW" \
    | grep -vE 'ENABLE ROW LEVEL SECURITY' \
    | grep -vE '^(SET transaction_timeout|SELECT pg_catalog\.set_config)' \
    | grep -vE '^\\(un)?restrict' \
    | grep -vE '^CREATE SCHEMA public;'
} > "$OUT"

echo "Wrote $OUT ($(grep -c 'CREATE TABLE' "$OUT") tables)"
