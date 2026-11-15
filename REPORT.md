# sleep-timer

## What it does (user-visible, and where it appears in the UI)

A Spotify-style sleep timer that keeps running when the app is in the background or closed.

- **Now Playing → the moon button** in the header row (right of the "playing from" title, left of
  lyrics and the queue) opens a bottom sheet: the durations **5 / 10 / 15 / 30 / 45 / 60 / 90 min**
  as chips, then **End of track** and **End of episode** as radio rows, with the running one filled
  in. With a timer running the sheet also offers **Cancel sleep timer**.
- While a timer runs, a **small pill under the title** counts down (`12:34 left`) — or says *End of
  track* / *End of episode* when the item's length isn't known yet. **Tapping the pill cancels.**
- When it runs out, the volume **fades to nothing over the last 5 s** and playback pauses there; the
  volume comes back the moment it pauses, on cancel, and when playback resumes. For *End of track*
  the fade is the last 5 s of that song, so the song after it is never heard (it is paused at the
  transition, at zero volume).
- The countdown only runs while audio is really playing: a pause freezes it, a seek moves an
  end-of-item timer with the song.
- A timer survives the app and the service going away: it is persisted as it counts and picked up
  when `PlaybackService` comes back.

## Files changed

- `core/model/src/main/kotlin/.../core/model/SleepTimer.kt` — **new**: the pure state machine
  (`SleepTimerMode`, `SleepTimerPhase`, `SleepTimerState`, `SavedSleepTimer`); no Android, no clock.
- `core/model/src/test/kotlin/.../core/model/SleepTimerTest.kt` — **new**: 13 tests on a fake clock.
- `core/player/src/main/kotlin/.../core/player/SleepTimerController.kt` — **new**: the `@Singleton`
  that owns the live timer, started/stopped by the service, ticked every 250 ms, fades and pauses the
  player, persists through `SettingsRepository`.
- `core/player/src/main/kotlin/.../core/player/PlayerState.kt` — one field: `sleepTimer`.
- `core/player/src/main/kotlin/.../core/player/PlayerController.kt` — injects the controller, mirrors
  its state into `PlayerState.sleepTimer`, exposes `setSleepTimer` / `cancelSleepTimer` /
  `extendSleepTimer`.
- `core/player/src/main/kotlin/.../core/player/PlaybackService.kt` — three lines: inject, `attach`,
  `stop` (see handoff; already applied here).
- `core/data/src/main/kotlin/.../data/settings/UserSettings.kt` — one field: `sleepTimer`.
- `core/data/src/main/kotlin/.../data/settings/SettingsRepository.kt` — maps it back in, plus
  `setSleepTimer(...)` and three preference keys.
