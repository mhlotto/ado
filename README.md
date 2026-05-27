# ado

`ado` is a task-list application for daily and longer-term task tracking. The Android client is now the standalone primary application; the server and static web client remain in the tree as retained implementations.

<img src="clients/android/app/src/main/res/drawable-nodpi/ado_splash.png" alt="ado Android splash screen" width="360">

## Layout

```text
server/             Go REST API, PostgreSQL migrations, scripts, tests
clients/android/    Kotlin/Jetpack Compose Android client
clients/web-local/  Plain static HTML/CSS/JS local browser client
```

## Retained Server

For the retained server-backed `web-local` client, the server is the canonical API and persistence layer. It provides:

- Project, task, and subtask CRUD
- Core `Daily` and `Home` projects
- Editable templates for daily and home chore defaults
- Daily, seasonal, and Home checklist task generation
- PostgreSQL migrations and seed data
- API and PostgreSQL integration tests
- Development CORS support for local clients

Quick start:

```sh
cd server
make db-init
make run-server
```

Default server port:

```text
8989
```

Server setup, database scripts, tests, and API examples are in [server/README.md](server/README.md).

## Public Configuration

This repository does not embed a private LAN server address. Configure the client that you use:

- `clients/web-local/` defaults to `http://localhost:8989`; change its API server URL in the browser settings panel and it is stored in `localStorage`.
- `clients/android/` is standalone and does not contain or require an API server URL. Its data lives on the device and can be exported/imported from Settings.

If `web-local` is served from a LAN hostname/origin rather than opened directly or served from `localhost`, allow that origin when starting the server:

```sh
cd server
ADO_CORS_ORIGINS=http://web-host.example:5173 make run-server
```

Seeded server templates are generic starter content in the public repository and can be edited through `web-local` or the API after database initialization. Android maintains its own local templates.

The `ado` / `ado` PostgreSQL credential in local development configuration is deliberately a disposable development default. Replace it and provide a corresponding `DATABASE_URL` for any non-local deployment.

## Clients

`clients/android/` is standalone. `clients/web-local/` remains a server-backed browser client and lets you set its API URL locally.

### Android

Path:

```text
clients/android/
```

The Android client is built with Kotlin and Jetpack Compose. It includes:

- Project, task, and subtask browsing
- Create/edit/delete for projects, tasks, and subtasks
- Bulk-create tasks and subtasks from pasted text
- Task/subtask done/todo toggles
- Room-backed canonical local data storage
- JSON export/import backup with overwrite confirmation
- Local Daily/Home generation and calendar-derived daily items
- Template list and template edit screens
- Settings screen

Build:

```sh
cd clients/android
make build
```

Install on a connected device:

```sh
make install
```

More detail is in [clients/android/README.md](clients/android/README.md).

### web-local

Path:

```text
clients/web-local/
```

`web-local` is a no-build static browser client. It uses plain HTML, CSS, JavaScript, `fetch`, and `localStorage`; there is no npm, bundler, framework, or local backend shim.

It includes:

- Project, task, and subtask browsing
- Project creation
- Task and subtask creation
- Bulk-create tasks and subtasks from pasted text
- Task/subtask done/todo toggles
- Daily, Home seasonal, and Leaving house checklist generation buttons
- `localStorage` cache for recently fetched data
- Read-only cached display when the server is unavailable

Open directly:

```text
clients/web-local/index.html
```

Or serve locally:

```sh
cd clients/web-local
python3 -m http.server 5173
```

Then open:

```text
http://localhost:5173
```

More detail is in [clients/web-local/README.md](clients/web-local/README.md).

## Client Capability Notes

Android is now fully device-local and does not synchronize with the retained server. `web-local` remains server-backed and can display cached reads when the server is unavailable.
