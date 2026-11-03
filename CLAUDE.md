# Downtify Android — conventions

Native Android client for the Downtify self-hosted music server. Kotlin, Jetpack Compose + Material 3, Hilt, Media3, Room, DataStore. See README.md for build and setup.

## The server contract

- The contract lives in the server repo: `~/git/downtify/docs/mobile-client-contract.md`, with details in `docs/api-reference.md` ("Server and sign-in", "Mobile API (v1)", "WebSocket") and user-facing behaviour in `docs/features/mobile-apps.md`.
- **Don't invent endpoints.** If the app needs something the server doesn't offer, write it in `docs/server-requirements.md` and work around it (or leave the feature out).
- When the docs and the server code disagree, the code wins; note the mismatch in `docs/server-requirements.md`.
- **Never modify the server repo from here.** Read it only. To test against it, run it with `DOWNLOAD_DIR`/`DATABASE_DIR` pointed at a scratch directory and a spare `--port`; port 8000 may be the user's own instance. From 3.2 a fresh server has accounts: sign in as `admin`/`downtify` (`POST /api/auth/login`, keep the cookie, send an `Origin` header) to create pairing codes with `POST /api/auth/pairing`.
- Every request carries `Authorization: Bearer <token>` (`AuthInterceptor`), even when the server doesn't require sign-in: a 401 is how the app learns it was unpaired. A 401 "revoked", a WebSocket close 4401, or a refused WebSocket handshake confirmed by `GET /api/auth/status` all end the session (`SessionStore.onRevoked`).

## Modules and dependency rules

```
:app ──► :core:designsystem
     ──► :core:player ──► :core:data ──► :core:network ──► :core:model
```

