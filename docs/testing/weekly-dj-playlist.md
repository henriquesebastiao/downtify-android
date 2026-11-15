# Weekly DJ playlist page — beta

Weekly set cards in My music and See all navigate to a playlist page inside the
existing app shell. The shared mini player and Now Playing remain in that shell;
this page does not introduce another audio player or media session.

The page shows the local theme cover, track count, duration, preparation state,
and track list. Listening, preparing audio, and saving a named immutable audio
copy remain explicit actions. An unavailable saved copy cannot silently create
or save another set. Existing preferences are preserved and module defaults
remain off.

## Loading and ownership

The destination carries a theme or saved-copy identifier, never a filesystem
path or provider key. Summary refresh remains tied to the visible lifecycle.
Only the selected session's details are fetched. The loader cancels its previous
request and fences results by request generation, account and capability state.
Leaving the page clears its details; a late response cannot repopulate them.
Failures expose a retry action instead of starting playback or preparation.

## Source validation

Validated on 14 November UTC / 15 November 2026 Europe/Paris, using the existing
guarded build and unchanged memory limits:

- Formatting and separate Kotlin source/test compilation passed.
- Ten UI cases passed: five existing shelf cases, two copy-form cases and three
  new navigation/playlist/copy cases. No UI case failed or was skipped.
- Five loader cases passed after fixing test lifecycle cleanup: disabled module,
  reordered response, leaving the page, network failure, and disabling a module
  while a request is pending. The ViewModel is cleared before resetting Main.
- Detekt and `git diff --check` passed.

The loader-only invocation took 22.791 seconds and peaked at 1,315,373,056 bytes
for the whole build process group. The earlier ten-UI-plus-five-loader run
included the five fixture failures; it is not reported as a green combined run.
These are host test costs, not phone memory, latency, battery or server DJ costs.

APK/R8/Lint delivery gates, physical appearance, Bluetooth/Android Auto,
first-sound latency, audio quality and the uninterrupted one-hour qualification
remain separate checks. The page does not change the renderer or qualify an
unavailable real-time DJ engine.
