# Offline Media Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Save offline audio and portable playlists in `Android/media/com.henriquesebastiao.downtify/` by default or in a user-selected SAF folder.

**Architecture:** A storage facade selects a direct-file backend for the app-specific media directory or a `DocumentFile` backend for a persisted SAF tree. URI-based audio handles feed Media3 and loudness normalization; repository reconciliation migrates managed files and exports M3U playlists.

**Tech Stack:** Kotlin, Android Storage Access Framework, `androidx.documentfile`, Room, DataStore, Media3, Compose, JUnit/Robolectric/MockK.

**Spec:** `docs/superpowers/specs/2026-11-13-offline-media-storage-design.md`

## Global Constraints

- Android minimum SDK 26; no broad storage permission and no root.
- Default root is the first `Context.getExternalMediaDirs()` entry.
- Custom content lives below a `Downtify/` child; cleanup must never touch unrelated files.
- Playlist links are relative and only reference usable downloaded tracks.
- Existing offline playback, resume, deletion, storage accounting and normalization must keep working.

## Review Focus

- Duplicate titles and sanitized names must not overwrite tracks or playlists; pinned by path/export tests in Task 1.
- A custom folder containing unrelated files must preserve them during sweep/deletion; pinned by storage tests in Task 2.
- A revoked SAF permission must fail gracefully and leave the previous setting recoverable; pinned by backend/repository tests in Tasks 2 and 4.
- Interrupted location changes and legacy imports must not leave Room pointing at missing audio; pinned by migration tests in Task 4.
- Playlist changes during partial downloads must produce valid M3U without missing entries; pinned by exporter/repository tests in Tasks 1 and 4.

---

### Task 1: Portable paths and M3U generation

**Files:**
- Create: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/offline/OfflinePaths.kt`
- Create: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/offline/PlaylistExporter.kt`
- Test: `core/data/src/test/kotlin/com/henriquesebastiao/downtify/core/data/offline/OfflinePathsTest.kt`
- Test: `core/data/src/test/kotlin/com/henriquesebastiao/downtify/core/data/offline/PlaylistExporterTest.kt`

**Interfaces:**
- Produces: `OfflinePaths.audio(track: Track): String`, `OfflinePaths.playlist(title: String, key: String): String`, `OfflinePaths.PARTS`, `OfflinePaths.MUSIC`, `OfflinePaths.PLAYLISTS`.
- Produces: `PlaylistExporter.render(title: String, tracks: List<Pair<Track, String>>): String` where each pair is a usable track and its `Music/...` path.

- [ ] Write tests for readable artist/album/title paths, invalid separators, long names, duplicate track IDs, sanitized playlist collisions, `#EXTINF` metadata, UTF-8 text and `../Music/` relative entries.
- [ ] Run `./gradlew :core:data:testDebugUnitTest --tests '*OfflinePathsTest' --tests '*PlaylistExporterTest'` and confirm compilation fails because the new classes are absent.
- [ ] Implement pure deterministic path sanitizing with stable SHA-256 suffixes and M3U rendering; use `/` separators and escape no valid Unicode letters.
- [ ] Re-run the focused tests and confirm they pass.

### Task 2: File and SAF storage backends

**Files:**
- Create: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/offline/OfflineBackend.kt`
- Create: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/offline/FileOfflineBackend.kt`
- Create: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/offline/SafOfflineBackend.kt`
- Rewrite: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/offline/OfflineStorage.kt`
- Modify: `core/data/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Test: `core/data/src/test/kotlin/com/henriquesebastiao/downtify/core/data/offline/OfflineStorageTest.kt`

**Interfaces:**
- Produces: `OfflineAudio(val uri: Uri, val path: String, val bytes: Long, val lastModified: Long)`.
- Produces streaming targets with `path`, `length()`, `delete()`, `inputStream()`, `outputStream(append: Boolean)` and `commit(targetPath): OfflineAudio?`.
- Produces `OfflineStorage.part(trackId)`, `usable(path, bytes)`, `audio(path)`, `fileName(track)`, `commit(part, track)`, `delete(path)`, `sweep(keepPaths)`, `partialBytes()`, `freeBytes()`, `syncPlaylists(...)`, `moveTo(treeUri: String?)`.
- Consumes: path and playlist helpers from Task 1.

- [ ] Add the current stable `androidx.documentfile:documentfile` dependency through the version catalog.
- [ ] Write failing Robolectric/file-backend tests proving the default root is under `Android/media`, nested audio commits work, partial resume lengths survive, health checks reject bad files, and sweep preserves unrelated files and empty parent cleanup is safe.
- [ ] Write backend contract tests with fake document operations for SAF URIs, append output, missing documents and revoked-permission failures; verify no `java.io.File` path is fabricated from `content://`.
- [ ] Run focused tests and confirm they fail before implementation.
- [ ] Implement the common backend, direct-file and `DocumentFile` implementations, media scanner notifications and storage facade. All path resolution must reject `..` and absolute paths.
- [ ] Re-run storage and existing `OfflineFileHealthTest` tests and confirm they pass.

