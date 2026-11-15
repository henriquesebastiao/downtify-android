# Installing on a TV

Downtify isn't in the Amazon Appstore or Google Play, so on a TV it's installed from its APK. The same APK
runs on Amazon Fire TV (Fire OS 7 or newer) and on Android TV / Google TV, and shows the TV interface there.

There are three ways to get it onto the TV:

1. [With ADB from a computer](#1-with-adb-from-a-computer): the most direct, if you have a computer on the same network.
2. [With Tellyscope from your phone](#2-with-tellyscope-from-your-phone): the same thing done from an Android phone, no computer.
3. [With the Downloader app on the TV](#3-with-the-downloader-app-on-the-tv): everything on the TV itself, with the remote.

## The APK

Every release on the [Releases page](https://github.com/henriquesebastiao/downtify-android/releases) has the app
attached as `downtify-android-v<version>.apk` (with a `.sha256` next to it to check the download). Pick the newest
release and its `.apk`.

## Before you start: developer options

Methods 1 and 2 talk to the TV over ADB, which is off until you turn it on. Method 3 needs the TV to accept apps
from outside its store. Both settings live in the TV's developer options, hidden at first.

**Fire TV**

1. **Settings › My Fire TV › About**, and press OK on your device's name (e.g. *Fire TV Stick 4K*) seven times,
   until it says you're a developer.
2. Back in **My Fire TV**, open **Developer options** and turn on **ADB debugging** (methods 1 and 2) and, for
   method 3, **Install unknown apps** (on older Fire OS, **Apps from Unknown Sources**).
3. Note the TV's IP address: **Settings › My Fire TV › About › Network**.

**Android TV / Google TV**

1. **Settings › System › About** (on some TVs **Settings › Device Preferences › About**), and press OK on **Build**
   (or **Android TV OS build**) seven times.
2. In **Developer options**, turn on **USB debugging** and, if listed, **Network debugging** or
   **Wireless debugging** (methods 1 and 2).
3. Note the TV's IP address: **Settings › Network & Internet**, then your network.

The TV and the computer or phone you install from must be on the same network.

## 1. With ADB from a computer

You need `adb` on the computer: it comes with Android Studio, or alone as
[SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) for Windows, macOS and Linux.

1. Download the APK (see [The APK](#the-apk)) to the computer.
2. Connect to the TV, with its IP address from above:

   ```bash
   adb connect 192.168.1.50:5555
   ```

   The first time, the TV asks **Allow USB debugging?** Tick **Always allow from this computer** and choose
   **Allow**; otherwise it asks again after every restart. `adb devices` should then list the TV as `device`
   (`unauthorized` means the question is still waiting on the TV).

   A Google TV with **Wireless debugging** instead of network debugging gives a pairing code and its own port:
   open it in developer options, choose **Pair device with pairing code** and run
   `adb pair <ip>:<pairing port>` with that code, then `adb connect <ip>:<port shown on that screen>`.
3. Install:

   ```bash
   adb install downtify-android-v1.2.3.apk
   ```

   To update later, install the newer APK over it with `adb install -r downtify-android-v1.3.0.apk`: the pairing
   and the settings stay.
4. Downtify is now with the TV's apps (on Fire TV, at the end of **Your apps & channels**).

## 2. With Tellyscope from your phone

[Tellyscope](https://github.com/man15h/tellyscope) is an Android app (Android 8 or newer) that manages a TV over
ADB from the phone, installing APKs among other things. It's tested on Fire TV (Fire OS 8) and works with Android
TV and Google TV.

1. Install Tellyscope on the phone, from its
   [Releases page](https://github.com/man15h/tellyscope/releases) (or the other sources its README lists).
2. Download the Downtify APK (see [The APK](#the-apk)) on the phone: it lands in **Downloads**.
3. Open Tellyscope, choose your TV's brand under **Add your TV** and tap **Connect** when your TV shows up.
4. On the TV, check that the key matches the one Tellyscope shows, tick **Always allow from this computer** and
   choose **Allow** (with wireless debugging, type the 6-digit pairing code instead).
5. In Tellyscope, open **Install apps** and pick the Downtify APK from Downloads. It says whether it's a new app or
   an update, and installs it on the TV.

## 3. With the Downloader app on the TV

**Downloader** (by AFTVnews) is a free app with a built-in browser that downloads an APK and installs it on the TV
itself, with the remote. It's in the Amazon Appstore for Fire TV and in Google Play for Android TV / Google TV;
search for *Downloader* on the TV. There are many tutorials online showing it step by step.

1. Install **Downloader** from the TV's store.
2. Let it install apps:
   - **Fire TV:** **Settings › My Fire TV › Developer options › Install unknown apps**, and turn on **Downloader**.
   - **Android TV / Google TV:** **Settings › Apps › Security & restrictions › Unknown sources** (on newer versions
     **Settings › Apps › Special app access › Install unknown apps**), and turn on **Downloader**.
3. Open Downloader. In its address field type the Releases page,
   `https://github.com/henriquesebastiao/downtify-android/releases`, and choose **Go**: it opens in Downloader's
   browser.
4. Under the newest release, open **Assets** and choose `downtify-android-v<version>.apk`. Downloader downloads it
   and asks to install it: choose **Install**, then **Open** or **Done**.
5. Downloader offers to delete the APK afterwards; you can, the app stays installed.

To update, do the same with the newer release: it installs over the old one and keeps the pairing.

## After installing

Open Downtify. On a TV it starts in its own interface, dark, with the remote:

1. Pick your server under **Found on this network**, or move down to the address field and press **OK** to type it
   (`192.168.1.20:8000` is enough). The keyboard's **Next** moves on and puts the keyboard away.
2. Sign in with your account (**OK** on each field opens the keyboard), or choose **Pair with a code** and type the
   8-character code from the web page's **Settings › Apps**. A TV has no camera, so there's no QR code to scan.

Notes:

- **Fire TV and the app's icon.** Fire TV shows an app from outside its store by its icon, in a square on a grey
  tile; Downtify has a square TV icon for that. Fire TV keeps the icon it read the first time the app was installed:
  an update doesn't change it, nor does a restart. Only uninstalling and installing again does, and that also
  unpairs the app.
- **Updates keep everything** as long as the new APK is signed like the old one, which every release is. A debug
  build (`./gradlew installDebug`) installs as a separate app, `com.henriquesebastiao.downtify.debug`, next to
  the release one.
