# Android development in this setup

How native Android apps are built in `~/Code`, distilled from `android-local-transcribe` and applied
to Squadventure. This is the "how to do app dev here" reference — patterns, versions, and gotchas
that transfer to any local-only Android app.

## Toolchain

| Thing | Value | Where |
|---|---|---|
| Kotlin | 2.0.21 | root `build.gradle.kts` |
| AGP (`com.android.application`) | 8.7.3 | root `build.gradle.kts` |
| Gradle wrapper | 8.11.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Compose BOM | 2024.10.01 | `app/build.gradle.kts` (artifacts unversioned under the BOM) |
| `compileSdk` / `targetSdk` | 35 | `app/build.gradle.kts` |
| `minSdk` | 26 (Android 8.0) | `app/build.gradle.kts` |
| Bytecode target | Java 17 (`compileOptions` + `kotlinOptions.jvmTarget`) | `app/build.gradle.kts` |
| Build JDK | 21 (`gw21`); CI uses 21 | shell alias / `release.yml` |

- **`gw21`** = `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew`. Use it for every Gradle command.
  The bytecode target (17) is decoupled from the build JDK (21) via `compileOptions`, not a
  `jvmToolchain{}` block.
- **No version catalog** (`libs.versions.toml`); dependency versions are inline literals. Keep it that
  way for consistency with the reference app.
- `gradle.properties`: parallel + caching on, `-Xmx4G`, AndroidX + non-transitive R class, official
  Kotlin code style.
- `settings.gradle.kts`: single `:app` module, `google()` + `mavenCentral()`, `FAIL_ON_PROJECT_REPOS`.
- `local.properties` (gitignored) holds `sdk.dir=/opt/homebrew/share/android-commandlinetools`.

## UI

- **Jetpack Compose**, single-activity, **Navigation Compose** with string routes. Dark-only
  Material3 theme defined in Kotlin (`phone/ui/Theme.kt`) via `darkColorScheme(...)`; the XML theme
  (`res/values/themes.xml`) only paints the window/status/nav bars black for OLED.
- `enableEdgeToEdge()` in each activity; `MainActivity` sets `configChanges=...` so rotation never
  recreates it.

## State & architecture pattern (the transferable core)

