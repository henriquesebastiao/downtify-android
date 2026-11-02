# Android Auto

The phone app exposes a Media3 library service. Android Auto renders the driving interface; the phone activity is not a car screen. This is not a separate Android Automotive OS application.

## Available content

Browse playlists, liked songs, artists, albums, all library tracks, and completed offline downloads. The catalog uses the existing local library and permissions, not online search. Playlist and album order are preserved. Selecting a row starts its collection at that row in the same player/queue used by the phone. A browser that limits root entries receives one Library folder containing all six categories.

Text and voice search match local title, artist and album metadata. A unique playable voice result can start playback; ambiguous requests require choosing a search result. No download or administrative actions are exposed. Browsing never starts playback.

The existing sync coordinator runs while either the phone UI or the media service is active, with one shared sync loop. Reopening a catalog page does not rescan files or call server search. No server endpoint changes are required.

## Artwork and controllers

Trusted system/media controllers can browse and play. App-specific queue commands remain private to the phone app. External requests resolve known catalog IDs; supplied audio URLs are discarded and unknown IDs are rejected.

External artwork uses opaque read-only local content URIs, prepared asynchronously with the existing authenticated HTTP client. Images are at most 512 pixels, input is limited to 8 MiB, and the disk cache is capped at 32 MiB. Browsing reads ready images without waiting for network. Playback prepares the current and up to three following covers, without changing audio preloading settings. Images from a previous association cannot be reopened after changing accounts.

## Validation

Run `./gradlew testDebugUnitTest lint detekt spotlessCheck assembleRelease`. Catalog/callback tests cover paging, empty collections, accents, playlist duplicates, selection position, unknown IDs, supplied URLs, authentication errors and image cache isolation. Existing playback restoration and offline tests must remain green.

Then use the official [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu) and a real receiver, stationary:

1. Open Downtify from the Android Auto launcher without first opening the phone activity.
2. Browse every category, search, choose a playlist track and check queue order on the phone.
3. Check current artwork, steering-wheel controls, pause/resume, shuffle/repeat and call interruptions.
4. Reconnect with the phone locked and after process death; the queue should restore paused until playback is requested. Explicit Android force-stop restrictions still apply.
5. Disconnect the network and play completed offline files. Restore network and check catalog updates and artwork. Check both cable and wireless receiver connections where available.
6. Unpair the phone and verify that the previous library cannot be browsed or played.

For a sideloaded media APK, Android Auto's developer setting **Allow unknown sources** may be required; see [official testing instructions](https://developer.android.com/training/cars/testing). This setting does not replace the app integration. Keep the same application ID and signing certificate when updating an existing test installation.

Real receiver and voice-assistant behavior must be reported separately from automated tests. Do not claim car validation based solely on a successful APK build.

## App missing from the launcher

Check this while parked. An APK installed directly from a download link is not a Play Store installation. In the phone's Android Auto settings, tap the version information ten times to enable developer settings, open the overflow menu, then enable **Unknown sources**. This is Android Auto's own setting, separate from permission to install an APK in Android. See Google's [testing requirements](https://developer.android.com/training/cars/testing#unknown-sources).

Open **Customize launcher**, check the installed app's actual name (a separate test build can be called **Downtify Preview**), and reconnect Android Auto. The application declares a media library, not a phone screen for driving. A generic Android Auto message such as “Media unavailable while driving” does not establish which app or component failed. Do not mark the phone activity distraction-optimized to bypass that restriction.

If the app remains absent, check the installed package/version and whether Android resolves its exported `android.media.browse.MediaBrowserService`. If it appears but cannot open, record the service startup and controller connection error before changing playback code. Do not clear app data or reinstall with a different signing key as a diagnostic step.

## Last-source playback and resumption

Opening the library restores the queue paused, without opening an audio source. A playback request can resume the saved track, position, shuffle order and repeat mode through Media3. Media3 also handles System UI's special recent-content root and calls `onPlaybackResumption`; the app must not add a competing implementation of that framework behavior.

Android Auto and the receiver select the media source on connection. Downtify does not launch itself at Bluetooth connection or boot, and must not take audio focus just to replace another app. If Deezer resumes, first distinguish selection of Deezer from a failed request to resume Downtify. Test reconnecting after each app was the last source used. Android Auto's automatic-music setting is global; it is not an API for Downtify to designate itself as the preferred player.

## Diagnostic and integration checks

`./gradlew :app:testDebugUnitTest --tests '*CarServiceIntegrationTest'` checks the merged application identity/descriptor, discovery of the exported service, real Media3 browser connection to the actual playback service without an Activity, paused queue restoration, favorites browsing and association revocation. Only storage/network dependencies are faked. This does not emulate Android Auto's source filtering, a separate controller UID, an assistant or an autoradio.

