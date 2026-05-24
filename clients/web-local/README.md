# ado web-local

`web-local` is a plain static browser client for the `ado` REST API. It uses HTML, CSS, JavaScript, `fetch`, and `localStorage`; there is no npm, bundler, framework, or local backend shim.

## Open

This client is just static files. You can open it directly in a browser:

```text
clients/web-local/index.html
```

Serving it over localhost is optional:

```bash
cd clients/web-local
python3 -m http.server 5173
```

Then open `http://localhost:5173`.

## Default Server

The UI defaults to:

```text
http://localhost:8989
```

You can change the API server URL in the settings panel. Values like `api-host.example:8989` are normalized to `http://api-host.example:8989` and saved only in browser `localStorage`.

## CORS

The Go API server must allow requests from the static client origin. The server includes development CORS support for:

```text
http://localhost:5173
http://127.0.0.1:5173
null
```

`null` is the browser origin used when opening `index.html` directly from `file://`.

Extra origins can be allowed with:

```bash
ADO_CORS_ORIGINS=http://web-host.example:5173 make run-server
```

If the browser shows `Failed to fetch`, check:

- The ado server is running.
- The Server URL field matches where the API is actually running, for example `http://localhost:8989` or `http://api-host.example:8989`.
- The server process was restarted after the CORS change.

You can verify direct-file CORS with:

```bash
API_URL=http://api-host.example:8989
curl -i -H 'Origin: null' "$API_URL/healthz"
```

The response should include:

```text
Access-Control-Allow-Origin: null
```

Some browsers also preflight local-file requests to LAN addresses using Private Network Access. Verify that path with:

```bash
curl -i -X OPTIONS \
  -H 'Origin: null' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Private-Network: true' \
  "$API_URL/api/v1/projects"
```

The response should include:

```text
Access-Control-Allow-Private-Network: true
```

## Features

- Configure and persist API server URL.
- Test `/healthz`.
- View projects.
- Create projects.
- View tasks for a selected project.
- Create tasks for a selected project.
- Bulk-create tasks for a selected project.
- View task details and subtasks.
- Create subtasks for a selected task.
- Bulk-create subtasks for a selected task.
- Toggle tasks and subtasks between `done` and `todo`.
- Strikethrough completed tasks and subtasks.
- Generate Daily task list.
- Daily generation asks whether to carry over unfinished non-default items from the most recent prior Daily list.
- Generate Home seasonal chore lists and the Leaving house checklist.
- Cache projects, tasks, task details, and subtasks in `localStorage`.
- Continue as a read-only cached UI when the API is unavailable.

## Bulk Paste

Task bulk paste accepts plain lines, bullets, numbered lists, and checkbox lists. Top-level lines create tasks. One indentation level below a task creates subtasks. Deeper indentation below a subtask is folded into that subtask description.

```text
- Replace filters
  - Buy furnace filter
    20x25x1
    Check garage shelf first
  - Replace return vent filter
- Call plumber
```

Subtask bulk paste works the same way, except top-level lines create subtasks and indented lines become that subtask description.

## Toggle Diagnostics

Task and subtask toggles send `PATCH` requests and then immediately re-fetch canonical server state with browser caching disabled. If a toggle still appears not to persist, verify the API directly:

```bash
API_URL=http://api-host.example:8989
curl -i \
  -X PATCH \
  -H 'Content-Type: application/json' \
  -d '{"status":"done"}' \
  "$API_URL/api/v1/tasks/TASK_ID"
```

Then fetch the same task:

```bash
curl -i "$API_URL/api/v1/tasks/TASK_ID"
```

## Current Limitations

- No authentication.
- No offline write queue.
- Cache is `localStorage` only.
- Project, task, and subtask creation are online-only.
- Edit/delete forms are not implemented yet.
- Conflict handling is limited to friendly messages for duplicate generated lists.
