# Music AI and DJ — experimental beta

These features require a matching server with the versioned Music AI capability
API. Every AI module defaults off for new installations and accounts. The client
hides unsupported or disabled modules. Existing explicit preferences survive an
upgrade; the Android app does not silently enable server modules.

## Included

- Four daily mood cards directly below favorite playlists, with local vector
  artwork and readable mood colors. See all opens daily and weekly selections.
- Temporary selections reuse the native playlist screen: play, shuffle, likes,
  track menus and queue actions. Save a stable copy under a chosen name; later
  daily/weekly renewal never replaces that copy.
- A separate DJ studio beside Discover, Similar tracks and Podcasts.
- AI settings with independent user preferences and an experimental badge.
- Start a mix from the playing song or its overflow menu. Local similarity uses
  the exact native track identity; external discovery is a separate opt-in beta.
- A DJ button on the current queue, preserving local track order and repeats.
- The normal Media3 player, foreground service and media controls show the
  current and next DJ titles with transition progress. Next is a server command;
  already rendered audio cannot change instantly. Prepared mixes support seeking;
  interactive mixes follow their HLS window.
- A playback sleep timer: 5, 10, 20, 30, 45 or 60 minutes, or cancellation.
- Playlist menus show the three most recently used destinations, then a bounded
  selector for the rest.

## Resource use and boundaries

Models and mixing run on the server, not on the phone. Android uses its existing
Media3 audio decoder and authenticated HTTP stack. Live DJ segments bypass the
persistent library cache; ordinary offline files retain their existing behavior.
There is no on-device model download or new model subscription. Additional phone
CPU, memory, battery and mobile-data consumption have not yet been qualified.
An HLS connection and foreground playback remain necessary for interactive DJ.

Continuous DJ starts with a bounded selection, then requests fresh authorized
local tracks in the background, with recent-track avoidance. Inputs remain local
files, without a radio fallback. The server permits one
render session at a time and applies a separate 5 GiB cache budget by default.

## Validation and remaining acceptance

Earlier tests and the corrective four-module suite cover owner changes,
identifiers, commands, HLS metadata, repeated occurrences, listening and the
service-owned timer. Corrective player tests include 25 simulated mode cycles
and system-controller replacement of a DJ transport. The optimized beta enables
R8/resource shrinking and is non-debuggable, with the same package/signature as
the preceding test build. See the README for current results and pending checks.
Simulated cycles do not establish physical-device acceptance.

Physical Android background/network recovery and comparative listening remain
to be tested. An initial official Android Auto DHU check on Pixel 9 Pro,
Android 17, passed browsing, queue display, play and pause on the preceding beta.
It does not qualify this new build, DJ playback, a real receiver or endurance.
The latest DJ engine still lacks its real one-hour qualification. Qualified
interactive use requires zero interruptions and rendering above 1.5× real time.
A separate unqualified trial requires explicit server and user opt-in; it never
marks an engine as qualified. No model is downloaded for the unavailable local
DJ engine. Macrobenchmark is opt-in with `-PincludeBenchmarks=true`.

The physical optimized beta also passed a visual check of the four colored mood
cards, native temporary-playlist view, save-dialog opening and editable name.
That check cancelled the save and did not create a library playlist. Targeted
native collection/card/control tests passed (six passed); the save-confirmation
UI test is explicitly ignored because Robolectric dialog focus never becomes
idle. Its physical instrumented confirmation remains pending; the ignored test
must not be counted as a pass.

This is a draft beta contribution; please report the device, Android version,
server capability/version and reproducible steps without credentials or private
library files.
