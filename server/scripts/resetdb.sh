#!/usr/bin/env bash
set -euo pipefail

DATABASE_URL="${DATABASE_URL:-postgres://ado:ado@localhost:5432/ado?sslmode=disable}"
ADO_DB_USER="${ADO_DB_USER:-ado}"

if [[ "$DATABASE_URL" != *"localhost"* && "$DATABASE_URL" != *"127.0.0.1"* && "${ADO_CONFIRM_RESET:-}" != "yes" ]]; then
  echo "Refusing to reset non-local database without ADO_CONFIRM_RESET=yes" >&2
  exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required" >&2
  exit 1
fi

psql -v ON_ERROR_STOP=1 "$DATABASE_URL" \
  -c "DROP SCHEMA IF EXISTS public CASCADE" \
  -c "CREATE SCHEMA public" \
  -c "ALTER SCHEMA public OWNER TO \"$ADO_DB_USER\"" \
  -c "GRANT USAGE, CREATE ON SCHEMA public TO \"$ADO_DB_USER\""

DATABASE_URL="$DATABASE_URL" "$(dirname "$0")/migrate.sh"

echo "Database reset complete."
