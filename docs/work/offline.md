# offline (slice F — kept playlists and auto-sync)

## Audit: the questions asked, with evidence

**2a. Can the user keep a whole playlist offline in one action, or only individual tracks?**
One action, for the whole collection. The Album/Playlist/Liked header carries the offline chip
(`feature/collection/CollectionScreen.kt:370-374`, `OfflineChip` at `:426`) which calls
`CollectionViewModel.toggleOffline` (`CollectionViewModel.kt:184-187`) →
`OfflineRepository.setKept(type, refId, keep)` (`offline/OfflineRepository.kt:184`). That writes **one**
`offline_collections` row keyed `"<type>:<refId>"` (`OfflineCollection.keyOf`, `core/model/Offline.kt:21`);
the tracks a collection stands for are resolved from the library every time (`resolve`,
`OfflineRepository.kt:459`). There is no per-track keep. Kept collections are listed and removable on the
Downloads board (`DownloadsViewModel.kt:101-103`).

**2b. When a track is added to a playlist that is already kept, does the phone download it?**
Yes — the chain exists — **but the retry half was broken, and that is what I fixed.**

The chain: a playlist gains a track on the server → `LibraryRepository.sync` → `refreshPlaylists`
(`LibraryRepository.kt:93`, `:183`; synced by `SyncCoordinator` on launch and on `library_changed`) →
`db.playlists().observeAll()` is one of the five flows combined into `OfflineRepository.state`
(`OfflineRepository.kt:116-130`) → the collector started by `start()` (`:157`; called once from
`DowntifyApplication.kt:44`) → `reconcile` (`:334`) → `schedule` (`:382`) → `WorkManager`
`enqueueUniqueWork("offline-downloads")` → `OfflineSyncWorker.doWork` (`OfflineSyncWorker.kt:22`).

What was wrong: `schedule()` kept an in-memory `scheduledIds` memo and **returned without enqueuing**
whenever the wanted ids were a subset of it. Any run that ended without finishing left its ids in that
memo — attempts exhausted (`OfflineSyncWorker.kt:32`, `MAX_ATTEMPTS` `:39`), `Step.OutOfSpace`, or
`Step.Done` from a missing session (`OfflineRepository.kt:258`) — so the state change a newly added track
causes was swallowed and the worker's own promise ("the next change or app start schedules it again") was
false. A playlist that failed to download one track stayed "Downloading 49 of 50" until the next app start.

**2c. When a track is removed from the library or from a kept playlist, is the local copy cleaned up?**
Yes, in all three shapes:
- removed from a kept playlist/album → it drops out of `resolve` (`:459`) → not in `plan.keep`
  (`OfflinePlanner.plan`, `core/model/Offline.kt:59`) → in `plan.toDelete` (`:77`) → file deleted and row
  deleted in `reconcile` (`:336-343`);
- removed from the library altogether → same path, because the collection is resolved *through* the
  snapshot (`LibrarySnapshot.tracks`/`albumsById`, `LibrarySnapshot.kt:17`);
- collection un-kept → the row goes (`setKept(keep = false)`, `:190`), its tracks leave the plan, and the
  exported `.m3u` is deleted by `syncPlaylists`' expected-set (`OfflineStorage.kt:72-82`).
One real hole found here: `dao.deleteFiles` was called with an unbounded `IN (...)` (`Daos.kt:145-146`).
Past SQLite's 999-variable limit — a big Liked songs or a long playlist — it throws, and
`reconcileSafely` (`:322-332`) only catches `IOException`/`SecurityException`, so the exception kills the
`start()` collector and the offline layer stops for the rest of the process (no deletes, no downloads,
no exports). Fixed by chunking.