- `app/src/main/kotlin/.../feature/player/SleepTimerSheet.kt` — **new**: `SleepTimerChip`,
  `SleepTimerOptions` (the sheet's content), `SleepTimerSheet` (the `ModalBottomSheet`), previews.
- `app/src/main/kotlin/.../feature/player/NowPlayingScreen.kt` — the moon `HeaderButton`, the two
  callbacks on `NowPlayingActions`, the pill under the title.
- `app/src/main/kotlin/.../feature/player/PlayerViewModel.kt` — `setSleepTimer` /
  `cancelSleepTimer` / `extendSleepTimer` passthroughs.
- `app/src/test/kotlin/.../feature/player/SleepTimerSheetTest.kt` — **new**: 11 Compose tests.
- `handoff/sleep-timer.md` — **new**: strings + translations, `DowntifyApp.kt` wiring.

## Evidence that the APIs I call exist

Everything below was grepped in this worktree; `file:line` is where it is defined.

- `SettingsRepository`, `@Singleton @Inject constructor`, `val settings: Flow<UserSettings>` →
  `core/data/.../settings/SettingsRepository.kt:24,25`; `store.edit { }` writes as in
  `setReadAhead` (`:117`), `prefs.remove(...)` as in `setLibrarySort` (`:94`).
- `@ApplicationScope` qualifier → `core/data/.../di/ApplicationScope.kt:8`; its scope is
  `Dispatchers.Default` → `core/data/.../di/DataModule.kt:45` (so the timer's player reads run on
  the **service's** scope instead, see below).
- The service's own scope, which is the player's thread:
  `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` →
  `core/player/.../PlaybackService.kt:109`.
- `@Singleton` + `fun start(player: Player, scope: CoroutineScope)` / `fun stop()` / `Player.Listener`
  / `events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, …)` — the pattern copied from
  `ReadAheadBuffer` → `core/player/.../ReadAheadBuffer.kt:48,90,107,124`.
- `override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int)` → the same override in
  `core/player/.../PlaybackService.kt:284`; `Player.MEDIA_ITEM_TRANSITION_REASON_AUTO/…_REPEAT` are
  Media3's `Player` constants (see assumptions).
- `Player.EVENT_TIMELINE_CHANGED`, `EVENT_MEDIA_ITEM_TRANSITION`, `EVENT_IS_PLAYING_CHANGED`,
  `EVENT_PLAYBACK_STATE_CHANGED` → `ReadAheadBuffer.kt:121-129`, `PlayerController.kt:429-432`.
  `C.TIME_UNSET` → `PlaybackService.kt:389`.
- `PlayerState(track, …, error)` data class → `core/player/.../PlayerState.kt:49`; my field at `:78`.
- `PlayerController` injecting singletons, `refresh()`, `mutableState.update { }` →
  `core/player/.../PlayerController.kt:61,339,345`.
- `formatDuration(seconds: Double)` → `core/model/.../Formatting.kt:7`.
- `Tag(label, modifier, icon: Int?, tone, small)` and `TagTone.OnColor` →
  `core/designsystem/.../component/Tag.kt:37,30`; `DowntifyIcons.DarkMode` (the moon drawable,
  `ic_dark_mode.xml`) → `.../component/DowntifyIcons.kt:77`; `@PhonePreviews` → `.../PhonePreviews.kt:16`.
- `ListStyle.Segmented` and `shapeOf(index, count)` → `core/designsystem/.../theme/Shape.kt:97,89`;
  `Spacing.screen/sm/md/lg/xl` → `Shape.kt:151-162`.
- `SegmentedListItem` + `ListItemShapes(...)` + `ListItemDefaults.segmentedColors(...)` + a trailing
  `RadioButton` inside `Column(Modifier.selectableGroup())` — copied from the library's sort sheet →
  `app/.../feature/library/LibraryScreen.kt:358-369`; `ModalBottomSheet(onDismissRequest, containerColor
  = scheme.surfaceContainerHigh)` → `LibraryScreen.kt:347`; `FilterChip` → `SearchScreen.kt:226`.
- `minutes_short` (`%d\u00A0min`) already exists → `app/src/main/res/values/strings.xml:115`, used at
  `CollectionScreen.kt:344` — the duration chips reuse it, so no new duration key.
- `HeaderButton` (56 × 48, `FilledTonalIconButton`, `IconButtonShapes`) →
  `NowPlayingScreen.kt:307`; the existing lyrics/queue pair is its caller at `:280-306`.

## Assumptions I could not verify

1. **No compiler, no test run.** This sandbox has no Android SDK; nothing here was compiled or
   executed. Every symbol above was grepped, but overload resolution, import ordering and the
   Compose layout are unverified. Expect a possible fix-up round in CI.
2. `Player.MEDIA_ITEM_TRANSITION_REASON_AUTO` / `..._REPEAT` names are not used anywhere else in the
   repo (only `DISCONTINUITY_REASON_AUTO_TRANSITION` is, `EpisodeProgressTracker.kt:64`). They are
   Media3's `Player` constants (`REPEAT = 0`, `AUTO = 1`); no media3 artifacts exist in this sandbox
   to grep.
3. `player.volume` (`Player.getVolume`/`setVolume`) is not used elsewhere in the repo. It is core
   Media3 API, and it is the right knob: `NormalizationAudioProcessor` changes gain separately, in
   the audio pipeline, so the fade does not fight it.
4. **`setPauseAtEndOfMediaItems` finding — not used, deliberately.** It is a player-wide flag
   (there is no per-item equivalent in Media3 `1.11.1`), so arming it for one song would also pause
   at the end of *every* item afterwards; it is not part of any persisted state, so
   `PlaybackPersistence` would restore a queue that then stops after its first song, and
   `PlaybackService.removeDeletedLibraryTracks`/`addToQueue` would keep a player that silently stops.
   Instead: `onMediaItemTransition` with `REASON_AUTO/REPEAT` pauses immediately, and because the last
   5 s of that item were already faded to zero, the next item is never audible. The one gap: an item
   with **no known length** (a live-looking stream) can't fade and so can blip for a few ms.
5. The `<plurals>`/translation values in `handoff/sleep-timer.md` are my own wording, not the web
   app's terms; the parent should keep `%1$s` and may reword.
6. `SleepTimerOptions` is tested directly; `SleepTimerSheet` (the `ModalBottomSheet` wrapper) is not
   — it is a dialog window under Robolectric, and the repo tests stateless screens, not dialogs.
7. A timer's *remaining* is persisted, not an absolute instant: after a restart nothing is counted
   for the time the service was gone (a wall-clock deadline would expire the timer while the phone
   sat idle). An end-of-item timer is kept as its mode only and re-reads the item's position on the
   first player reading.
8. `MAX_STEP_MS` (30 s) caps how much one reading may count, so a stalled process can't burn a
   timer in one jump; a ticker starved for longer than that under-counts by the difference.

## What the parent must do (handoff items, DI, routes, strings)

`handoff/sleep-timer.md` has every snippet with its anchor line.

- **Strings** — 5 new `sleep_` keys in `values/strings.xml` after
  `<string name="player_source_this_phone">…`, plus the same block in the 7 translations (table with
  suggested values included). No plural needed: `%1$s left` has no count in it. Duration chips reuse
  `minutes_short`.
- **`ui/DowntifyApp.kt`** — 5 small edits (one import, `sleepTimerOpen` state, reset on collapse, two
  `NowPlayingActions` callbacks, the `SleepTimerSheet` call at the end of `NowPlayingOverlay`). Until
  applied the branch compiles and the moon button does nothing.
- **`PlaybackService.kt`** — the 3-line wiring is **already applied** in this branch; the snippet is
  in the handoff for reference only.
- **DI / gradle / manifest / routes** — nothing. `SleepTimerController` is constructor-injected
  (`SettingsRepository` + `@ApplicationScope`), the service and the UI share the process, and there
  is no new screen.

## Test coverage added

- `core/model/src/test/.../SleepTimerTest.kt` (JUnit, fake clock, no coroutines):
  a timer of minutes counts playback time only (a pause adds nothing) · the last 5 s fade and then
  it is out of time (volume 0.6 → 0) · an end-of-track timer follows the song and expires at its end
  · a paused song burns no time · seeking back gives an end-of-item timer its time back and leaves
  the fade · an item with no known length has no countdown and never expires · extending a timer of
  minutes keeps its choice · extending an end-of-item timer carries on as minutes · a kept timer
  comes back with what was left (and counts nothing for the time away) · a kept end-of-item timer
  re-reads the item · nothing is counted or saved without a timer · a long gap counts at most
  `MAX_STEP_MS` · minutes are clamped to 1..600.
- `app/src/test/.../feature/player/SleepTimerSheetTest.kt` (Robolectric Compose, 411 × 914 dp):
  every preset and both end-of choices are shown · a chip reports `AfterMinutes(45)` · the running
  duration is the selected chip (and another is not) · "End of episode" reports its mode · a running
  timer turns off from the sheet and the cancel row only exists while one runs · the pill counts down
  (`4:59 left`) · tapping the pill cancels · an end-of-item pill with no length says what it waits
  for · no pill without a timer · on `NowPlayingScreen` the moon opens the sheet and the pill cancels.

## Known gaps

- No "5 more minutes" control in the UI: `extend` exists in the model, controller and ViewModel but
  the sheet only replaces the timer. Deliberate — the spec's sheet is durations + end of item.
- The pill's `12:34` is the remaining time; there is no "ends at 23:40" line.
- A device that is killed mid-fade comes back with the timer still armed (the volume is restored by
  the player being gone) — the countdown resumes rather than continuing to fade.
- The moon reuses `DowntifyIcons.DarkMode` instead of a new `ic_bedtime.xml`: same Material Symbols
  Rounded moon, and no new drawable to merge in `:core:designsystem`.
- Not verified on a device: the fade, the transition pause and the service-restart restore all need
  an emulator plus real audio (see the assumptions).
