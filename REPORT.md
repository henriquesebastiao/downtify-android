# playlists

## What it does (user-visible, and where it appears in the UI)

Server-side playlist editing existed already (`capabilities.playlist_editing`); this slice is the
client half, plus the multi-select the app was missing.

- **Library → Playlists tab → the *New playlist* button** (a `+` in the toolbar, only when the paired
  server advertises `playlist_editing`). It asks for a name, then opens the new, empty playlist.
- **A playlist page (`PlaylistRoute` → `CollectionScreen`) → its `⋮` menu**:
  - *Rename* — only on a manual playlist the user owns (`mine`), never for an editor, never on
    Liked songs or an album.
  - *Add songs* — a sheet of the whole library with a check per song, then "Add N songs to playlist"
    (`POST /api/v1/playlists/tracks` with `add`).
  - *Edit songs* — edit mode: a drag handle per row (press and hold, drag; the order is sent whole to
    `PUT /api/v1/playlists/order`, repeats included) and swipe-left to take a single row out
    (`remove_entries` with the row's zero-based occurrence, so the other copies of that song stay).
    *Done* leaves edit mode.
  - A refused write says why under the header: 403 a permission ("you can change songs, not the
    name"), 400 the 10,000-song limit, 503 the sync index still warming up, 409 a playlist that
    changed underneath (the reorder is reloaded and retried once first), offline, or the server
    having no write routes at all.
- **Library → Songs: press and hold a song** starts a selection: the top bar becomes a contextual
  one ("2 selected", close, select all / clear) and a bar under the list offers
  "Add 2 songs to playlist". Taps then pick or unpick instead of playing; the swipe-to-queue is off
  while selecting. Picks leave the selection when songs leave the library.
- **Any song's `⋮` menu → *Add to playlist*** opens the same sheet (manual playlists the user may
  edit, with *New playlist* at the top, which creates the playlist and puts the song in it).
- Podcast episodes are refused before a request is made (`EpisodeIds.isEpisode`), hidden songs are
  simply absent from the library the app draws, and everything above disappears when
  `capabilities.playlist_editing` is false — an older server stays read-only instead of answering 403.
- Another device's change arrives as `playlists_changed` on the WebSocket: the app refetches the
  playlists into both the database copy and the editable list.

## Files changed

Shared-file diffs the parent must know about are marked **(shared-ish)**; none of them is on
PLAN.md §4's forbidden list, and none is owned by another slice.

Network / model
- `core/network/.../dto/PlaylistEdits.kt` (new) — the four request bodies + `PlaylistWriteDto`, mapped to `PlaylistEditResult`.
- `core/network/.../dto/Dtos.kt` — `CapabilitiesDto.playlist_editing`; `PlaylistDto` gains `manual`, `can_edit_tracks`, `mine`, `owner`.
- `core/network/.../DowntifyApi.kt` — `createPlaylist`, `renamePlaylist`, `editPlaylistTracks`, `reorderPlaylist`.
- `core/network/.../live/LiveUpdatesClient.kt` **(shared-ish)** — `LiveEvent.PlaylistsChanged` + parsing `playlists_changed`.
- `core/model/.../ServerInfo.kt` **(shared-ish)** — `Capabilities.playlistEditing = false`.
- `core/model/.../Collections.kt` **(shared-ish)** — `Playlist` gains `manual`, `canEditTracks`, `mine`, `owner`, `isEditable()`, `isRenamable()`.
- `core/model/.../Selection.kt` (new) — immutable `Selection` (toggle/select/deselect/clear/retain/covers/selectAll) and `SelectionController`.
- `core/model/.../PlaylistEditing.kt` (new) — `PlaylistEditResult`, `playlistTrackIds` (drops episodes/blank/dupes), `addablePlaylists`, `List.moved()`.

Data
- `core/data/.../playlists/PlaylistEditorRepository.kt` (new) — the four writes, the playlists as the server lists them, the capability flag, 409 retry, status→`PlaylistEditError`.
- `core/data/.../library/LibraryRepository.kt` **(shared-ish)** — public `refreshPlaylists()` (the private per-api one already existed).
- `core/data/.../sync/SyncCoordinator.kt` **(shared-ish)** — new `playlists` constructor parameter; refreshes playlists on connect and on `PlaylistsChanged`.

Design system
- `core/designsystem/.../component/SelectionChrome.kt` (new) — `SelectionTopBar`, `SelectionBottomBar`, `SelectionCheckbox` (strings are parameters, no screen's words in here).
- `core/designsystem/.../component/DowntifyIcons.kt` + `res/drawable/ic_edit.xml` (new) — `DowntifyIcons.Edit` for Rename.

App
- `app/.../ui/common/PlaylistDialogs.kt` (new) — `PlaylistNameDialog` (new + rename).
- `app/.../ui/common/AddToPlaylistSheet.kt` (new) — `AddToPlaylistSheet` + testable `AddToPlaylistContent`.
- `app/.../ui/common/AddSongsSheet.kt` (new) — song picker for a playlist page: `AddSongsSheet` + `AddSongsContent`.
- `app/.../ui/common/PlaylistErrors.kt` (new) — `PlaylistEditError` → user text.
- `app/.../ui/common/LibraryItems.kt` — `TrackItem(selectionMode, selected, onLongClick, longClickLabel)`; `TrackActions.onAddToPlaylist` and its menu item.
- `app/.../ui/common/SwipeActions.kt` — `SwipeToRemove(label = …)` so a playlist row doesn't say "remove from queue".
- `app/.../feature/library/LibraryViewModel.kt` — selection state, sheet/dialog state, `createPlaylist`, `addToPlaylist`, `createWithTracks`, playlist capability + editable list, `openPlaylist` after a create.
- `app/.../feature/library/LibraryScreen.kt` — contextual top bar + selection bar, long press to select, `New playlist` on the Playlists tab, the sheet/dialogs, the error line, `LibraryEditActions`.
- `app/.../feature/collection/CollectionViewModel.kt` — `canEditTracks`/`canRename`, edit mode, optimistic `order`, `removeAt(occurrence)`, `move`, `rename`, `addSongs`, rollback with a `revision` that rebuilds rows, renamed-name follow-up.
- `app/.../feature/collection/CollectionScreen.kt` — overflow menu (Rename/Add songs/Edit songs), Done, `EditableTrackRow` (drag + swipe), per-occurrence row keys, rename dialog, song sheet, error line, `CollectionEditActions`.

Tests
- `core/model/.../SelectionTest.kt`, `core/model/.../PlaylistEditingTest.kt`
- `core/data/.../playlists/PlaylistEditorRepositoryTest.kt`
- `core/data/.../sync/SyncCoordinatorTest.kt` — constructor updated for the new parameter.
- `app/src/test/.../ui/common/PlaylistSheetsTest.kt`, `app/src/test/.../ui/components/SelectionChromeTest.kt`,
  `app/src/test/.../feature/library/LibrarySelectionTest.kt`, `app/src/test/.../feature/collection/CollectionEditTest.kt`

Handoff: `handoff/playlists.md` (26 strings/plurals, and an optional Search wiring note).

## Evidence that the APIs I call exist

- `DowntifyApi` is a Retrofit interface with `@GET("api/v1/playlists") suspend fun playlists()` — `core/network/.../DowntifyApi.kt:71`; new endpoints at `:79`, `:82`, `:85`, `:88`.
- `ApiFactory.create(baseUrl): DowntifyApi` — `core/network/.../ApiFactory.kt:28`.
- `SessionStore.current: Session?` — `core/network/.../session/SessionStore.kt:53`.
- `ServerRepository.serverInfo: StateFlow<ServerInfo?>` — `core/data/.../session/ServerRepository.kt:70`.
- `Capabilities` (add a trailing `playlistEditing`) — `core/model/.../ServerInfo.kt:31`, flag at `:45`.
- `Playlist` model — `core/model/.../Collections.kt:28`.
- `EpisodeIds.isEpisode` — `core/model/.../Podcasts.kt:80`.
- `LibraryRepository.playlists` — `core/data/.../library/LibraryRepository.kt:64`; private `refreshPlaylists(api)` — `:191`; new public `refreshPlaylists()` — `:120`; `refreshLikes()` used as the pattern — `:112`.
- `ServerResult` / `callServer` (WebApi-only, which is why the writes have their own mapping) — `core/data/.../ServerResult.kt:12`, `:26`.
- `LibrarySnapshot.tracks` / `tracks(ids)` — `core/data/.../library/LibrarySnapshot.kt:10`, `:17`.
- `PlayerController.nowPlaying` / `play` / `addToQueue` — `core/player/.../PlayerController.kt:90`, `:159`, `:267`.
- `OfflineRepository.state` / `setKept` — `core/data/.../offline/OfflineRepository.kt:113`, `:182`.
- `SyncCoordinator` WebSocket `when (event)` — `core/data/.../sync/SyncCoordinator.kt:108-127`; new parameter at `:51`.
- `LiveEvent` sealed interface — `core/network/.../live/LiveUpdatesClient.kt:20`.
- `MediaListItem(..., onLongClick, onLongClickLabel, trailing, …)` — `core/designsystem/.../MediaListItem.kt:47`.
- `CircleButton(icon, contentDescription, onClick, modifier, enabled, containerColor, contentColor)` — `core/designsystem/.../PageHeader.kt:90`.
- `IconAvatar(icon, modifier, container, content, size)` — `core/designsystem/.../Containers.kt:130`.
- `EmptyState(...)` — `core/designsystem/.../Components.kt:78`; `@PhonePreviews` — `core/designsystem/.../PhonePreviews.kt:16`.
- `SwipeToRemove` — `app/.../ui/common/SwipeActions.kt:59`; `songsCount` — `app/.../ui/common/LibraryItems.kt:427`.
- `Spacing` (xxs…xxl, screen) — `core/designsystem/.../theme/Shape.kt:151`; `DowntifyShape.Section` — `:39`; `motionScheme`/`morphShape` already used by `TrackItem`.
- `pluralStringResource` is `androidx.compose.ui.res.pluralStringResource`, already used at `ui/common/LibraryItems.kt:427`.
- Server side: the four routes and the capability exist — `downtify/mobile_routes.py` (`/playlists`, `/playlists/rename`, `/playlists/tracks`, `/playlists/order`) and `downtify/auth_routes.py:164` (`capabilities['playlist_editing'] = True`), documented in `docs/mobile-client-contract.md` §4 "Editing playlists".

## Assumptions I could not verify

- **No compiler was run** (PLAN.md §0). Everything below is unverified by execution.
- The drag-to-reorder in `EditableTrackRow` is hand-rolled on `detectDragGesturesAfterLongPress` +
  `graphicsLayer` (the queue screen's proven `QueueReorder` is private to `feature/player`, which is
  another slice's file, so it could not be reused). It assumes a long-press on the 48 dp handle keeps its pointer input across the
  list's re-ordering of keyed rows (`rememberUpdatedState(index)` + keys built from
  `id#occurrence`, so a row keeps its identity while it moves). If it misbehaves, the fallback is
  the same `onMove(from, to)` callback driven by up/down buttons — the ViewModel contract doesn't
  change.
- `SwipeToRemove` is `resetAfter = false`: a row that is removed optimistically and put back by a
  refusal is rebuilt because the row key includes `CollectionUiState.revision`, which a rollback
  increments. If Compose keeps the dismissed state anyway, the returned row may look swiped.
- Robolectric: `AlertDialog`, `DropdownMenu` and `ModalBottomSheet` popups are exercised by
  `PlaylistSheetsTest`/`CollectionEditTest` (dialog and menu) but the *modal sheets* are tested
  through their content composables (`AddToPlaylistContent`, `AddSongsContent`), because a modal
  sheet's window is not reliably reachable from `createComposeRule` under Robolectric.
- `POST /api/v1/playlists` answers `{name, manual, count, track_ids}` per the contract; I read
  `name` back rather than assuming the trimmed input survives (the server may slugify it).
- A reorder `409` retry re-sends the *user's* order only when the reloaded playlist holds the same
  multiset of ids (`sorted()` compare of `String`s); a renamed playlist or one whose songs changed
  reports `Stale` instead of guessing.
- Renaming a playlist changes the route argument, so the page keeps the server's new name in
  `CollectionEdit.renamedTo`. Offline-kept playlists are keyed by name elsewhere
  (`OfflineRepository` uses `refId`), so a renamed playlist loses its "kept on the phone" mark —
  a known gap, see below.
- `PlaylistDto.mine` defaults to `true` and `manual`/`can_edit_tracks` to `false`, so a row from the
  app's own database (which has no such columns) can never enable an edit affordance.

## What the parent must do (handoff items, DI, routes, strings)

See `handoff/playlists.md`:

1. `app/src/main/res/values/strings.xml` — 24 strings + 2 plurals (`pl_` prefix), with the anchor and
   the exact block; the seven translations are yours.
2. Optional: the Search-results wiring is described there but **not done**, because
   `feature/search/*` is the `search-lyrics` slice's per PLAN.md §3.
3. Nothing else: no new route (PlaylistRoute exists), no DI module (`PlaylistEditorRepository` is an
   `@Inject` `@Singleton`, as `CatalogRepository` is), no manifest, no Gradle change.

## Test coverage added

- `core/model/.../SelectionTest.kt` — pick order, toggle off, idempotent select, deselect of a
  stranger, `selectAll`/`covers` (including the empty view), `retain` after a sync, `clear` on empty,
  and `SelectionController` publishing every change.
- `core/model/.../PlaylistEditingTest.kt` — episodes/blank/dupes dropped by `playlistTrackIds`;
  `addablePlaylists` keeps only manual+editable, not liked; an editor may edit but not rename; an
  older server keeps a playlist read-only; an imported playlist is not editable; `moved` (both ways,
  no-op, out of range) and a repeated song moving by occurrence.
- `core/data/.../PlaylistEditorRepositoryTest.kt` — a fake `DowntifyApi` behind a mocked
  `ApiFactory`/`SessionStore`, with the trimming of a create, the `403`/`400`/`503` messages, the
  episodes-before-the-write filter, `NoTracks` for nothing to send, the whole order going out with
  repeats, the `409` reload-and-retry-once (and the refusal when the songs changed), and the listed
  playlists carrying `manual`/`can_edit_tracks`.
- `app/.../ui/common/PlaylistSheetsTest.kt` — the sheet lists "New playlist" + the editable playlists
  and answers with a name; the song picker needs a pick, un-picks on a second tap and sends the ids;
  the name dialog stays disabled until a name, trims it, and sends it.
- `app/.../ui/components/SelectionChromeTest.kt` — the top bar's count and both buttons, the
  "everything picked" label, the bottom bar running its one action and doing nothing when disabled.
- `app/.../feature/library/LibrarySelectionTest.kt` — long press starts a selection with that song, a
  tap while selecting calls the toggle and never plays, a tap without a pick still plays, the bar's
  count and its action, and *New playlist* shown only on the Playlists tab with the capability on.
- `app/.../feature/collection/CollectionEditTest.kt` — the menu's Rename and Add songs, an editor
  seeing Add songs but not Rename, a read-only playlist having no menu, edit mode showing one handle
  per row and Done, and the 403 message being dismissible.

## Known gaps

- **Reorder waits for the drag to settle** (350 ms after the last swap, `REORDER_SETTLE_MS`) and then
  sends the whole order once; `feature/player/QueueScreen.kt` solves the same problem by committing
  on finger-up, but its `QueueReorder` is private to that file and that file is another slice's, so
  this is the ViewModel-side equivalent instead of a shared component.
- **Rename loses the offline keep**: `OfflineRepository` keys a kept collection by its name
  (`refId = playlistName`), so after a rename the playlist is no longer marked kept on the phone.
  Fixing it needs a stable playlist id, which the contract doesn't have.
- **No rename/share for editors** — deliberate (the contract forbids it), and hidden in the UI.
- **Deleting a whole playlist** stays impossible: the mobile API has no such route.
- **The Playlists tab still draws from the database**, so `manual`/`owner` are not shown there (no
  "shared by" label, no editor badge). The playlist page shows the same rows from the server's list.
- **Search results have no selection** — see the handoff note; the songs under the Search tab are
  library tracks and would work, but that file belongs to another slice.
- Selection is not offered for albums/artists/playlists tabs (songs only), and the playlist page has
  no multi-select of its own rows (drag and swipe cover the edits it allows).
- No snackbar/undo after "Add N songs": the sheet closes and the count in the Playlists tab follows
  the refetch; a failure shows the error line instead.