**2d. Is the limit eviction honoured for playlist-kept collections, or only for likes/tracks?**
Honoured for every kept collection, playlists included. `buildState` (`:435`) flattens *all* kept
collections' tracks, in collection order (`ORDER BY addedAt`, `Daos.kt:124`), into the single
`OfflinePlanner.plan` call — there is no separate path for likes or albums. Over-limit tracks land in
`plan.overLimit` (`Offline.kt:66-72`) and on-phone files past the limit land in `toDelete`. (`QuotaCacheEvictor`
is unrelated: it is the player's read-ahead LRU cache, `core/player/QuotaCacheEvictor.kt:12`.)

**2e. What happens when the same track is in two kept collections?**
Planned once (`distinctBy`, `Offline.kt:64`): downloaded once, counted by both collections' progress
(`OfflineProgress.of`, `Offline.kt:89`), listed in both `.m3u` files (`syncPlaylists`, `:354-372`), and it
survives until the **last** collection that wants it is un-kept. Test added.

## What it does (user-visible)

- A kept playlist now picks up a song added to it after a failed/aborted download run, instead of waiting
  for the next app start. Downloads board, "Downloading n of m" no longer sticks.
- One track too large for the free space no longer stops every other download; it is passed over like the
  planner already does with the storage limit.
- The Downloads row and the exported `.m3u` for a kept playlist show the playlist's display title, not its
  server-side name.
- No screen changed; no new strings.

## Files changed

- `core/data/.../core/data/offline/OfflineRepository.kt` — `schedule` uses `KEEP`/`REPLACE` instead of the
  `scheduledIds` memo (`:382`, `existingWorkPolicy` `:424`); chunked `deleteFiles` (`:409`) used by
  `reconcile` (`:340`) and `dropInvalidFiles` (`:401`); `downloadNext` asks `spaceCheck` (`:248`, `:428`,
  `SpaceCheck` `:98`) and passes an oversized track over; `resolve` uses `Playlist.title` for a kept
  playlist (`:468`).
- `core/data/src/test/.../offline/OfflineStateTest.kt` — five new derivation tests (+ `limitBytes` and
  `snapshot` parameters on the existing helper).
- `core/data/src/test/.../offline/OfflineScheduleTest.kt` — new: every reconcile hands the request to
  WorkManager, with the constraint-driven policy.
- `core/data/src/test/.../offline/OfflineSpaceCheckTest.kt` — new: the free-space decision.
- `handoff/offline.md`, `REPORT.md`.

## Evidence that the APIs I call exist

- `OfflineRepository.buildState` — `OfflineRepository.kt:435` (internal, already used by the existing test).
- `OfflineRepository.spaceCheck` — `:428`; `SpaceCheck` — `:98` (both added here).
- `OfflineRepository.existingWorkPolicy` — `:424`; `ExistingWorkPolicy` is already imported at `:7`
  (`androidx.work.ExistingWorkPolicy`, also used before this change at the old `:372`).
- `OfflineRepository.FREE_SPACE_MARGIN` — `:417` (`internal const`, was private).
- `OfflineDao.deleteFiles(ids: List<String>)` — `core/data/.../db/Daos.kt:145-146`.
- `OfflineCollectionEntity`, `PlaylistDao.observeAll()` — `Daos.kt:45-54`, `Entities.kt:109`.
- `Playlist.title` — `core/model/.../Collections.kt:38`.
- `OfflinePlanner.plan` / `UNLIMITED` / `OfflineProgress.of` — `core/model/.../Offline.kt:59`, `:48`, `:89`.
- `LibrarySnapshot(listOf(track))` and `tracks(ids)` — `LibrarySnapshot.kt:10`, `:17`.
- `SettingsRepository.settings` / `UserSettings()` — used identically by `OfflineRepositoryStorageTest.kt:37-39`.
- `io.mockk`, `kotlinx-coroutines-test` `runTest`/`backgroundScope` — already used in
  `OfflineRepositoryStorageTest.kt`.
- `WorkManagerImpl.getInstance(context)` static-mocked, `mockk<Operation>()` — already used in
  `OfflineRepositoryStorageTest.kt:41-43`, `:92-98`.
- `OfflineRepositoryStorageTest.kt` (existing) confirms `WorkManager.getInstance` routes to
  `WorkManagerImpl.getInstance`.

## Assumptions I could not verify (no SDK/compiler here)

1. `ExistingWorkPolicy.KEEP` = "if there is existing pending (uncompleted) work with the same unique name,
   do nothing" — from the androidx.work docs, not executed.
2. mockk can stub the `enqueueUniqueWork(String, ExistingWorkPolicy, OneTimeWorkRequest)` overload; I typed
   the matchers (`any<String>()`, `any<ExistingWorkPolicy>()`, `any<OneTimeWorkRequest>()`) so overload
   resolution is unambiguous against the `List<WorkRequest>` overload.
3. `internal` declarations are visible from the module's `src/test` (friend paths) — true here already for
   `buildState`/`reconcileSafely`/`downloadNext`.
4. `displayName` is the right title for the Downloads row: matches `CollectionViewModel` using `p.title`
   (`CollectionViewModel.kt:118`).
5. Detekt: `downloadNext` has 5 returns (limit 6, `config/detekt/detekt.yml:42-44`); no line over 120.

## What the parent must do

- Nothing for strings, DI, routes, manifest or gradle: no new string key, binding, route or permission.
- Run `./gradlew :core:data:test :core:data:compileDebugKotlin detekt spotlessCheck` (I could not).
- Review the `KEEP` decision: it makes `schedule()` a no-op only while WorkManager still has the job
  pending/running, which is what "the next change re-schedules it" was supposed to mean.

## Test coverage added

- `OfflineStateTest.aTrackAddedToAKeptPlaylistIsQueuedForDownload` — 2b: the added song is in `toDownload`.
- `OfflineStateTest.aTrackGoneFromTheLibraryLeavesItsKeptPlaylistAndThePhone` — 2c: library removal → `toDelete`.
- `OfflineStateTest.aTrackTwoKeptCollectionsShareStaysWhileEitherOfThemWantsIt` — 2e.
- `OfflineStateTest.aKeptPlaylistFillsTheLimitInTheOrderItsCollectionWasKept` — 2d.
- `OfflineStateTest.aKeptPlaylistIsNamedByItsDisplayTitle` — the title fix.
- `OfflineScheduleTest.every reconcile hands the missing tracks over again` — REPLACE, KEEP, REPLACE across
  three reconciles: the request is always handed over, so a finished run is retried.
- `OfflineScheduleTest.nothing to download enqueues nothing`.
- `OfflineSpaceCheckTest` — `SkipTrack` for one oversized track, `Enough` when it fits, `Full` without the margin.

## Known gaps (found, not fixed — deliberate)

- `OfflineCollection.refId` is the playlist **name**. Renaming a playlist on the server leaves a
  `Playlist:<old name>` row that resolves to no tracks, so its copies are deleted. Fixing it needs an id in
  the contract and a Room migration — the parent's call.
- `reconcile` still returns before `schedule()` on the delete branch (`:336-343`), relying on the emission
  `dao.deleteFiles` produces; correct today, fragile if that write is ever batched differently.
- Narrow race: `commit()` writes the audio file and only then `upsertFile` (`:296`); a reconcile running in
  between can `sweep` the file it has just written (its path is not yet in `state.files`).
- `OfflineProgress.downloaded` counts files the plan is about to evict, so a collection can read
  "On this phone" for one frame after the limit drops it.
- Downloads do not resume on pairing alone: `downloadNext` returns `Done` with no session (`:258`); it
  recovers on the post-pairing library sync.
