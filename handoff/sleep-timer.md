# Sleep timer — handoff for the parent

Two things in this branch touch files the parent owns:

- **`PlaybackService.kt`** — already **APPLIED** in `feat/sleep-timer` (3 lines). Skip it if you
  merge this branch; apply the snippet only if you rebuild that file from `main`.
- **`DowntifyApp.kt`** and **`app/src/main/res/values*/strings.xml`** — **NOT touched**: apply the
  snippets below. Until you do, the branch compiles and works but nothing opens the sheet (the moon
  button is there with an empty callback).

No DI module, gradle, manifest or `Routes.kt` change is needed: `SleepTimerController` is a
`@Singleton` with an `@Inject` constructor whose only dependencies (`SettingsRepository`,
`@ApplicationScope CoroutineScope`) are already bound, and it is read/written in the app's own
process (the service declares no `android:process`).

---

## 1. `app/src/main/res/values/strings.xml` — and the 7 translations

### 1a. English

Insert **after** this exact anchor line (in the `<!-- Player -->` block):

```xml
    <string name="player_source_this_phone">Playing from this phone</string>
```

Insert:

```xml
    <!-- Sleep timer -->
    <!-- What is left of a running timer: "12:34 left". -->
    <string name="sleep_chip_remaining">%1$s left</string>
    <string name="sleep_timer">Sleep timer</string>
    <string name="sleep_end_of_track">End of track</string>
    <string name="sleep_end_of_episode">End of episode</string>
    <string name="sleep_cancel">Cancel sleep timer</string>
```

The duration chips reuse the existing `minutes_short` (`%d\u00A0min`) — no new key for them.

| key | English | where it shows |
|---|---|---|
| `sleep_timer` | Sleep timer | the moon button's description on Now Playing, and the sheet's title |
| `sleep_chip_remaining` | %1$s left | the countdown pill under the title, e.g. `12:34 left` |
| `sleep_end_of_track` | End of track | the sheet's first end-of choice; the pill when the song has no known length |
| `sleep_end_of_episode` | End of episode | the sheet's second end-of choice, likewise for an episode |
| `sleep_cancel` | Cancel sleep timer | the sheet's cancel row, and the pill's click label |

### 1b. The seven translations

Same insertion point in each file (`values-bg`, `values-el`, `values-es`, `values-fr`, `values-hu`,
`values-pt-rBR`, `values-tr`): after that locale's `player_source_this_phone` line, in the Player
block. If a locale lacks that key, put the block after its last `player_*` string instead.

| key | bg | el | es | fr | hu | pt-rBR | tr |
|---|---|---|---|---|---|---|---|
| `sleep_chip_remaining` | `%1$s до края` | `απομένουν %1$s` | `quedan %1$s` | `%1$s restantes` | `%1$s van hátra` | `faltam %1$s` | `%1$s kaldı` |
| `sleep_timer` | Спи таймер | Χρονόμετρο ύπνου | Temporizador | Minuteur | Elalvási időzítő | Temporizador | Uyku zamanlayıcısı |
| `sleep_end_of_track` | Край на песента | Τέλος κομματιού | Fin de la canción | Fin du morceau | A dal vége | Fim da música | Parçanın sonu |
| `sleep_end_of_episode` | Край на епизода | Τέλος επεισοδίου | Fin del episodio | Fin de l'épisode | Az epizód vége | Fim do episódio | Bölümün sonu |
| `sleep_cancel` | Спри таймера | Ακύρωση χρονομέτρου | Cancelar temporizador | Annuler le minuteur | Időzítő kikapcsolása | Cancelar temporizador | Zamanlayıcıyı iptal et |

(Reword freely — they are values I could not check against the web app's locales. Keep
`sleep_chip_remaining` as a `%1$s` placeholder.)

---

## 2. `app/src/main/kotlin/com/henriquesebastiao/downtify/ui/DowntifyApp.kt`

### 2a. Import — after

```kotlin
import com.henriquesebastiao.downtify.feature.player.QueueScreen
```

```kotlin
import com.henriquesebastiao.downtify.feature.player.SleepTimerSheet
```

### 2b. In `NowPlayingOverlay`, after

```kotlin
    var queueOpen by rememberSaveable { mutableStateOf(false) }
```

```kotlin
    var sleepTimerOpen by rememberSaveable { mutableStateOf(false) }
```

### 2c. In the same composable, in `LaunchedEffect(open)`, after

```kotlin
            queueOpen = false
            lyricsOpen = false
```

```kotlin
            sleepTimerOpen = false
```

### 2d. In the `controls = remember(player, onClose, onNavigate) { NowPlayingActions(...) }` block,
after

```kotlin
            onOpenQueue = { queueOpen = true },
```

```kotlin
            onOpenSleepTimer = { sleepTimerOpen = true },
            onCancelSleepTimer = player::cancelSleepTimer,
```

(`cancelSleepTimer` is on `PlayerViewModel`; `setSleepTimer(SleepTimerMode)` and
`extendSleepTimer(Int)` are there too if you ever want "5 more minutes".)

### 2e. The sheet itself — last in `NowPlayingOverlay`, after the lyrics `AnimatedVisibility` block,
which ends with

```kotlin
        LyricsScreen(
            lyrics = lyrics,
            offsetMs = lyricsOffset,
            state = state,
            controls = controls,
            onBack = { lyricsOpen = false },
        )
    }
```

Insert before that block's closing `}` of the composable (i.e. after the `}` that closes
`AnimatedVisibility`):

```kotlin

    // The sleep timer's choices, in a sheet over Now Playing from its moon button.
    // Its own window takes the back press, so no BackHandler or dragEnabled change is needed.
    if (open && sleepTimerOpen) {
        SleepTimerSheet(
            state = state.sleepTimer,
            onPick = {
                player.setSleepTimer(it)
                sleepTimerOpen = false
            },
            onCancel = {
                player.cancelSleepTimer()
                sleepTimerOpen = false
            },
            onDismiss = { sleepTimerOpen = false },
        )
    }
```

`state.sleepTimer` is the field this slice added to `PlayerState` (default `SleepTimerState.Inactive`),
so no extra collect is needed: the sheet and the pill both read it from the state already collected
there.

---

## 3. `core/player/.../PlaybackService.kt` — APPLIED, informational

Only if you rebuild the file from `main`:

after

```kotlin
    @Inject lateinit var syncCoordinator: SyncCoordinator
```

```kotlin
    @Inject lateinit var sleepTimer: SleepTimerController
```

after

```kotlin
        readAhead.start(player, scope)
```

```kotlin
        sleepTimer.attach(player, scope)
```

after

```kotlin
        readAhead.stop()
```

```kotlin
        sleepTimer.stop()
```
