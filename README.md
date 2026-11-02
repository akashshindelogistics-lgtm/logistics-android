# Logistics Driver (Android)

[![Android CI](https://github.com/akashshindelogistics-lgtm/logistics-android/actions/workflows/android.yml/badge.svg)](https://github.com/akashshindelogistics-lgtm/logistics-android/actions/workflows/android.yml)
[![Release](https://github.com/akashshindelogistics-lgtm/logistics-android/actions/workflows/release-please.yml/badge.svg)](https://github.com/akashshindelogistics-lgtm/logistics-android/actions/workflows/release-please.yml)
&nbsp; [![Latest release](https://img.shields.io/github/v/release/akashshindelogistics-lgtm/logistics-android?sort=semver)](https://github.com/akashshindelogistics-lgtm/logistics-android/releases)

A native Kotlin app for the driver's phone. It pairs with a driver using a
device token and reports the phone's location, so the driver's
assigned vehicle moves on the dispatch map without a hardware GPS tracker.
It replaces the Expo app in `mobile/` of logistics-backend.

It is one of three repos:

| Repo | What |
| --- | --- |
| [logistics-backend](https://github.com/akashshindelogistics-lgtm/logistics-backend) | The Rust REST API the app talks to, the feature docs, and `todo.org` |
| [logistics-frontend](https://github.com/akashshindelogistics-lgtm/logistics-frontend) | The dispatch dashboard, where the office pairs a driver's phone (QR code) and hands out this app |
| **logistics-android** | This app |

Until November 2026 all three lived in one repo; this repo keeps the app's
history from then (its two docs were copied in at the split).

Design, decisions and phases: [docs/android-driver-app.md](docs/android-driver-app.md).
Install guide for drivers and the office: [docs/android-driver-install.md](docs/android-driver-install.md).
Backend contract: [driver-phone-tracking.md](https://github.com/akashshindelogistics-lgtm/logistics-backend/blob/master/docs/driver-phone-tracking.md).

## Status

Phases 2 to 5 are built (skeleton, background reporting, tests and demo, release):

- **Pairing screen:** **Scan QR code** reads the pairing code from the
  dashboard's Driver's phone panel (a `logisticsdriver://pair` link, which
  the app also opens from outside). Or enter the server address and device
  token by hand. Either way the app checks them with `GET /api/driver/me`
  when the driver taps Pair, and saves nothing before.
- **Status screen:** the driver, organization, active status, assigned
  vehicle and its last position, from `GET /api/driver/me`. It has Refresh,
  Unpair (with confirmation) and the app version.
- **"Share my location":** asks for location (and notification) permission,
  then starts a foreground service that takes a fix every 30 s or 50 m,
  queues it in Room and uploads in batches of 100. Fixes queue while offline
  and upload when the network is back (WorkManager). Queued count, last fix,
  last sync and Sync now are on the status screen.
- **Restarts:** after a reboot or an app update, a "Tap to resume"
  notification; the status screen warns when sharing is on but stopped.
- **Battery:** a one-time explanation and a card with the phone brand's
  battery settings steps.
- **Updates:** the app checks `GET /api/driver-app/latest` on the paired
  server. When a higher `versionCode` is published, an "Update available"
  card downloads it through the browser, to install over this app.

Left: phase 6, retiring the Expo app in logistics-backend's `mobile/` once this app has been
checked on a real phone against an `https://` backend.

## Setup

One-time, on this machine (details in the plan doc):

- Android Studio extracted to `~/Downloads/android-studio`; its bundled JDK is `jbr/`.
- SDK at `~/Android/Sdk` with platforms 36 and 37 and build-tools 36, plus the
  `Medium_Phone_API_37.0` emulator (API 37, with Google Play services).

For command-line builds:

```sh
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=$HOME/Downloads/android-studio/jbr
```

Or open this repo in Android Studio (File > Open), which uses its own JDK and SDK.

## Build and test

```sh
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew lintDebug           # Android lint
./gradlew connectedDebugAndroidTest   # on a running emulator or phone
```

The instrumented tests (`app/src/androidTest`) cover the Room queue on a real
SQLite database and the pairing screen in the running app, against a
MockWebServer on the phone. They unpair the app, so don't run them on a phone
in use.

Install on a running emulator or USB phone with
`$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Demos

The demos need the API and, for their dashboard half, the dashboard: clone
the other two repos next to this one (or set `BACKEND_DIR` / `FRONTEND_DIR`):

```text
logistics/
├── logistics-backend/    # started with cargo run if nothing answers on API_PORT
├── logistics-frontend/   # npm ci once; its Playwright demos run from here
└── logistics-android/    # this repo
```

All open the emulator in a window, start the backend if nothing answers on
port 8080 (`API_PORT=8081` to use another), seed an org, a vehicle and a
driver with a device token, install the app and pair it by typing the token
over adb. Shared steps are in `scripts/lib.sh`.

```sh
scripts/demo-tracking.sh   # phase 4 end to end, about 3 minutes
scripts/demo-update.sh     # distribution and in-app update, about 3 minutes
scripts/demo-phone-pairing.sh  # pairing from the dashboard's QR code, about 3 minutes
scripts/demo-pairing.sh    # pairing only, under a minute
```

`demo-update.sh` starts its own backend, with releases in
`build/demo/driver-app-releases`. It installs and pairs v1, publishes
a v2 build (`-Pdriver.versionCode=200`, above v1's), and checks that the app offers it.
Download update must have Chrome save v2's exact APK (it clicks through
Chrome's first-run screens), and v2 installed over v1 must still be paired.
It ends on the dashboard (`tests/driver-app.demo.ts` in logistics-frontend): the driver's
page shows v0.2.0-demo and a QR code, and its link downloads the same APK.
Recorded to `build/demo/update-demo.webm`.

`demo-tracking.sh` switches sharing on and drives the emulator along the
Chakan - Pune road with `adb emu geo fix`. After each position it checks the
vehicle through `GET /api/vehicles` and in the database (the `Vehicle` row in
MySQL, with the backend's `MYSQL_*` settings; needs the `mysql` client). It
then turns the emulator's network off, checks that fixes queue on the phone
while the API and database stay put, and turns the network back on to check
that the queue uploads. Last, it opens the dashboard in Chromium
(`tests/driver-tracking.demo.ts` in logistics-frontend), signs in as the seeded org and
checks the final position on the Vehicles page (fleet map pin and
coordinates) and on the vehicle's page. Any check that fails stops the
script. The phone run is recorded to `build/demo/tracking-demo.webm`
(final screenshot `tracking-status.png`), the dashboard to
logistics-frontend's `test-results/driver-tracking*/video.webm`. With `API_PORT`
other than 8080, the dashboard runs on its own Vite at 5174 (`WEB_PORT`). On its own,
`npm run test:e2e:demo:driver-tracking` (in logistics-frontend) seeds a driver and
sends a fix the way the phone does.

`demo-phone-pairing.sh` runs the dashboard (`tests/phone-pairing.demo.ts` in logistics-frontend)
and the emulator side by side:
- the driver's page issues a token as a QR code, and the demo decodes it off
  the screen;
- the emulator opens the decoded link; the form fills in, and pairs only on
  Pair;
- MySQL shows the phone paired and seen, and the panel shows "last reported";
- the dashboard unpairs, and the app's next Refresh is refused.

The emulator reaches the dashboard's address through `adb reverse`. Recorded
to `build/demo/phone-pairing-demo.webm`.

`demo-pairing.sh` reports one location fix with curl and taps Refresh.
Recorded to `build/demo/pairing-demo.mp4`.

## Releases

Drivers install a signed APK directly; there's no Play Store listing. The
install guide for drivers and the office, including building a release, is
[docs/android-driver-install.md](docs/android-driver-install.md). In short:

```sh
scripts/make-release-keystore.sh   # once; back the key up off this machine
# merge the release PR (bumps version.txt, tags vX.Y.Z), check out the tag, then:
DRIVER_KEYSTORE=~/.config/logistics-driver/release.jks PUBLISH_DIR=/tmp/driver-app \
    scripts/build-release.sh
```

Drivers download the app from the backend, at `/api/driver-app/download`;
each driver's page on the dashboard shows the link, a QR code and the server
address. `PUBLISH_DIR` (or `scripts/publish-apk.sh <apk> <dir>`) writes the
APK and `latest.json` for the backend to serve; for a deployed server, copy
that folder onto the uploads volume (see the install guide). The app offers
every newer release by itself.

Signing reads `DRIVER_KEYSTORE`, `DRIVER_KEYSTORE_PASSWORD` and optionally
`DRIVER_KEY_ALIAS` (default `driver`) and `DRIVER_KEY_PASSWORD` from the
environment; nothing about the key is in git except its certificate
fingerprint, `release-cert.sha256`, which `build-release.sh` checks every
release against. Without `DRIVER_KEYSTORE`, `assembleRelease` builds an
unsigned APK.

**Versions:** `version.txt` holds the app's version (`versionName`), and
Gradle derives `versionCode` from it as major × 10000 + minor × 100 + patch
(0.1.0 → 100), so every release installs over the last.

**CI and releases:**

| Workflow | What it does |
| --- | --- |
| `android.yml` | Unit tests, lint, and the debug and unsigned release builds on every push; uploads the debug APK |
| `release-please.yml` | Keeps a release PR that bumps `version.txt` and `CHANGELOG.md`. Merging it tags `vX.Y.Z` in this repo, independent of the backend's and the dashboard's versions. The signed APK is still built locally from that tag, because the signing key never goes to CI |

## Connecting to a backend

- **Emulator:** the debug build pre-fills `http://10.0.2.2:8080`, which is
  `cargo run` on this machine as seen from the emulator.
- **USB phone:** run `adb reverse tcp:8080 tcp:8080`, then use
  `http://localhost:8080`.
- **Real deployment:** an `https://` address. Release builds refuse plain
  `http://`; debug builds allow it only for the three local addresses above
  (`src/debug/res/xml/network_security_config.xml`).

## Layout

```
app/src/main/java/com/logistics/driver/
├── Config.kt            # constants mirrored from the backend
├── DriverApplication.kt, AppContainer.kt, MainActivity.kt
├── domain/              # pure Kotlin: input parsing, fix validation, flush outcomes, battery advice
├── net/                 # DriverApi (OkHttp + kotlinx.serialization), DTOs, error messages
├── data/                # pairing (DataStore + Keystore), Room fix queue, StatusStore, LocationReporter
├── tracking/            # LocationService, FlushWorker, BootReceiver, notifications
└── ui/                  # DriverApp (pair vs status), screens, view models, theme
```

Versions are pinned in `gradle/libs.versions.toml`: AGP 9.4.1, Gradle 9.6.0,
Kotlin 2.4.20, Compose BOM 2026.09.00, Room 2.8.5 (KSP 2.3.12), WorkManager 2.12.0,
Play services location 21.4.0. `compileSdk` is 37, `targetSdk` 36 and
`minSdk` 26.