### Task 3: URI playback and loudness support

**Files:**
- Modify: `core/player/src/main/kotlin/com/henriquesebastiao/downtify/core/player/StreamResolver.kt`
- Modify: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/loudness/LoudnessProfiles.kt`
- Modify: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/offline/OfflineRepository.kt`
- Test: `core/player/src/test/kotlin/com/henriquesebastiao/downtify/core/player/StreamResolverTest.kt`
- Test: `core/data/src/test/kotlin/com/henriquesebastiao/downtify/core/data/loudness/LoudnessProfilesTest.kt`

**Interfaces:**
- Consumes: `OfflineRepository.localAudio(trackId: String): OfflineAudio?` from Task 2.
- Produces: Media3 `DataSpec` using `OfflineAudio.uri`; loudness accepts `OfflineAudio` and hashes through `ContentResolver`.

- [ ] Replace/add tests asserting offline playback returns a `content://` URI unchanged and default files still return `file://`.
- [ ] Add loudness tests for hashing a content URI and invalidating a receipt when metadata changes.
- [ ] Run focused tests and confirm the old `File` API fails the new cases.
- [ ] Replace `localFile`/URI conversion and file-only digest calls with the URI abstraction; keep blocking disk work on existing loader/IO threads.
- [ ] Re-run focused tests plus existing player and loudness suites.

### Task 4: Repository migration, reconciliation and playlists

**Files:**
- Modify: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/offline/OfflineRepository.kt`
- Modify: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/offline/OfflineSyncWorker.kt`
- Modify: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/db/Entities.kt`
- Test: `core/data/src/test/kotlin/com/henriquesebastiao/downtify/core/data/offline/OfflineRepositoryStorageTest.kt`

**Interfaces:**
- Produces: `OfflineRepository.setDirectory(treeUri: String?)` and observable location-move status for Settings.
- Consumes: storage move/export APIs and URI audio from Tasks 1–3.

- [ ] Write failing tests for importing `files/offline` before invalid-file cleanup, moving all managed files on location change, updating Room only after successful copies, deleting partial work safely, and exporting/removing M3U after commits and collection changes.
- [ ] Run repository tests and confirm they fail against the current fixed internal storage behavior.
- [ ] Implement serialized location changes with unique-work cancellation, startup legacy import, same-backend commit, playlist synchronization and existing sweep/plan semantics. Preserve previous paths and setting on failed moves.
- [ ] Re-run all `core:data` offline tests and confirm they pass.

### Task 5: Settings folder picker and final verification

**Files:**
- Modify: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/settings/UserSettings.kt`
- Modify: `core/data/src/main/kotlin/com/henriquesebastiao/downtify/core/data/settings/SettingsRepository.kt`
- Modify: `app/src/main/kotlin/com/henriquesebastiao/downtify/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/henriquesebastiao/downtify/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify translated `app/src/main/res/values-*/strings.xml` only where project convention requires complete translations; otherwise use default English fallback.
- Test: `core/data/src/test/kotlin/com/henriquesebastiao/downtify/core/data/settings/OfflineLocationSettingsTest.kt`
- Test: `app/src/test/kotlin/com/henriquesebastiao/downtify/SettingsScreenTest.kt` if existing screen-test patterns support it.

**Interfaces:**
- Produces: `UserSettings.offlineDirectoryUri: String?`, `SettingsRepository.setOfflineDirectory(uri: String?)`, Settings actions for choose/reset and move-error display.
- Consumes: `OfflineRepository.setDirectory` from Task 4 and `ActivityResultContracts.OpenDocumentTree`.

- [ ] Write failing DataStore tests for absent/default URI, persisted custom URI and reset to null.
- [ ] Add a settings UI test or focused state test for default path, custom label, moving state, reset visibility and failure message.
- [ ] Run focused tests and confirm they fail before the setting and UI exist.
- [ ] Implement persistence, persistable URI permission taking/release, picker callbacks, folder row and non-destructive error reporting.
- [ ] Run `./gradlew testDebugUnitTest` and confirm all project tests pass.
- [ ] Run `./gradlew :app:assembleDebug spotlessCheck detekt` and confirm the deliverable builds and static checks pass; inspect `git status` for generated artifacts.
- [ ] Exercise a real download, default media scan, M3U in an external player and SAF relocation on an Android device/emulator if available; otherwise state that device-only verification remains outstanding.
