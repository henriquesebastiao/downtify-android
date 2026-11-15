# Handoff — slice `playlists` (parent applies these)

Everything below is a change to a file this slice does **not** own. Nothing else is needed: no new
route, no new DI module, no manifest permission, no Gradle change.

---

## 1. `app/src/main/res/values/strings.xml` — 24 strings + 2 plurals

Insertion point: the last line of the file, before the closing tag.

Anchor line (exists today, the file's final `<string>`):

```xml
    <string name="settings_client_certificate_remove">Remove</string>
</resources>
```

Insert between them (one blank line before the block, as the file already separates sections):

```xml
    <!-- Playlists: creating, renaming, editing, and picking songs for one (slice `playlists`). -->
    <string name="pl_new">New playlist</string>
    <string name="pl_name_label">Playlist name</string>
    <string name="pl_create">Create</string>
    <string name="pl_rename">Rename</string>
    <string name="pl_add_songs">Add songs</string>
    <string name="pl_add_to_playlist">Add to playlist</string>
    <string name="pl_edit_songs">Edit songs</string>
    <string name="pl_done">Done</string>
    <string name="pl_more">Playlist options</string>
    <string name="pl_select_song">Select song</string>
    <string name="pl_select_all">Select all</string>
    <string name="pl_clear_selection">Clear selection</string>
    <string name="pl_close_selection">Close selection</string>
    <string name="pl_reorder_handle">Move song</string>
    <string name="pl_remove_from_playlist">Remove from playlist</string>
    <string name="pl_edit_hint">Press and hold a row’s handle to move a song, or swipe it to take it out.</string>
    <string name="pl_error_unsupported">This server can’t edit playlists. Updating it lets you add songs here.</string>
    <string name="pl_error_forbidden">You can change this playlist’s songs, but not its name.</string>
    <string name="pl_error_limit">A playlist holds at most 10,000 songs.</string>
    <string name="pl_error_index">The server is still indexing the library. Try again in a moment.</string>
    <string name="pl_error_not_found">That playlist or song is no longer on the server.</string>
    <string name="pl_error_stale">This playlist changed in the meantime. Reopen it and try again.</string>
    <string name="pl_error_offline">Could not reach the server.</string>
    <string name="pl_error_generic">Could not change the playlist.</string>
    <plurals name="pl_add_n_to_playlist">
        <item quantity="one">Add %1$d song to playlist</item>
        <item quantity="other">Add %1$d songs to playlist</item>
    </plurals>
    <plurals name="pl_selected_count">
        <item quantity="one">%1$d selected</item>
        <item quantity="other">%1$d selected</item>
    </plurals>
```

Both plurals need `one` + `other`, plus `many` for `es`, `fr` and `pt-rBR` (CLAUDE.md's rule); the
seven translations (`values-bg|el|es|fr|hu|pt-rBR|tr`) are the parent's. Note the two typographic
apostrophes (`’`) — ktlint/lint don't care, but keep them unescaped vs. `\’` as the file already does.

Keys referenced from code, all of them declared above:

```
pl_add_n_to_playlist  pl_add_songs  pl_add_to_playlist  pl_clear_selection  pl_close_selection
pl_create  pl_done  pl_edit_hint  pl_edit_songs  pl_error_forbidden  pl_error_generic
pl_error_index  pl_error_limit  pl_error_not_found  pl_error_offline  pl_error_stale
pl_error_unsupported  pl_more  pl_name_label  pl_new  pl_remove_from_playlist  pl_rename
pl_reorder_handle  pl_select_all  pl_select_song  pl_selected_count
```

---

## 2. `app/src/main/kotlin/.../feature/search/SearchScreen.kt` — optional, another slice owns it

This slice wires multi-select into **Library** only. `feature/search/*` belongs to the
`search-lyrics` worker, so editing it here would be a merge conflict by contract (PLAN.md §3). If
you want Search results to offer the same selection, the shape is:

- In `SearchRoute` (line 84), add to the `TrackActions` passed to `SearchScreen` (line 124):
  `onAddToPlaylist = { viewModel.openAddSheetFor(it) }` — a ViewModel that calls
  `PlaylistEditorRepository.addTracks(name, listOf(track.id))`.
- In `SearchScreen`'s `results` section, the songs are `itemsIndexed(results.songs, key = { _, t ->
  "song:${t.id}" })` rendering `TrackItem(...)` (line 323): give that `TrackItem` the same three
  parameters Library's `Songs()` uses — `selectionMode`, `selected` and `onLongClick` — and wrap the
  screen in the contextual `SelectionTopBar` / `SelectionBottomBar` (both in
  `:core:designsystem/component/SelectionChrome.kt`).

The songs under the Search tab *are* library `Track`s (the server's results, `RemoteSong`, carry no
v1 track id and can't be added), so only that list is a candidate.

---

## 3. Not needed (checked, so you don't look for it)

- **Routes.kt** — `PlaylistRoute(name)` already exists; a new playlist opens through it. Albums and
  Liked songs reach the same screen through `AlbumRoute`/`LikedRoute`.
- **DI** — `PlaylistEditorRepository` is a `@Singleton` with an `@Inject` constructor in
  `:core:data`, like `CatalogRepository`; Hilt needs no module. It is also injected by
  `SyncCoordinator`, whose constructor gained that parameter (already done in this branch).
- **DowntifyApp.kt / AndroidManifest / gradle** — untouched, nothing new needed.
- **`playlist_features.xml`** — the existing favorite strings stay there; the new ones go in
  `strings.xml` (above) as PLAN.md §4 asks.