For a real device, record Android, Android Auto and app versions, install source, launcher visibility, whether the phone was locked, and which app was last playing. Capture only the relevant service/Media3 connection errors. Keep raw device logs private: they can contain titles, addresses or identifiers, and must never be pasted into a public issue without review. A DHU check and a real parked-car check remain separate acceptance steps.


## Stability follow-up: full-phone freeze report

Launcher visibility is not a playback or stability acceptance test. A full-phone
freeze was reported over direct USB after enabling Android Auto's unknown-source
option. A retained input-dispatch ANR from the installed Preview was subsequently
retrieved. Its exact R8 mapping identifies the main thread waiting in
MediaBrowserServiceCompat.onGetRoot through Media3 ConditionVariable. This
establishes a blocked legacy root path in Downtify, but does not establish the
cause of every reported device-wide freeze; do not recommend repeating the failing setup without diagnostics.

The service now accepts trusted controllers without waiting for queue restoration.
Its library root is static and immediately available, including when unpaired;
authentication errors are returned when content is requested. Browsing does not
prepare audio or seize focus. Storage loading remains off the main thread.

The four primary sections are Playlists, Liked songs, Library and Offline. Library
contains Artists, Albums and Tracks. Receivers requesting fewer than four root
items receive a single Collections folder. Large collections use range folders
with at most 100 children per node; selected playlist rows retain their full
original queue and position. Searches with more than 100 matches ask for a more
specific query instead of constructing an unbounded response.

Catalog updates are coalesced, compared against the previous index and sent only
to subscribed controllers. Unsubscribe/disconnect removes controller state and
cancels unneeded artwork tasks. The initial subscription notification belongs to
Media3, avoiding duplicate notifications from the app.

### Diagnostic procedure (development machine / scratch installation)

Keep the owner's unknown-source option disabled during preparation. First record
OS, Android Auto and app versions, APK certificate, process exit reasons and any
retained ANR/system logs. Keep raw bugreports private: they can contain credentials,
network addresses and personal data. Do not reset storage or reinstall Google Play.

For opt-in counters on a diagnostic installation only:

```sh
adb shell setprop log.tag.DowntifyCar DEBUG
adb logcat -v threadtime -s DowntifyCar AndroidRuntime ActivityManager
adb shell dumpsys meminfo com.henriquesebastiao.downtify.preview
adb shell dumpsys activity exit-info com.henriquesebastiao.downtify.preview
adb shell setprop log.tag.DowntifyCar INFO
```

Counters contain service creations/destructions, heap bytes, thread count and
browse response item counts, never tokens, URLs, media IDs or titles. They are
disabled by default. They do not replace ANR traces or system diagnostics.

On an emulator/scratch installation, compare minimal catalog, 10,000 tracks without
artwork, then artwork. Exercise a platform/legacy browser in a separate process,
repeated bind/unbind, slow storage, cold startup, offline content and revocation.
Modern same-process Robolectric service tests do not establish this acceptance.
Stop on an uncontrolled reconnect loop, ANR or device unresponsiveness.

Then require a usable DHU UI and real receiver checks at rest: locked phone,
all sections, playback, steering buttons, call interruption, ten reconnects,
an hour without freeze, and reconnect after each last-used player (Downtify and
Deezer). The phone must remain responsive. Protocol/TLS negotiation alone is not
a DHU pass. If the official Media3 demo fails too, investigate the host/OS with
sanitized evidence rather than weakening trust checks or forcing a foreground service.

References: [root and hierarchy contract](https://developer.android.com/training/cars/media/create-media-browser/content-hierarchy),
[Media3 1.11.1 fixes](https://github.com/androidx/media/blob/1.11.1/RELEASENOTES.md),
[service churn discussion](https://github.com/androidx/media/issues/3158).
Media3 1.11.1 already contains #3393; upgrading blindly is not proof of a fix.
The generic driving-time error is not an incorrect application label proven by
the report. Do not rename system messages or mark the phone Activity as safe to
display while driving.


The development-only `LegacyCarProbeService` runs in `:legacyCarProbe` and uses
the Android framework MediaBrowser against the actual PlaybackService. The
instrumentation test verifies a different PID and 30 root connect/disconnect
cycles. It uses the app UID: it does not impersonate Android Auto or validate
Android Auto's external trust identity. Run only on a scratch debug installation:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.henriquesebastiao.downtify.LegacyCarProcessTest
```

The debug service is absent from the release manifest. No reset or pairing token
is needed for the unpaired-root test. It does not open an Activity or start Auto.
