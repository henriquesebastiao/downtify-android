# Music trash

On a server with the [music trash](https://downtify.henriquesebastiao.com/features/music-trash/) on (`DOWNTIFY_TRASH=true` or `DOWNTIFY_TRASH_CONFIG`), an admin's pairing can send songs to it from the phone and get them back. The server does the work and checks the account on every request; the app only asks. Contract: "Music trash (admins)" in the server's `docs/mobile-client-contract.md`.

## What the app offers

- **Move to trash** in a song's ⋮ menu (Library, albums, playlists, artists). The server checks exactly which files would move — this can take a moment — and the confirmation lists each one (title, format, path) before anything moves. Confirmed songs leave the library at the next sync; the snackbar says they're on their way, not that they're gone.
- **Trash** in the side menu: what is in the trash and until when, with search, **Restore** (one song at a time) and **Try again** for a move or restore that stopped. A second tab lists the songs whose time ran out and that automatic downloads still skip, with **Allow downloads**.

Both appear only while `GET /api/v1/library/trash/capabilities` says `can_manage` for this pairing. An older server (404), a server with the trash off, an account that isn't an admin, or a 403 on any request hides them. A new pairing or account cancels a check or confirmation under way.

## Where it lives

- `:core:model` — `TrashItem`, `TrashState`, `TrashPreview`, `TrashPage`, `TrashCapability`.
- `:core:network` — the endpoints in `TrashApi` (`ApiFactory.createTrash`), DTOs in `dto/TrashDtos.kt`.
- `:core:data` — `LibraryTrashRepository`: the capability per pairing, the check/confirm steps, the trash pages; failures carry the server's `detail.code` (`TrashFailure`).
- `:app` — `feature/trash`: `MoveToTrashHost` (the menu action through `LocalMoveToTrash`, and its dialogs) and `TrashRoute`/`TrashScreen`.

## Songs that leave the library

The trash isn't the only way a song leaves (a delete on the web, an admin hiding it): the app handles all of them the same way, keyed by library id.

- A track id the sync reports gone is written to a small persistent list (`DeletedTrackRegistry`), and `AudioCacheStore` drops its read-ahead copies from the cache, trying again later while a copy is in use. Offline copies follow the offline plan as before.
- The play queue drops tracks that are no longer in the library, but only once a sync of this session has succeeded (`LibraryRepository.successfulSync`): a queue restored while offline isn't emptied by a stale local library.
- A restored song comes back with the same id, so likes, playlists and offline copies find it again.
