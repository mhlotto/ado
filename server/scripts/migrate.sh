#!/usr/bin/env bash
set -euo pipefail

DATABASE_URL="${DATABASE_URL:-postgres://ado:ado@localhost:5432/ado?sslmode=disable}"
GOPATH="${GOPATH:-$(pwd)/../.cache/go}"
GOCACHE="${GOCACHE:-$(pwd)/.cache/go-build}"
DATABASE_URL="$DATABASE_URL" GOPATH="$GOPATH" GOCACHE="$GOCACHE" go run ./cmd/ado-server -migrate-only
