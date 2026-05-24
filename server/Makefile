DATABASE_URL ?= postgres://ado:ado@localhost:5432/ado?sslmode=disable
ADO_TEST_DATABASE_URL ?= postgres://ado:ado@localhost:5432/ado_test?sslmode=disable
GO_CACHE_DIR ?= $(CURDIR)/.cache/go-build
GO_PATH_DIR ?= $(CURDIR)/../.cache/go
GO_ENV = GOPATH="$(GO_PATH_DIR)" GOCACHE="$(GO_CACHE_DIR)"

.PHONY: run-server test test-integration db-init db-reset migrate

run-server:
	$(GO_ENV) DATABASE_URL="$(DATABASE_URL)" go run ./cmd/ado-server

test:
	$(GO_ENV) go test ./...

test-integration:
	$(GO_ENV) ADO_TEST_DATABASE_URL="$(ADO_TEST_DATABASE_URL)" go test ./...

db-init:
	./scripts/initdb.sh

db-reset:
	./scripts/resetdb.sh

migrate:
	./scripts/migrate.sh
