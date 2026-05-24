# ado

`ado` is a single-user client/server task-list application for daily task lists, long-running projects, and recurring home chores.

The first pass includes:

- Go REST API server using `net/http`
- PostgreSQL persistence
- SQL migrations and seed data
- Core `Daily` and `Home` projects
- Editable task templates
- Daily, seasonal, and Home checklist task generation
- Unit/API tests with an in-memory repository
- Opt-in PostgreSQL integration tests for migrations, seed data, SQL behavior, and soft deletes
- Android architecture scaffold under `../clients/android/`

## Requirements

- Go 1.22+
- PostgreSQL 16+ running locally or on a reachable host
- `psql` and `createdb` on your PATH for database initialization scripts

## Local Development

From this directory:

Initialize the database role, app database, optional test database, migrations, indexes, and seed data:

```sh
make db-init
```

`db-init` creates or updates a PostgreSQL login role named by `ADO_DB_USER` and verifies that it can connect to `ADO_DB_NAME`. It does not create an operating-system user.

The default initialization values are:

```text
PGHOST=localhost
PGPORT=5432
PGADMIN_USER=postgres
ADO_DB_USER=ado
ADO_DB_PASSWORD=ado
ADO_DB_NAME=ado
ADO_TEST_DB_NAME=ado_test
CREATE_TEST_DB=1
```

These `ado` / `ado` values are local development defaults, not deployment credentials. Configure stronger PostgreSQL credentials and set `DATABASE_URL` before exposing a database or API outside a trusted development environment.

If your local admin role is different, pass it in:

```sh
PGADMIN_USER="$(whoami)" make db-init
```

Run migrations only:

```sh
make migrate
```

Run the API server:

```sh
make run-server
```

Run tests:

```sh
make test
```

Run PostgreSQL integration tests against a disposable test database:

```sh
PGPASSWORD=ado createdb -h localhost -U ado ado_test
make test-integration
```

Integration tests reset the target database schema before each test. For safety, `ADO_TEST_DATABASE_URL` must point at a database whose name contains `test`.

Reset the local database:

```sh
make db-reset
```

`db-reset` drops and recreates the `public` schema for `DATABASE_URL`, then reruns migrations. It refuses non-local database URLs unless `ADO_CONFIRM_RESET=yes` is set.

The default database URL is:

```text
postgres://ado:ado@localhost:5432/ado?sslmode=disable
```

Override it with `DATABASE_URL`.

## API

Base path:

```text
/api/v1
```

Health:

```sh
curl http://localhost:8989/healthz
```

Create a project:

```sh
curl -X POST http://localhost:8989/api/v1/projects \
  -H 'Content-Type: application/json' \
  -d '{"name":"Errands","description":"Things to do outside the house","tags":["personal"]}'
```

List projects:

```sh
curl http://localhost:8989/api/v1/projects
```

Project list responses include `task_counts`. Core projects also include `template_actions` for Android buttons like generating daily lists, seasonal chores, or Home checklists:

```json
[
  {
    "id": "uuid",
    "name": "Daily",
    "description": "",
    "tags": [],
    "is_core": true,
    "core_key": "daily",
    "created_at": "2026-05-08T12:00:00Z",
    "updated_at": "2026-05-08T12:00:00Z",
    "task_counts": {
      "total": 1,
      "open": 1,
      "todo": 1,
      "in_progress": 0,
      "done": 0,
      "archived": 0
    },
    "template_actions": [
      {
        "template_key": "daily",
        "name": "Daily",
        "generate_endpoint": "/api/v1/templates/daily/generate"
      }
    ]
  }
]
```

Get project detail with task counts:

```sh
curl http://localhost:8989/api/v1/projects/{project_id}
```

Get project detail with nested tasks:

```sh
curl 'http://localhost:8989/api/v1/projects/{project_id}?include=tasks'
```

Get project detail with nested tasks and subtasks:

