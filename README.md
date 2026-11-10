# Nuvio (personal Android build)

A personal, Android-only copy of [NuvioMedia/NuvioMobile](https://github.com/NuvioMedia/NuvioMobile).
The iOS app, desktop sources, sideload store feed and upstream community tooling have been
removed. The shared Kotlin code in `composeApp/src/commonMain` is kept because the Android app
is built from it.

## Layout

| Path | What it is |
| --- | --- |
| `androidApp/` | The Android application module: manifest, launcher-icon activities, flavors, signing, Sentry. |
| `composeApp/src/commonMain` | Almost all app logic and UI (Compose Multiplatform), resources and translations. |
| `composeApp/src/androidMain` | Android `actual` implementations: storage, ExoPlayer/libmpv player, downloads, notifications, P2P engine. |
| `composeApp/src/androidFull` / `androidPlaystore` | Per-flavor code, mainly `AppFeaturePolicy`. |
| `composeApp/src/fullCommonMain` | Code only compiled into the `full` flavor: JS scraper plugins and the in-app YouTube trailer extractor. |
| `composeApp/libs/` | Prebuilt AARs (custom Media3 build with FFmpeg/AV1 decoders, libass, QuickJS, Nuvio P2P engine). |
| `testFixtures/` | Media used by instrumented subtitle tests. |
| `version.properties` | App version name and code. |

## Build

Requires JDK 17+ and the Android SDK (platform `android-37.1`).

```bash
./gradlew :androidApp:assembleFullDebug        # sideload flavor, all features
./gradlew :androidApp:assemblePlaystoreDebug   # Play Store flavor, restricted features
```

Run on a device or emulator: `./scripts/run-mobile.sh android [e|p] [full|playstore]`.
Stream logs: `./scripts/debug_logs.sh`.

Optional keys go in `local.properties` (TMDB, Trakt, Simkl, MDBList, Supabase, Sentry, release
signing). Without them the app builds, but the matching integrations are disabled.

## License

[GNU General Public License v3.0](./LICENSE)
