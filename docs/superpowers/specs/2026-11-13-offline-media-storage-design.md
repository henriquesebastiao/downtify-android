# Offline media storage

## Intent

Make saved offline music usable by other music players, including PowerAMP, without root or broad storage permissions. New downloads must use `Android/media/com.henriquesebastiao.downtify/` by default, and the user must be able to choose another folder in Settings. Downloaded playlists must have portable playlist files and paths that continue to work.

## Current state

`OfflineStorage` writes ID-named files to internal `files/offline`. `OfflineRepository` records only the file name and byte count in Room. Media3 and loudness normalization consume `java.io.File`. There is no M3U export. The minimum Android version is 26.

## Goals

- Default to the app-specific external media directory returned by `Context.getExternalMediaDirs()`.
- Offer Android's system folder picker for a custom location and persist its tree URI permission.
- Store audio in a readable `Music/Artist/Album/Track` hierarchy with stable, collision-resistant names.
- Export kept server playlists as UTF-8 M3U files under `Playlists/`, using relative paths to downloaded audio.
- Preserve offline playback, deletion, storage accounting, partial downloads and loudness normalization for both location types.
- Move existing internal downloads and files from a previous selected location instead of discarding them.
- Restrict cleanup to Downtify-managed files; never sweep a user's selected folder.
- Register default-location media changes with Android's media scanner.

## Non-goals

- No `MANAGE_EXTERNAL_STORAGE`, root access or direct writes to arbitrary paths denied by Android.
- No export of albums as M3U playlists and no synchronization of playlist changes made by other apps.
- No migration of streaming/read-ahead caches.

## Approaches

1. **External media directory only.** Simple `File` I/O, but does not satisfy user-selected folders.
2. **SAF only.** One URI-based backend works in theory for the default location too, but the user would have to grant the default folder manually and direct file playback would be lost.
3. **Hybrid storage backend (selected).** Direct `File` I/O in the default app-specific media directory and `DocumentFile`/content URIs for a custom SAF tree. Both expose a common offline-audio handle to playback and normalization. This has more implementation work but is the only approach that meets both requirements without broad permissions.

## Design

`OfflineStorage` owns a default file backend and a SAF backend. The selected location is stored in DataStore. A custom tree gets a `Downtify` child so application cleanup cannot affect unrelated user files. The default layout is:

```
Android/media/com.henriquesebastiao.downtify/
  Music/<artist>/<album>/<track number> - <title> [<stable id>].<ext>
  Playlists/<playlist name>.m3u
```

A custom folder uses the same layout below `Downtify/`. Unsafe path characters are replaced and long components are shortened with a stable hash. A short stable track-ID suffix prevents duplicate titles from overwriting each other.

Storage operations return URI-based audio handles with byte and modification metadata. Media3 opens file or content URIs directly. Loudness hashing opens either URI through `ContentResolver`. The downloader writes to an output-stream abstraction so resume, commit and health checks work in both backends.

Playlist export runs after offline state reconciliation and after completed downloads. Only present, usable audio files are included. Entries contain `#EXTINF` metadata and `../Music/...` relative links. Playlist files for removed collections are deleted. Duplicate sanitized playlist names receive stable suffixes.

On startup, existing internal offline files are imported into the selected backend and their Room paths updated. On a location change, active unique download work is cancelled, the managed tree is moved, the new location is saved, and normal reconciliation resumes downloads. Failed moves leave the previous setting active and report an error in Settings.

Settings shows the current default path or selected folder, a Choose folder action, and a Reset to default action for a custom location. Android's `ActivityResultContracts.OpenDocumentTree` grants persistable read/write access. No new storage permission is added.

## Verification

- Unit tests for safe readable paths, duplicate handling, M3U metadata and relative paths.
- Robolectric tests for default external-media storage, SAF downloads/playback URIs, migration and restricted cleanup.
- Settings persistence tests for the custom tree URI and reset behavior.
- Existing offline, loudness and player test suites.
- Debug build and static formatting/analysis; exercise the folder-picker contract in an Android runtime when available.

## Assumptions

- Server playlists marked for offline keeping are the playlists to export; Liked songs is exported as `Liked Songs.m3u`.
- Other players are expected to scan the selected/default folder themselves; media scanning is triggered explicitly for the default location.
- Moving an existing library may temporarily pause downloads, but must not remove its audio or playlist entries.