- **A Kotlin `object` singleton is the live-state hub.** In transcribe it's `RecordingController`; here
  it's `TrackingController`. It holds all runtime state as `MutableStateFlow`s exposed read-only, owns
  its own `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, and is read by the foreground
  service, the Compose UI, **and** the home-screen widget — same process, no DI, no IPC.
- **ViewModels are thin** (`AndroidViewModel`) — just wrap the repository for list screens, do IO on
  `Dispatchers.IO`, expose a `StateFlow`. Obtained with `viewModel()` in Compose.
- **No dependency injection** — construct dependencies directly or via the singleton. The
  `Application` subclass only creates the notification channel.
- **Persistence is plain files + `kotlinx.serialization`** — one directory per entity under
  `filesDir`, a `@Serializable` `*Meta` written as JSON. No Room, no DataStore. Sufficient for a
  per-activity file model; reach for Room only if you need queries over many rows.
- **Keep domain logic Android-free under `core/`** so it runs as JVM unit tests (`src/test/`). This is
  a Squadventure improvement over transcribe (which could only test on-device). Android-touching code
  lives under `phone/`.

## Foreground service (for background capture)

`TrackingService` follows transcribe's `RecordingService`:
- Manifest `<service android:foregroundServiceType="location">` (transcribe used `microphone`) +
  matching `FOREGROUND_SERVICE_LOCATION` permission.
- Started with `startForegroundService(...)`; commands routed by `intent.action`
  (`ACTION_START`/`ACTION_STOP` fully-qualified constants); `START_NOT_STICKY`.
- `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_LOCATION)` on Q+.
- Ongoing `NotificationCompat` notification (channel created in the `Application`), with a **Stop**
  action (`PendingIntent.getService` + `ACTION_STOP`) and a content intent into the active screen.
- A 1s coroutine loop refreshes the notification **and** calls the widget provider's `update()`.
- `PARTIAL_WAKE_LOCK` (needs `WAKE_LOCK`) so capture survives screen-off, released on stop/destroy.
- The service is a thin host; the real work lives in the `*Controller` singleton.

## Permissions

Runtime requests via the Compose Activity Result API
(`rememberLauncherForActivityResult(RequestMultiplePermissions())`). GPS wrinkle transcribe didn't
have: **`ACCESS_BACKGROUND_LOCATION` must be requested separately, after** fine location is granted
(Android won't grant both in one dialog). `POST_NOTIFICATIONS` only on `SDK_INT >= TIRAMISU`.

## Home-screen widget (RemoteViews, not Glance)

- `AppWidgetProvider` receiver + `res/xml/*_widget_info.xml` (`updatePeriodMillis="0"` → self-updated,
  `configure=` → config activity, `resizeMode`, `widgetFeatures="reconfigurable"`) + XML layouts in
  `res/layout/`.
- `buildViews()` constructs `RemoteViews`, sets text/visibility, attaches click `PendingIntent`s.
- A `companion object fun update(context)` enumerates placed IDs via
  `AppWidgetManager.getAppWidgetIds(ComponentName(...))` and re-renders each — call it after data
  changes and from the service loop.
- **Stat widgets must read persisted data** (repository totals), not only the in-memory singleton, so
  they show numbers when the app is idle.
- Per-widget config in `SharedPreferences` keyed by widget id; cleaned up in `onDeleted`. Config
  activity does the standard `RESULT_CANCELED`-first → write prefs → `setResult(RESULT_OK, id)` dance.

## Shortcuts

Static `res/xml/shortcuts.xml` (referenced from `MainActivity`'s `android.app.shortcuts` meta-data)
for long-press launcher shortcuts, plus a `CREATE_SHORTCUT` activity building a `ShortcutInfoCompat`
so the app appears in launchers' shortcut picker. `MainActivity.handleIntent()` reads the custom
action and routes.

## File sharing

- **Export out** via `FileProvider` (`authorities="${applicationId}.fileprovider"`, `res/xml/file_paths.xml`)
  + `ACTION_SEND`/`SEND_MULTIPLE` chooser intents with `FLAG_GRANT_READ_URI_PERMISSION`. Reusable
  from transcribe's `ShareHelper`.
- **Receive / import** (new here — transcribe only shares out): a SAF picker
  (`ACTION_OPEN_DOCUMENT`, `allowMultiple`) **and** a share-target `<intent-filter>` with
  `ACTION_SEND`/`SEND_MULTIPLE`. Note: health apps often send GPX as `*/*`, not `application/gpx+xml`,
  so accept both mime types (plus `VIEW`). Read the `content://` Uri via `contentResolver.openInputStream`.

## Signing & release

- `signingConfigs.debug` is pinned to `DEBUG_KEYSTORE_PATH` **only if that env var is set**, else falls
  back to `~/.android/debug.keystore`. The `release` build type uses the debug signing config so
  personal sideloads install without extra setup and update in place.
- CI base64-decodes the `DEBUG_KEYSTORE_BASE64` secret to a temp keystore and exports
  `DEBUG_KEYSTORE_PATH`, so CI signs with the same key as local → in-place updates.
- Release is R8-minified + resource-shrunk. Squadventure ships a **single universal APK** (no ABI
  splits — no native code).

## Tag-driven release CI (`.github/workflows/release.yml`)

- Trigger: `push` of a `v*` tag, or `workflow_dispatch` with a `tag` input.
- Steps: checkout → setup-java 21 (gradle cache) → setup-android → restore keystore from secret →
  run unit tests → `assembleRelease` → stage `squadventure-<tag>.apk` → `softprops/action-gh-release@v2`
  publishes the GitHub release with the APK. `permissions: contents: write`.
- Squadventure drops transcribe's asset-download/cache steps (no large bundled model).

## Testing

- **JVM unit tests** (`src/test/`) for the pure-Kotlin `core/` — run with `gw21 :app:testDebugUnitTest`,
  no device. This is where tile math, metrics, and GPX are proven.
- **Instrumented tests** (`src/androidTest/`) for on-device behavior; run with
  `gw21 :app:connectedDebugAndroidTest` (needs an arm64 emulator/AVD on Apple Silicon).
