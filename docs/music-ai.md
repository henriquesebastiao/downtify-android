# Music AI and DJ — experimental beta

These features require a matching server with the versioned Music AI capability
API. Every AI module defaults off for new installations and accounts. The client
hides unsupported or disabled modules. Existing explicit preferences survive an
upgrade; the Android app does not silently enable server modules.

## Included

- Home → For you: daily and weekly mood playlists, stable saved copies and
  thematic continuous DJ shortcuts.
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
CPU, memory, battery and mobile-data consumption have not yet been measured.
An HLS connection and foreground playback remain necessary for interactive DJ.

Continuous DJ currently extends a bounded selection (24 tracks for thematic
shortcuts, up to 40 for a queue), with recent-track avoidance. It is not a radio
or an unlimited catalog service. Inputs are local files. The server permits one
render session at a time and applies a separate 5 GiB cache budget by default.

## Validation and remaining acceptance

The delivered beta passed 454 unit tests (two skipped), debug assembly, Spotless,
Detekt and Lint. Tests cover owner changes, exact track identifiers, server
commands, HLS timeline metadata, repeated occurrences, listening events and the
service-owned timer. These checks do not establish physical-device acceptance.

Physical Android background/network recovery and comparative listening remain
to be tested. The latest server DJ engine has not completed its one-hour real
playback qualification. Ordinary interactive creation stays gated until the
administrator completes that validation with zero interruptions and rendering
above 1.5× real time. The administrator validation stream is available for this
purpose. No synthetic test or client time claim unlocks it.

This is a draft beta contribution; please report the device, Android version,
server capability/version and reproducible steps without credentials or private
library files.
