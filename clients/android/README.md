# ado Android

Initial Android client for `ado`, built with Kotlin and Jetpack Compose.

## Open In Android Studio

Open this directory as the project:

```text
clients/android/
```

Android Studio should detect `settings.gradle.kts` and import the `app` module.

## Build

From this directory:

```sh
make build
```

Other useful targets:

```sh
make clean
make install
```

`make install` builds the debug APK and installs it to the connected device with `adb install -r`.

The Makefile uses `./gradlew` if present and falls back to system `gradle`. Override tools as needed:

```sh
GRADLE=./gradlew ADB=/path/to/adb make install
```

## API Server Configuration

The public build defaults to the Android emulator address for a server on the host development machine:

```text
http://10.0.2.2:8989
```

For a physical phone, provide the URL reachable from that phone during the build:

```sh
ADO_SERVER_URL=http://api-host.example:8989 make install
```

You can also use a Gradle property directly:

```sh
gradle -PADO_SERVER_URL=http://api-host.example:8989 :app:assembleDebug
```

Do not commit private LAN addresses into source; use one of these local build parameters or edit the URL in the Settings screen.

The Settings screen lets you change the server URL at runtime. If only host and port are entered, the app normalizes it:

```text
api-host.example:8989 -> http://api-host.example:8989
```

## Settings Storage

The server URL is stored in Android DataStore Preferences under:

```text
ado_settings / server_url
```

A previously stored value takes precedence over a newly compiled default. Change it in Settings or clear app data when testing a new build default.

## Local Cache

The app uses `RoomLocalStore`, a Room-backed SQLite cache under app-private storage:

```text
databases/ado.db
```

Cached data:

- Projects
- Individual projects
- Tasks by project ID
- Individual tasks
- SubTasks by task ID
- Individual subtasks
- Templates
- Pending mutations

This is wrapped behind the `LocalStore` interface. Project/task/subtask writes are local-first: if the server is unavailable, the app updates the Room cache and records a pending mutation for later sync.

## Offline Changes

Supported offline operations:

- Create/edit/delete projects
- Create/edit/delete tasks
- Create/edit/delete subtasks
- Toggle task and subtask done/todo status
- Generate Daily and Home chore lists
- Import Android device calendar events into generated Daily lists when `READ_CALENDAR` is granted

The app has a persistent `Offline` / `Online` mode toggle near the top of the main screens. Offline mode uses cached reads and sends changes straight to the local mutation queue without waiting on network timeouts. If a refresh fails while online, the app asks whether to switch to offline mode. `Sync` is bold when queued mutations exist. The `Queue` action opens the Sync screen, which lists queued mutations, attempts, last errors, local IDs, and payloads for generated lists. Sync sends creates first, then updates, then deletes. Local IDs remain stable while `serverId` is filled in after successful upload, which allows offline-created items to remain usable before they reach the server.

Daily/Home generation is queued as a special `generation` mutation. The app creates a local placeholder task immediately, using cached template items for placeholder subtasks when available. Daily placeholders can also include calendar-derived subtasks read from the Android calendar provider. On sync, the app calls the server generation endpoint so canonical duplicate detection and final task/subtask creation still happen server-side. If the server reports `409 Conflict`, the local placeholder is removed.

Daily calendar import uses Android's `CalendarContract.Instances` for the selected local date. Timed events are named `HH:mm - Event title`; all-day events use the title directly. Because the v1 server subtask model does not have a tags field, calendar-derived subtasks store the source marker in the subtask description as `calendar`.

This is an intentional single-user v1 tradeoff. In a multi-user or multi-device setup, offline generation would need richer conflict handling because another client could create the same daily/seasonal list before this device syncs.

Template editing is still online-only in this pass.

## Sync Safety

The Android app does not treat an empty local cache as an instruction to delete server data.

Deleting and reinstalling the Android app removes local Room data and the pending mutation queue. On the next online launch, the app should repopulate its local cache by fetching canonical state from the server. Pressing `Sync` after a fresh install with an empty queue does not push deletes to the backend.

Only explicit queued mutations are sent to the server:

- `pending_create`
- `pending_update`
- `pending_delete`
- queued Daily/Home generation payloads

For example, if the app is freshly installed while offline, it has no cached server projects or tasks. Creating a new project/task offline records local `pending_create` mutations. When the app goes online and syncs, those creates are added to the backend. Existing backend data is not removed just because it was absent from the fresh local cache.

Normal online refreshes also preserve local rows with pending sync status so server reads do not overwrite unsynced offline edits before `Sync` replays them.

## Android Calendar Permission Setup

1. Open Android **Settings**.

2. Go to:

```text
Apps -> ado -> Permissions
```

3. Tap **Calendar**.

4. Choose **Allow**.

5. Open your Calendar app.

6. Make sure the calendar/account you use is enabled and visible.

7. Add the events you want ado to import.

8. Reopen ado and create the daily list.

## Current Features

- Project list screen
- Project detail screen
- Task detail screen
- Settings screen
- Sync queue screen
- Create/delete user projects
- Create/delete tasks
- Bulk-create tasks with pasted plain text, bullets, numbered lists, checkbox lists, and indented subtasks
- Create/delete subtasks
- Bulk-create subtasks with pasted plain text, bullets, numbered lists, checkbox lists, and indented description lines
- Edit projects, tasks, and subtasks
- Template list screen
- Template detail/edit screen
- Add/edit/delete/reorder template default subtasks
- Configurable server URL
- Test connection against `/healthz`
- Fetch projects, tasks, subtasks from the ado REST API
- Display cached data when server refresh fails
- Local-first offline create/edit/delete/toggle for projects, tasks, and subtasks
- Offline queued Daily/Home generation with local placeholders
- Manual pending mutation sync from the Project list screen
- Pending mutation diagnostics with attempts and last error
- Refresh buttons
- Strikethrough completed tasks and subtasks
- Long-press task rows to toggle done/todo
- Long-press subtask rows to toggle done/todo
- Daily core project generation button
- Daily generation asks whether to carry over unfinished non-default items from the most recent prior Daily list
- Daily generation asks for calendar permission when needed and imports same-day calendar events when allowed
- Home seasonal chore and Leaving house checklist generation buttons
- Handles `409 Conflict` generation responses as “That list already exists.”

## Known TODOs

- Add background sync with WorkManager.
- Add conflict handling based on `updated_at`.
- Add explicit `conflict` UI for server-deleted objects edited offline.
- Add UI tests once flows settle.

## Server Assumptions

The client expects the existing ado server API under `/api/v1`. JSON parsing is tolerant of missing optional fields. Project list responses may include `task_counts` and `template_actions`; if omitted, counts default to zero and actions default to empty.
