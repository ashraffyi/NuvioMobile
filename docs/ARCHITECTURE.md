# How this app works

Nuvio is a Stremio-style media client. It has no content of its own: the user installs
**add-ons** (Stremio protocol) that supply catalogs, metadata, streams and subtitles, and Nuvio
turns them into a browsable library with a built-in player, progress sync and tracking.

## Modules

```
:androidApp   Android application (APK). Thin shell: manifest, flavors, signing, Sentry,
              launcher-icon alias activities. Depends on :composeApp.
:composeApp   Kotlin Multiplatform library. Only the Android target remains, but code is still
              split into commonMain (shared logic + Compose UI) and androidMain (`actual`s).
```

Source sets in `composeApp/src`:

| Source set | Compiled when | Contents |
| --- | --- | --- |
| `commonMain` | always | ~95% of the app: screens, repositories, models, sync, Compose resources and ~30 translations |
| `androidMain` | always | `MainActivity`, `NuvioApplication`, SharedPreferences storage, ExoPlayer + libmpv player, downloads, notifications, P2P engine |
| `androidFull` | `full` flavor | `AppFeaturePolicy` (everything on), in-app updater, trailer extraction, plugin runtime |
| `androidPlaystore` | `playstore` flavor | `AppFeaturePolicy` (plugins, in-app trailers, updater, custom servers off) |
| `fullCommonMain` | `full` flavor | JS scraper plugin system (QuickJS) and the in-app YouTube extractor |
| `commonTest`, `androidHostTest`, `androidFullHostTest` | tests | JVM/Robolectric unit tests |

The flavor is chosen at configuration time from the Gradle task name (`...Full...` /
`...Playstore...`) or `-Pnuvio.android.distribution=full|playstore`. Building both in one
invocation is rejected because the source sets differ.

## Build-time config

`composeApp/build.gradle.kts` registers `generateRuntimeConfigs`, which writes Kotlin objects
into `build/generated/runtime-config` from `local.properties` or environment variables:
`SupabaseConfig`, `SentryConfig`, `TmdbConfig`, `TraktConfig`, `SimklConfig`, `MdbListConfig`,
`PremiumizeConfig`, `IntroDbConfig`, `ImdbEpisodeRatingsConfig`, `CommunityConfig`,
`AppVersionConfig`. Empty values simply disable that integration.

Version name/code come from `version.properties` (`-Pnuvio.app.versionName` overrides the name).

## Startup

1. `NuvioApplication` installs the Coil image loader (poster fallback interceptor, SVG/GIF, OkHttp).
2. `MainActivity.onCreate` installs the splash screen, starts Sentry (if opted in), and calls
   `initialize(context)` on ~60 `*Storage` objects. Each is an `actual object` backed by its own
   `SharedPreferences` file; most keys are profile-scoped via `ProfileScopedKey`.
3. `setContent { App() }` → `AppEnvironment` (theme) → `AppGate` (auth / profile selection /
   PIN) → `MainAppContent` → `RootTabHost` with tabs Home, Search, Library, Settings.
4. Deep links (`nuvio://meta/...`, `nuvio://party/<code>`, `nuvio://downloads`, OAuth callbacks
   `nuvio://auth/...`) arrive through `LinkActivity`/`MainActivity` and `core/deeplink`.

Navigation uses Jetpack Navigation 3 with typed routes in `navigation/Routes.kt`
(`DetailRoute`, `StreamRoute`, `PlayerRoute`, settings routes, ...).

## Data flow for playback

```
Add-on manifest ──► Home/Search catalogs ──► Details (meta) ──► Streams ──► Player
     │                    (catalog/)             (meta/)        (stream/)
     └─ AddonRepository: install, order, enable, refresh; synced per profile
```

* **Add-ons** (`features/addons`): `buildAddonResourceUrl` produces
  `<base>/<resource>/<type>/<id>[/<extra>].json` per the Stremio protocol.
* **Metadata**: add-on `meta` plus TMDB enrichment (`features/tmdb`), IMDb episode ratings,
  MDBList ratings.
* **Streams** (`features/streams`): `StreamsRepository` queries every add-on that supports
  `stream`, parses results, applies badges/filters, caches links and binge groups, and can
  auto-play based on rules. Other stream sources merge in here:
  * Debrid (`features/debrid`): Real-Debrid, Premiumize and TorBox resolution of magnets/cached files.
  * Media servers (`features/servers`): Jellyfin and Emby libraries.
  * Plugins (`full` only): JS scrapers run in QuickJS.
  * P2P (`features/p2p` + `lib-nuvio-engine-android` AAR): torrent streaming to a local HTTP URL.
* **Player** (`features/player`, the largest feature): `PlayerScreen` drives a
  `PlayerEngineController`. On Android there are two engines: **ExoPlayer/Media3** (custom
  build with FFmpeg/AV1/MPEG-H decoders in `composeApp/libs`) and **libmpv**. `Auto` sends
  anime to libmpv and HDR/Dolby Vision to ExoPlayer. Extras: libass subtitle rendering,
  add-on subtitles, subtitle AutoSync (`features/autosync`, see `docs/subtitle-autosync.md`),
  intro/outro skip, chapters, audio delay/amplification, PiP, media-session notification,
  external-player hand-off.
* **Progress**: `features/watchprogress` and `features/watched` record position; scrobbling
  goes to Trakt/Simkl through `features/tracking`.

## Accounts and sync

The backend is **Supabase** (`core/network/SupabaseProvider`, server can be swapped in
settings). `core/sync/SyncManager` pulls/pushes per profile through Postgres RPCs
(`sync_pull_*` / `sync_push_*`): add-ons, plugins, profile settings, provider credentials,
library, watch progress, watched items, collections, home catalog layout, media servers.
It pulls on foreground (rate-limited) and every 15 min. Watch parties (`features/party`) use
Supabase Realtime channels. Without an account the app works locally.

Third-party integrations: Trakt, Simkl, MDBList (lists, ratings, history), TMDB (artwork,
people, discovery), IntroDB (skip segments).

## Other features

Profiles with avatars and PINs, collections/folders on the home screen, downloads (foreground
service in `full`), new-episode notifications (WorkManager), custom themes and app icons,
episode shuffle, in-app updater (`full`) that still checks **upstream** `NuvioMedia/NuvioMobile` GitHub releases
(`features/updater/AppUpdaterRepository.kt`).

## Where to start when changing things

| Want to change | Look in |
| --- | --- |
| A screen's UI | `commonMain/.../features/<feature>/*Screen.kt` |
| What is persisted | `commonMain` `expect object *Storage` + `androidMain` `actual` |
| Playback behaviour | `features/player` (common) and `androidMain/.../features/player` |
| Feature differences between flavors | `AppFeaturePolicy` in `androidFull` / `androidPlaystore` |
| App id, signing, ABI splits, minify | `androidApp/build.gradle.kts` |
| Dependencies | `gradle/libs.versions.toml`, `composeApp/build.gradle.kts` |
