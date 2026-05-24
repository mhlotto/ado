#!/usr/bin/env bash
set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGADMIN_USER="${PGADMIN_USER:-postgres}"
ADO_DB_USER="${ADO_DB_USER:-ado}"
ADO_DB_PASSWORD="${ADO_DB_PASSWORD:-ado}"
ADO_DB_NAME="${ADO_DB_NAME:-ado}"
ADO_TEST_DB_NAME="${ADO_TEST_DB_NAME:-ado_test}"
CREATE_TEST_DB="${CREATE_TEST_DB:-1}"

require_identifier() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "$name must be a simple PostgreSQL identifier, got: $value" >&2
    exit 1
  fi
}

require_identifier "ADO_DB_USER" "$ADO_DB_USER"
require_identifier "ADO_DB_NAME" "$ADO_DB_NAME"
require_identifier "ADO_TEST_DB_NAME" "$ADO_TEST_DB_NAME"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required" >&2
  exit 1
fi

if ! command -v createdb >/dev/null 2>&1; then
  echo "createdb is required" >&2
  exit 1
fi

escaped_password="${ADO_DB_PASSWORD//\'/\'\'}"

psql_admin() {
  psql -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGADMIN_USER" -d postgres "$@"
}

role_exists="$(psql_admin -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$ADO_DB_USER'")"
if [[ "$role_exists" != "1" ]]; then
  psql_admin -c "CREATE ROLE \"$ADO_DB_USER\" WITH LOGIN PASSWORD '$escaped_password'"
  echo "Created PostgreSQL role: $ADO_DB_USER"
else
  psql_admin -c "ALTER ROLE \"$ADO_DB_USER\" WITH LOGIN PASSWORD '$escaped_password'"
  echo "Updated PostgreSQL role password: $ADO_DB_USER"
fi

ensure_database() {
  local db_name="$1"
  local exists
  exists="$(psql_admin -tAc "SELECT 1 FROM pg_database WHERE datname = '$db_name'")"
  if [[ "$exists" != "1" ]]; then
    createdb -h "$PGHOST" -p "$PGPORT" -U "$PGADMIN_USER" -O "$ADO_DB_USER" "$db_name"
    echo "Created PostgreSQL database: $db_name"
  else
    psql_admin -c "ALTER DATABASE \"$db_name\" OWNER TO \"$ADO_DB_USER\""
    echo "Database already exists: $db_name"
  fi

  psql -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGADMIN_USER" -d "$db_name" \
    -c "GRANT CONNECT, TEMPORARY ON DATABASE \"$db_name\" TO \"$ADO_DB_USER\"" \
    -c "ALTER SCHEMA public OWNER TO \"$ADO_DB_USER\"" \
    -c "GRANT USAGE, CREATE ON SCHEMA public TO \"$ADO_DB_USER\""
  echo "Granted database and schema privileges on $db_name to $ADO_DB_USER"
}

ensure_database "$ADO_DB_NAME"
if [[ "$CREATE_TEST_DB" == "1" ]]; then
  ensure_database "$ADO_TEST_DB_NAME"
fi

export DATABASE_URL="${DATABASE_URL:-postgres://$ADO_DB_USER:$ADO_DB_PASSWORD@$PGHOST:$PGPORT/$ADO_DB_NAME?sslmode=disable}"

connected_user="$(PGPASSWORD="$ADO_DB_PASSWORD" psql -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$ADO_DB_USER" -d "$ADO_DB_NAME" -tAc "SELECT current_user")"
if [[ "$connected_user" != "$ADO_DB_USER" ]]; then
  echo "Failed to verify PostgreSQL login role $ADO_DB_USER for database $ADO_DB_NAME" >&2
  exit 1
fi
echo "Verified PostgreSQL login role: $ADO_DB_USER"

"$(dirname "$0")/migrate.sh"

echo "Database initialization complete. Migrations created tables, indexes, seed data, and schema_migrations rows."