- `:core:model` is pure Kotlin (JVM): no Android imports. Put logic here whenever it can live without Android — it's the cheapest place to test. It runs on Android too, so no JVM APIs newer than API 26 (e.g. `URLEncoder.encode(String, Charset)` is API 33 — use the `"UTF-8"` overload). Android lint does not check this module; be careful.
- `:core:network` knows the wire format (DTOs, Retrofit interfaces, WebSocket, NSD). DTOs never leave it: map to `:core:model` types. Two interfaces: `DowntifyApi` for the mobile client contract (`/api/v1`, pairing, listens, activity) and `WebApi` for the web app's own routes a device may call (search, downloads, queue, podcasts, Discover). `ApiFactory.createWebPatient` is for calls the server answers only when it's done (an episode download, Discover's first run).
- `:core:data` owns persistence and the sync/report logic. Repositories expose `Flow`/`StateFlow`; the UI never touches DAOs or the API directly.
- `:core:player` owns Media3. The UI talks to playback only through `PlayerController`.
- Screens live in `:app` under `feature/<name>/` (Screen + ViewModel). There are no feature modules yet: at this size they'd add build config without buying isolation. Split a feature out when it gains its own data layer (e.g. offline downloads).
- Gradle config goes in the convention plugins in `build-logic/`; versions only in `gradle/libs.versions.toml`. Check current stable versions before bumping — don't guess.

## Architecture

- Single activity, Navigation Compose with type-safe `@Serializable` routes (`ui/navigation/Routes.kt`).
- MVVM + unidirectional data flow: a ViewModel exposes one `StateFlow<XUiState>`; the screen calls ViewModel functions for events.
- Split every screen into a `XRoute` (gets the ViewModel, collects state) and a stateless `XScreen(state, callbacks…, modifier)` that previews and tests can call directly.
- **Text fields:** keep the text in synchronous snapshot state (`var x by mutableStateOf("")` in the ViewModel, or a plain `MutableStateFlow`), never in a flow that went through `combine`/`stateIn` — the async hop drops keystrokes. Don't rewrite the typed text (e.g. uppercase) in `onValueChange`; use a `VisualTransformation`.
- Coroutines: `viewModelScope` in ViewModels, the `@ApplicationScope` scope for app-lifetime work. Rethrow `CancellationException` from every broad `catch`.
- Android 17+ (target 37): talking to anything on the LAN needs the runtime `ACCESS_LOCAL_NETWORK` permission. The connect screen asks for it before starting NSD discovery.
- Server search and requests: `CatalogRepository` and `ServerQueueRepository` (`:core:data/catalog`). The web API's song objects aren't versioned like `/api/v1`: read them leniently (`CatalogJson`) and send them back to the server untouched (`RemoteSong.raw`), never rebuilt from the app's fields. The queue is read-only for devices.
- Podcasts (`PodcastsRepository`) and Discover (`DiscoverRepository`) go through `callServer`/`ServerResult` like the catalog. A device can only read podcasts, download an episode and save progress — subscribing is admin-only, so don't offer it. An episode is a media item with id `episode:{id}` (`EpisodeIds`) playing the server's file; anything that assumes a media id is a library track (likes, lyrics, listens, the notification's heart) must check `EpisodeIds.isEpisode` first. Progress is saved by `EpisodeProgressTracker` in the service, not the UI.
- Previews play in `PreviewPlayer`, never through the media session: they must not reach the queue, the notification, listens or activity reports. Clips come from third-party CDNs — only `AuthInterceptor` adds the token, and only for the paired server; keep it that way.
- Offline copies: `OfflineRepository` (in `:core:data/offline`) owns them. What should be on the phone is a pure `OfflinePlan` (`:core:model`) computed from the kept collections, the library, the files and the limit; the repository deletes what the plan drops at once and hands downloads to `OfflineSyncWorker`. Don't write offline files from anywhere else. The offline tables are user data: change them with Room migrations (`autoMigrations`), never by bumping the version and relying on the destructive fallback.

## Theming and UI

- Use the real Material 3 components; no look-alikes. Colors come from `MaterialTheme.colorScheme` — never hard-code hex values in screens. Brand tokens live in `core/designsystem/theme/Color.kt` (dark is the default look; light matches `Tokens.dc.html`). Dynamic color is opt-in (Settings).
- Type: by default Sora (display/headline/titleLarge) and DM Sans (everything else), as Google downloadable fonts — `theme/Type.kt`. The user can switch to the system font or Google Sans Flex, always its rounded cut (ROND axis at 100) (`AppFont` in `:core:model`, `downtifyTypography(font)`). Don't set a `fontFamily` in a screen unless it is a preview of a choice.
- Pure black (`amoled` setting): `ColorScheme.amoled()` in `theme/Theme.kt` turns the dark background and base surfaces black and darkens the raised ones; `MainActivity` also paints the window black. Use `surfaceContainer*` roles for cards so they stay visible on it.
- Settings and About use `GroupedList` (`ui/common/GroupedList.kt`), a thin wrapper over Material 3 Expressive's `SegmentedListItem` (there is no `GroupedListItem` in Material 3). It needs **material3 1.5.0-beta01** (`libs.versions.toml`); BOM-resolved 1.4.0 doesn't have it. Move to the stable 1.5 when it ships. Shapes in `theme/Shape.kt`, spacing on a 4 dp grid via `Spacing`.
- Now Playing, the album header and the mini player take their surface from the cover (`rememberCoverColors`); the play button stays `primary`.
- Icons: Material Symbols Rounded as vector drawables in `:core:designsystem` (`DowntifyIcons`), outlined at rest and filled when selected.
- Edge-to-edge: pad with `WindowInsets`, don't fight the system bars. 48 dp minimum touch targets. Every screen works in `NavigationBar` (compact) and `NavigationRail` (medium+).
- Every user-visible string goes in `app/src/main/res/values/strings.xml` **and in the eight translations** (`values-bg|el|es|fr|hu|pt-rBR|ru|tr`): the languages of the Downtify web app, English being the fallback and `resources.properties`' default. The web app's German and Korean locales exist in the server repo but aren't translated here yet — add `values-de`/`values-ko` and their `localeFilters` before offering them in the app. Lint fails on a missing translation. Keep `localeFilters` in `app/build.gradle.kts` in step with the folders (it also feeds `generateLocaleConfig`, the per-app language list). Strings that must not be translated (brand names, the license name) are `translatable="false"`. Plurals need `one` and `other`, plus the extra forms of the language (`few` and `many` for ru). Take the web app's terms (`~/git/downtify/frontend/src/i18n/locales`) when a word exists there.
- `app/src/main/res/raw/license.txt` is a copy of the repository's `LICENSE` (the License screen shows it); `LicenseTextTest` fails when they differ.
- Each screen has `@PreviewLightDark` previews built from `ui/common/PreviewData`.

## Testing and quality

- `./gradlew build` must pass (compile, unit tests, Android lint). `./gradlew detekt spotlessCheck` must be clean; `spotlessApply` formats.
- Unit tests: JUnit 4 + Turbine + fakes (MockK only where a fake is awkward). Pure logic gets tests in `:core:model`; sync and the listen queue are tested in `:core:data` against in-memory fakes.
- Compose UI tests run on the JVM with Robolectric (`app/src/test`, SDK 34 — newer sandboxes need a Java 21 test JVM). Test the stateless `XScreen`s.
- When a change affects behaviour against the server, verify it for real (emulator + a scratch server instance) and say exactly what you ran and what you didn't.

## Releases

- The version lives only in `app/build.gradle.kts` (`versionName`, and `versionCode`, which must rise with every release). Publishing a GitHub release tagged `v<versionName>` builds and attaches the signed APK in CI. Steps and the one-time key setup: `docs/releasing.md`.
- The release build is minified by R8: a library that works by reflection needs a keep rule in `app/proguard-rules.pro`. Run the release APK on a device (pair, sync, play, open the QR scanner) after adding a dependency.

## Commits

- Don't commit or push; the maintainer commits by hand. When a piece of work is finished, write a suggested message to `commit-message.md` at the repo root (it's git-ignored).
- Conventional Commits (`feat:`, `fix:`, `build:`, `docs:`, `test:`, `refactor:`), imperative subject ≤ 72 characters, body explaining why when it isn't obvious.
