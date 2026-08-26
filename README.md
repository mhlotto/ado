# Ado

<img src="ado-icon.png" alt="Ado app icon" width="140">

Ado is a simple, local-first Android app for projects, tasks, checklists, daily lists, and the everyday things you need to remember.

It is designed to stay practical: capture items quickly, organize them when needed, mark them finished, and keep the data on your device.

## Features

- Projects, tasks, and subtasks
- Simple and full list views
- Completion controls and finished-item history
- Drag-and-drop reordering
- Single, bulk, and template-based creation
- Daily, seasonal, market, and other reusable lists
- Optional calendar import for daily lists
- Task sharing and checklist printing
- Local Room storage
- Versioned JSON backup and restore
- Clickable HTTP and HTTPS links

Ado does not require an account or a server. Its working data is stored locally and only leaves the app when you explicitly export, print, or share it.

## Repository layout

```text
app/          Android application
app-website/  Static Ado website and privacy policy
```

The Android app is built with Kotlin, Jetpack Compose, Material 3, and Room. The website is plain HTML and CSS with no build step.

## Build the Android app

Requirements:

- JDK 17
- Android SDK

```sh
make doctor
make build
```

Install the debug build on a connected Android device:

```sh
make install
```

Run the unit tests:

```sh
./gradlew test
```

Run `make help` to see the available Android build targets.

## Website

The public website lives in `app-website/`. It can be served by any static web server. For a local preview:

```sh
cd app-website
python3 -m http.server 8000
```