```sh
curl 'http://localhost:8989/api/v1/projects/{project_id}?include=tasks,subtasks'
```

Project detail responses include `task_counts`. Core project detail responses also include `template_actions`.

```json
{
  "id": "uuid",
  "name": "Errands",
  "description": "",
  "tags": [],
  "is_core": false,
  "created_at": "2026-05-08T12:00:00Z",
  "updated_at": "2026-05-08T12:00:00Z",
  "task_counts": {
    "total": 4,
    "open": 2,
    "todo": 1,
    "in_progress": 1,
    "done": 1,
    "archived": 1
  }
}
```

Create a task:

```sh
curl -X POST http://localhost:8989/api/v1/projects/{project_id}/tasks \
  -H 'Content-Type: application/json' \
  -d '{"name":"Buy groceries","description":"Milk, eggs, coffee"}'
```

Mark a task done:

```sh
curl -X PATCH http://localhost:8989/api/v1/tasks/{task_id} \
  -H 'Content-Type: application/json' \
  -d '{"status":"done"}'
```

Create a subtask:

```sh
curl -X POST http://localhost:8989/api/v1/tasks/{task_id}/subtasks \
  -H 'Content-Type: application/json' \
  -d '{"name":"Buy coffee","description":""}'
```

Get the daily template:

```sh
curl http://localhost:8989/api/v1/templates/daily
```

Update a template:

```sh
curl -X PATCH http://localhost:8989/api/v1/templates/daily \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"name":"review calendar","position":0},{"name":"set priorities","position":1},{"name":"check weather","position":2}]}'
```

Generate a daily task list:

```sh
curl -X POST http://localhost:8989/api/v1/templates/daily/generate \
  -H 'Content-Type: application/json' \
  -d '{"date":"today"}'
```

Daily task names are generated as `YYYY-MM-DD Day`, for example `2026-05-08 Friday`.

Generate seasonal chores:

```sh
curl -X POST http://localhost:8989/api/v1/templates/spring_chores/generate \
  -H 'Content-Type: application/json' \
  -d '{"year":2026}'
```

Generate the Home leaving-house checklist:

```sh
curl -X POST http://localhost:8989/api/v1/templates/leaving_house/generate \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Sync snapshot for initial local cache hydration:

```sh
curl http://localhost:8989/api/v1/sync/snapshot
```

Incremental sync after a known timestamp:

```sh
curl 'http://localhost:8989/api/v1/sync/snapshot?updated_since=2026-05-08T12:00:00Z'
```

Snapshot responses include server time plus projects, tasks, subtasks, and templates:

```json
{
  "server_time": "2026-05-08T12:00:00Z",
  "projects": [],
  "tasks": [],
  "subtasks": [],
  "templates": []
}
```

Full snapshots include active projects, tasks, and subtasks. Incremental snapshots include rows changed after `updated_since`, including soft-deleted projects, tasks, and subtasks with `deleted_at` set.

## Error Shape

Errors use:

```json
{
  "error": {
    "code": "validation_error",
    "message": "name is required"
  }
}
```

Common status mappings:

- `400` validation error
- `404` not found
- `409` conflict
- `500` internal error

## Data Model

```text
Project
  Task
    SubTask
```

Projects, tasks, and subtasks use soft deletes via `deleted_at`. Deleting a project soft-deletes its tasks and subtasks. Deleting a task soft-deletes its subtasks.

Core projects are seeded automatically:

- `Daily`, with `core_key = daily`
- `Home`, with `core_key = home`

Core projects cannot be deleted or renamed, but their description and tags can be updated.

## Tests

`make test` exercises the API and service behavior with an in-memory repository. It also compiles the PostgreSQL integration tests, which skip unless `ADO_TEST_DATABASE_URL` is set.

`make test-integration` runs the same server stack against PostgreSQL using `db.Store`. The integration tests reset the target test database schema, run migrations, verify seed data, exercise project/task/subtask CRUD, check soft-delete cascades, and verify template update/generation behavior.
