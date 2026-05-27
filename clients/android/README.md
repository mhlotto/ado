# ado Android

The Android app is the standalone `ado` task application, built with Kotlin and Jetpack Compose. Its Room database on the phone is canonical; it does not require the Go server or the web client.

## Open In Android Studio

Open this directory as the project:

```text
clients/android/
```

Android Studio should detect `settings.gradle.kts` and import the `app` module.

## Build And Install

From this directory:

```sh
make build
make install
```

`make install` builds the debug APK and installs it on a connected device with `adb install -r`. The Makefile uses `./gradlew` if present and falls back to system `gradle`; tools can be overridden:

```sh
GRADLE=./gradlew ADB=/path/to/adb make install
```

There is no API endpoint or server address to configure for the Android app.

## Local Data And Backup

The app stores projects, tasks, subtasks, and templates in the app-private Room database:

```text
databases/ado.db
```

Settings includes:

- `Roll up completed entries`
- `Export`, which writes a JSON backup through Android's document picker
- `Import`, which reads an exported JSON dataset

An import checks for records that match items already on the phone. If matches exist, the app asks whether to overwrite matching local records or keep existing local values while importing new records. Reinstalling or clearing app data deletes the Room database, so export a backup first when the data matters.

## Migration From The Server-Backed Build

Earlier builds used this same Room database as a cache and included a backend mutation queue. The standalone build migrates visible projects, tasks, and subtasks into local-only Room tables and drops backend sync metadata and the obsolete outbound queue. Records already marked for deletion remain deleted. No online/offline toggle, pending-state indicator, queue screen, or server URL setting is exposed or used.

Before installing the standalone build over a server-backed installation, perform one final refresh/sync in the old build so the phone contains the server state you intend to keep. The standalone build cannot later fetch records that exist only on the retired backend.

The source files for the old server and `web-local` client remain in the repository for reference; the Android application does not call either one.

## Android Calendar Permission Setup

Daily list creation can import same-day device calendar events as subtasks.

1. Open Android **Settings**.
2. Go to:

```text
Apps -> ado -> Permissions
```

3. Tap **Calendar** and choose **Allow**.
4. Open the Calendar app and ensure the calendar/account to import is enabled and visible.
5. Add the events to import, then reopen ado and create the daily list.

If calendar permission is denied, the daily list is still created without imported calendar entries.

## Current Features

- Project, task, and subtask browsing and create/edit/delete
- Move tasks between projects and subtasks between tasks
- Bulk-create tasks and subtasks from pasted text
- Done/todo toggles and rolled-up finished sections
- Daily core project generation for today/tomorrow, carryover prompts, and calendar import
- Home seasonal and Leaving house generated lists
- Template list and template item editing
- Room-only persistent storage
- JSON export/import backup with overwrite confirmation

## Known TODOs

- Add automated Android UI tests for backup/restore and generated lists.
