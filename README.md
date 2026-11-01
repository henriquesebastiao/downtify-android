# Downtify for Android

The official Android client for [Downtify](https://github.com/henriquesebastiao/downtify), the self-hosted music server. It finds your server on the home network (or takes an address you type), signs in with your Downtify account, syncs the library and streams it — original quality on Wi-Fi, transcoded on mobile data — with the system media notification, lock screen and Bluetooth controls.

> [!IMPORTANT]
> This app is currently in the development and testing phase; it will contain bugs. Integration with Downtify is also still being developed and tested.

## Screenshots

<img width="30%" vspace="20" src="https://github.com/user-attachments/assets/dd1fc199-a8d1-454b-84c6-3fbb96822c7a" />
<img width="30%" vspace="20" src="https://github.com/user-attachments/assets/8ea1042a-a4dc-416d-9752-8939f55741e4" />
<img width="30%" vspace="20" src="https://github.com/user-attachments/assets/c86efa10-362d-4794-a19a-a5097a0fc084" />

# Download

Downtify Android is available on GitHub Releases page.  

[<img height="75" src="https://github.com/user-attachments/assets/ea9ecc83-372a-4ef5-8595-1423873f2faf" />](https://github.com/henriquesebastiao/downtify-android/releases)
[<img height="75" src="https://github.com/user-attachments/assets/33762cb0-6efe-4082-bd45-56c49fb0cc27" />](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.henriquesebastiao.downtify%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fhenriquesebastiao%2Fdowntify-android%22%2C%22author%22%3A%22henriquesebastiao%22%2C%22name%22%3A%22Downtify%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22includeDrafts%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertReleaseTitlesFilter%5C%22%3Afalse%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertReleaseNotesFilter%5C%22%3Afalse%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22includeZips%5C%22%3Afalse%2C%5C%22zippedApkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22includeTarballs%5C%22%3Afalse%2C%5C%22tarballedApkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22minimumUpdateAgeDays%5C%22%3A%5C%22%5C%22%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22detectionDateAsReleaseDate%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22allowedSigningCertHashes%5C%22%3A%5C%22%5C%22%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthStaleRelease%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthStaleVersion%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthFailedChecks%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthArchived%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthFilteredReleases%5C%22%3A%5C%22%5C%22%2C%5C%22github-creds%5C%22%3A%5C%22%5C%22%2C%5C%22GHReqPrefix%5C%22%3A%5C%22%5C%22%2C%5C%22checkRepoRename%5C%22%3Afalse%7D%22%2C%22overrideSource%22%3A%22GitHub%22%7D
)

## Android Auto

Android Auto can browse playlists, liked songs, artists, albums, tracks and completed offline downloads through the app's media library. Set up and sync the app on the phone first, then open **Downtify** in Android Auto's launcher. A separate test build may be named **Downtify Preview**.

**Installing an APK from GitHub, Obtainium or a download link:** Android Auto may hide it until its own **Unknown sources** developer option is enabled. Allowing Android to install APKs is a different setting. While parked:

1. Open Android Auto settings on the phone and scroll to **Version**. Tap it to show the version/permission information, then tap that section ten times to enable developer settings; confirm if asked.
2. In the overflow menu, open **Developer settings** and enable **Unknown sources**.
3. Open **Customize launcher**, check **Downtify** (or the installed test build's name), then reconnect Android Auto.

This is an Android Auto installation-source requirement; signing an APK or changing the app's code cannot remove it. Google's supported alternative is installation from a trusted source, such as Google Play, including an internal test track. See the [official testing requirements](https://developer.android.com/training/cars/testing#unknown-sources). This does not announce a Play Store release of Downtify.

Opening the library does not start music. Android Auto/your receiver chooses the last media source and requests playback; Downtify does not force itself over another player. See [Android Auto setup, troubleshooting and validation](docs/android-auto.md) for missing apps, resumption and tests.

## Languages, fonts and theme

- **Languages:** the same eight as the Downtify web app — Bulgarian, English, French, Greek, Hungarian, Portuguese (Brazil), Spanish and Turkish. The app follows the phone's language and falls back to English when it isn't one of these. On Android 13+ the language can also be set per app (**Settings › Appearance › Language** opens Android's own screen for it).
- **Fonts** (Settings › Appearance › Font): Google Sans Flex in its rounded cut, as on Pixel phones (the default), Downtify classic (Sora and DM Sans) or the system font. Google fonts are fetched by Play services; without it the system font is used.
- **Theme:** follow system, dark or light, optional wallpaper colors (Android 12+) and **Pure black** for OLED screens in the dark theme.
- **About** (Settings › About Downtify): version (tap to copy), bug reports, source code, the GPL-3.0 license, the terms and disclaimer, and ways to support the project.

## Requirements

- JDK 17 or newer (the build targets Java 17).
- Android SDK with platform 37 (`compileSdk`/`targetSdk` 37, `minSdk` 26). Gradle installs missing platforms on first build when `sdk.dir` in `local.properties` points at a writable SDK.
- A Downtify server **3.1.0 or newer** (mobile API v1, pairing). Signing in with a username and password needs a server newer than 3.7.0 (`password_sign_in` in `GET /api/server/info`); older ones pair with a code.

## Build

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # onto a connected device or emulator
./gradlew build                  # everything: compile, unit tests, Android lint
./gradlew detekt spotlessCheck   # static analysis and formatting
./gradlew spotlessApply          # fix formatting
```

The debug build installs as `com.henriquesebastiao.downtify.debug`, so it sits next to a release build. `assembleRelease` is signed with the debug key (there's no release keystore in the repo) — replace the signing config before shipping.

## Pointing it at a server

1. Run Downtify (`make run` or Docker). Note the address you open it at in a browser, e.g. `http://192.168.1.20:8000`.
2. Open the app. Allow **nearby devices / local network** access when asked (Android 17+ needs it to reach anything on your home network).
3. Pick the server under **Found on this network**, or type its address. `192.168.1.20:8000` is enough; `http://` is added for you. HTTPS addresses work too, including behind a reverse proxy with a private CA installed on the phone.
4. Sign in with the username and password you use on the web page. The phone becomes one of that account's paired apps; the password isn't kept on the phone.

   Or pair instead, without typing the password: on the web page, signed in as the account the phone should belong to, open **Settings → Apps → Pair a phone**, then scan the QR code (from the first screen, or **Use a pairing code instead**) or type the 8-character code. A server with sign-in turned off, or older than app sign-in, goes straight to the code.

Notes:

- **Docker:** the default bridge network doesn't carry mDNS, so the server won't show up under "Found on this network". Type the address, or run the container with `network_mode: host`.
- **Emulator:** the host machine is `10.0.2.2` (`http://10.0.2.2:8000`).
- **Plain http** is allowed for any address, because self-hosted servers are usually `http://` on the LAN and Android's network security config can't express "private addresses only". Over http the password and the device token travel unencrypted: fine at home, not across the internet. The connect and sign-in steps warn about a plain-http public address. See `app/src/main/res/xml/network_security_config.xml`.
- **Unpairing** from the web (Settings → Apps → the phone → Unpair) stops playback and sends the app back to the connect screen. **Sign out** in the app's Settings forgets the token on the phone; the web page keeps listing the phone until it's unpaired there.

## Project layout

| Module | What's in it |
|---|---|
| `:app` | Activity, navigation, all screens (`feature/*`), strings |
| `:core:model` | Pure Kotlin: domain types and logic (address normalising, pairing URI, stream policy, grouping, listen counter, LRC parser, search) |
| `:core:network` | OkHttp/Retrofit client for the contract, NSD discovery, WebSocket, Keystore-encrypted session store |
| `:core:data` | Room (library, playlists, likes, recents, listen queue, offline index), DataStore settings, sync, offline downloads, the listen flusher (WorkManager), server search and the server's download queue |
| `:core:player` | Media3 `MediaSessionService`, stream URL resolving, cache, listen and activity tracking, UI-side `PlayerController`, the preview player |
| `:core:designsystem` | Material 3 theme (brand tokens, dark/light, dynamic color), fonts, icons, logo, shared components |
| `build-logic` | Gradle convention plugins |

Conventions for working on the code are in [CLAUDE.md](CLAUDE.md). The server contract the app implements lives in the server repository: `docs/mobile-client-contract.md`.

## Credits

The app follows Material 3 Expressive. Several of its components take their design from [PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer), a Material 3 Expressive music player for Android by [theovilardo](https://github.com/theovilardo) and [contributors](https://github.com/PixelPlayerHQ/PixelPlayer/graphs/contributors). Its free and open-source edition is [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS), under GPL-3.0. Thank you for the inspiration.

Inspired by PixelPlayer:

- the type scale with Google Sans Flex's rounded cut, the large page titles and the wide hero title;
- page headers with round back and action buttons, the tinted Library header and its pill tabs;
- grouped lists with large outer and small inner corners, and the settings' value tags;
- the floating navigation bar, with the option of a full-width one;
- the side menu opened from Home's menu button;
- the library's one-row title, its icon buttons, its sort sheet and its scrollbar;
- the song that's playing drawn as a pill with a round cover;
- the mini player:
  - joined to the floating bar as one group;
  - its title sliding when it doesn't fit;
  - its previous, play/pause and next buttons;
- Now Playing and the mini player in the colors of the cover.

These are designs, not code: Downtify implements them in its own components (`:core:designsystem`) and includes no PixelPlayer source.

## License

GPL-3.0 — see [LICENSE](LICENSE).
