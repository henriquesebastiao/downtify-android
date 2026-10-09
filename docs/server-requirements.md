# Server requirements and contract notes

Things the app needs from the server that it doesn't offer yet, and places where the server's docs and code disagree. The server is the source of truth; nothing here is implemented server-side from this repo.

## Endpoints the app uses beyond the contract's device list

The mobile contract (§"What a device may do") lists reads, searching, previews, download requests, likes, listens and podcast downloads. The app additionally uses three web routes the server offers to any signed-in client, for full-track streaming and Similar:

- `GET /api/similar/tracks` — the Similar screen (YouTube Music's radio mix, no key).
- `GET /api/stream/file?video_id=` — full audio of a song that isn't downloaded, cached on the server. The player behind it: rows play it in the media session instead of 30-second preview clips.
- `POST /api/stream/prefetch` — warms that cache one track ahead so skipping starts at once.

Deleting library files stays out: the contract answers `403` for devices, so the app offers download but never delete.

## Doc/code mismatches

### Revoked token on the WebSocket handshake is HTTP 403, not close code 4401

- **Docs** (`mobile-client-contract.md` §3 and "WebSocket"; `api-reference.md` "WebSocket"): an unpaired device's socket "closes with code `4401`".
- **Code** (`downtify/auth.py`, the WebSocket branch of the auth middleware): when the token is already revoked at connect time, the middleware sends `websocket.close` with 4401 *before* accepting, which the ASGI server turns into an **HTTP 403** on the handshake. 4401 only reaches clients that were connected when the device was unpaired (`api.py`, `close_device`).
- **What the app does:** treats a refused handshake as "maybe revoked" and confirms with `GET /api/auth/status` before signing out (a proxy that blocks WebSockets also gives 403, so it can't sign out on 403 alone). Verified against a running server: unpairing the device while the app was connected closed its socket, the app checked `/api/auth/status`, dropped the token, stopped playback and returned to the connect screen.
- **Suggested fix:** document the 403 case, or accept the socket and then close with 4401 so clients see one signal.

### `GET /api/albums/search` isn't in the API reference

The web UI's album search exists in the server's code (`api.py`, `search_albums_endpoint`) and the reference only mentions it in passing (`search_albums` preference). The app uses it for the albums under "Not in your library yet". It answers `[]` when the user turned album search off, which the app shows as "no albums". Worth a section in the reference, with the album summary's fields.

### A paired phone can't subscribe to a podcast

`POST /api/podcasts/subscribe`, `PATCH`/`DELETE /api/podcasts/shows/{id}` and `DELETE /api/podcasts/episodes/{id}` are admin-only (`auth.py`, the `ADMIN` rules for `/api/podcasts/`), and a device is never an admin, even when the account it belongs to is. So the app can list shows and episodes, play, download an episode on demand and save progress, but not add or remove a show or an episode's file. Letting a device act for an admin account (or a per-account "may manage podcasts" switch) would let the app subscribe from a pasted feed or Spotify link, as the web page does.

## Nice to have

### Already-downloaded results

Server search results carry YouTube Music or Spotify ids; library tracks carry Downtify's own ids. Nothing links the two, so the app can't tell that a result is already in the library. A `track_id` on a search result the server already has (it knows the source id of what it downloaded) would let the app show "In your library" instead of a download button.

### Recently played across devices

"Jump back in" on Home is tracked locally (Room `recent_contexts`), per the phase-1 brief. A server endpoint listing a device's (or the user's) recent play contexts — album, playlist, artist, liked songs — would let it follow the user between the web page and the phone. The listen reports (`POST /api/discover/listens`) carry track ids only, not the context they were played from.
