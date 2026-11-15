# Developing and testing on a TV

The TV interface (`app/…/tv/`) was built and checked on a real Fire TV Stick 4K over ADB, from the computer, without
touching the remote: build, install, launch, press keys, take a screenshot, look, change, repeat. This page is that
workflow, so you can do the same on your own TV. The emulator helps (see [Without a TV](#without-a-tv)), but a real
Fire TV behaves differently in ways that matter (see [What a real Fire TV does differently](#what-a-real-fire-tv-does-differently)).

The commands are for a Unix shell (Git Bash works on Windows). `$TV` stands for your TV's address with the port:

```bash
TV=192.168.1.50:5555
```

## Connect

1. Turn on the TV's developer options and ADB debugging, and find its IP address: see
   [Before you start: developer options](tv-installation.md#before-you-start-developer-options).
2. Connect, and accept on the TV:

   ```bash
   adb connect $TV
   adb devices                     # the TV listed as "device"
   ```

   Tick **Always allow from this computer** when the TV asks. Without it the TV asks again after every restart and
   `adb devices` shows `unauthorized` until someone answers on the TV.
3. With more than one device attached, give every command the TV: `adb -s $TV …`, or `export ANDROID_SERIAL=$TV`
   once (Gradle's `installDebug` honours it too).

After the TV restarts or sleeps for long, run `adb connect $TV` again.

## Build, install, launch

```bash
./gradlew :app:assembleDebug
adb -s $TV install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $TV shell am start -n com.henriquesebastiao.downtify.debug/com.henriquesebastiao.downtify.MainActivity
```

- The debug build is `com.henriquesebastiao.downtify.debug`, apart from a release install: it pairs on its own.
- `install -r` keeps the app's data (the pairing, settings, offline files) but kills it, which stops the music. Be
  careful when someone is listening.
- `adb uninstall com.henriquesebastiao.downtify.debug` wipes everything, pairing included. It's also the only way
  to see a new launcher icon on Fire TV (see below).
- On a machine with little memory (8 GB), keep the loop light: `:app:assembleDebug`, one module's tests
  (`./gradlew :core:data:testDebugUnitTest --tests '*AppearanceSettingsTest'`) or a filtered set
  (`./gradlew :app:testDebugUnitTest --tests '*.tv.*'`), and leave `./gradlew build` (lint and every test) to CI.
  Lowering Gradle's heap in `~/.gradle/gradle.properties` (`org.gradle.jvmargs=-Xmx2560m`,
  `org.gradle.parallel=false`, `org.gradle.workers.max=2`) keeps the computer usable while it builds.

## See the screen

```bash
adb -s $TV exec-out screencap -p > shot.png
```

The screenshot is the full 1920 × 1080 picture; open it and look. To zoom into a detail (a tile, a focus ring),
crop it, e.g. with Python's Pillow:

```bash
python -c "from PIL import Image; im=Image.open('shot.png').crop((270,565,435,662)); im.resize((im.width*4, im.height*4), Image.NEAREST).save('zoom.png')"
```

[scrcpy](https://github.com/Genymobile/scrcpy) mirrors the screen live over the same ADB connection, if you'd rather
watch than take shots.

## Drive it with the remote's keys

```bash
k() { adb -s $TV shell input keyevent "$1"; sleep 1.2; }

k KEYCODE_DPAD_DOWN
k KEYCODE_DPAD_RIGHT
k KEYCODE_DPAD_CENTER           # OK
k KEYCODE_BACK
adb -s $TV exec-out screencap -p > after.png
```

| Remote | Key code |
|---|---|
| Arrows | `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_DOWN`, `KEYCODE_DPAD_LEFT`, `KEYCODE_DPAD_RIGHT` |
| OK | `KEYCODE_DPAD_CENTER` |
| Back, Home, Menu | `KEYCODE_BACK`, `KEYCODE_HOME`, `KEYCODE_MENU` |
| Play/pause, next, previous | `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS` |

- Wait a second between keys and before a screenshot: animations and focus moves take that long, and a key sent too
  soon lands somewhere else.
- One key, one screenshot, while you're finding out where focus goes. A row of keys sent blind ends up on a screen
  you didn't mean (a tab, Settings), and you only find out at the end.
- To type into a field once the TV's keyboard is up: `adb -s $TV shell input text "192.168.1.20:8000"` (a space
  is `%s`). On Fire TV the keyboard is full screen and takes the arrow keys while it's up: find its **Next** key with
  the arrows and press OK, as with the remote.

## Ask the TV what's going on

| Question | Command |
|---|---|
| Which app and window have focus? | `adb -s $TV shell dumpsys window \| grep mCurrentFocus` |
| Is the keyboard up, and who asked for it? | `adb -s $TV shell dumpsys input_method` (`mInputShown`, and `mSoftInputShowHideHistory`: each show or hide, its time and reason; `SHOW_SOFT_INPUT` is the app asking) |
| Does it count as a TV? | `adb -s $TV shell dumpsys uimode` (`mCurUiMode=0x14`: television, day) |
| What hardware does it say it has? | `adb -s $TV shell pm list features` (Fire TV: `leanback`, `type.television`, no `touchscreen`) |
| When was the app installed or updated? | `adb -s $TV shell dumpsys package com.henriquesebastiao.downtify.debug \| grep -E "firstInstallTime\|lastUpdateTime"` |
| The app's log | `adb -s $TV logcat --pid=$(adb -s $TV shell pidof com.henriquesebastiao.downtify.debug)` |
| How long since the TV restarted? | `adb -s $TV shell uptime` |
| Where did an app come from? | `adb -s $TV shell pm list packages -i` (`installer=com.amazon.venezia`: the Appstore) |

## Learn from a reference app

The TV screens follow the TV music apps. To match one, open it on the TV and study it the same way: a screenshot,
an arrow key, a screenshot. Notice how focus is drawn, where it goes, what each key does, how rows scroll.

- Move with the arrows and Back only. OK on a song, a play button or a now-playing card starts music in that app.
  If something plays, pause it at once.
- Put the app back as you found it (the tab, the scroll) when you're done: it's someone's TV.

## What a real Fire TV does differently

These are what the emulator won't show you. Each cost a session to find.

- **The launcher icon is cached from the first install.** Fire TV draws an app from outside its store by its icon,
  read once when the app is first installed. Updates (`install -r`) and restarts never change it: uninstall and
  install again to see a new icon. It draws that icon in a square on a grey tile, so a 16:9 icon gets squeezed, and
  it ignores `android:banner` for such apps (only Appstore apps get a full tile, from Amazon's servers). The TV icon
  is `drawable/tv_icon.xml`, square, picked by `mipmap-television`.
- **No Google Play services.** Google's downloadable fonts never arrive: the APK carries Sora and DM Sans, and a TV
  defaults to them (`DeviceDefaults`).
- **It reports day mode.** Following the system theme made the first screens white; a TV defaults to dark.
- **The keyboard.** Fire TV's keyboard covers the screen, so you can't see what it's doing to the form behind it.
  It stays up after its action key unless the app hides it, and focusing a text field opens it, also when focus
  comes back to a field by itself after a step. On a device without a touchscreen the connect form opens the
  keyboard on OK only (`KeyboardGate` in `ConnectScreen.kt`); `KeyboardOptions.showKeyboardOnFocus = false` did not
  stop it. Check with `dumpsys input_method` rather than by eye.
- **Focus starts where the system puts it.** After a screen changes, focus can land on the first focusable thing
  (a text field, a tab). Test what Up, Down and Back do from each place, not only the path you meant.
- **After a restart** the TV may ask **Allow USB debugging?** again, over whatever is on screen, if
  **Always allow** wasn't ticked. That's yours to answer on the TV.

## Without a TV

Android Studio's **Android TV** emulator (a 1080p TV image) runs the TV interface (`isTelevision()` sees the
television UI mode and leanback), and the same `adb` commands work on it. It has Google Play services and none of the
Fire TV behaviour above, so check those on a real Fire TV before calling a change done.

## Automated tests and previews

- A TV screen is a stateless `TvXScreen`; test it on the JVM with Robolectric as a TV:

  ```kotlin
  @RunWith(AndroidJUnit4::class)
  @Config(qualifiers = "w960dp-h540dp-television")
  class TvSomethingScreenTest { … }
  ```

  Press the remote's keys with `performKeyInput { pressKey(Key.DirectionDown) }`.
- To test as a device without a touchscreen:
  `shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)` before
  `setContent` (see `ConnectScreenTest`).
- TV previews: `@Preview(name = "TV", device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_TELEVISION)`.

## Before you open a pull request

- `./gradlew build detekt spotlessCheck` passes (or CI ran it).
- On a TV, or at least the TV emulator: every screen you touched works with the arrows, OK and Back alone; focus is
  visible wherever it is; nothing needs a touch.
- Say in the pull request what you ran on which device (model and Fire OS / Android TV version) and what you
  didn't.
