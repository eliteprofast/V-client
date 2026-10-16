# V-Client for Android, Java Edition (experimental)

An all-in-one Minecraft: Java Edition launcher for Android. It bundles its own Java runtimes and game engine, so players install only this app. In it you add a **Microsoft account** (premium) or a **local account** (offline) and play.

**This is a modified copy of [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)** by the PojavLauncher team and contributors, licensed under the **GNU LGPL v3** (see [LICENSE-LGPL-3.0.txt](LICENSE-LGPL-3.0.txt)). Full credit to them. PojavLauncher is not affiliated with V-Client, and Minecraft is a trademark of Mojang Studios.

## Status

First build. It compiles and the APK is signed and well-formed, but it has **not been tested on a device yet**. Expect rough edges. Downloads: [downloads page](../website/downloads.html), file `website/V-Client-Java.apk` (arm64 phones only, Android 8.0+).

## What was changed

[vclient-changes.patch](vclient-changes.patch) is the complete diff against upstream commit `b12ad04` (the last upstream commit). In short:

- App name, app ID (`com.vclient.javaclient`), version, icon, main logo image (`ic_pojav_full`, replaced by a V-Client PNG; binary, not in the patch) and color palette replaced with V-Client's; English UI texts reworded; minimum Android raised to 8.0. Other-language translations still use the upstream name.
- The About/credits screens still credit PojavLauncher, as the license requires.
- Native build limited to `arm64-v8a`, and the non-arm64 Java runtime archives removed, to keep the APK under 100 MB.

Everything else is unmodified upstream code, still under its original license and authorship notices.

## How it was built

1. Check out PojavLauncher at `b12ad04`, apply `vclient-changes.patch`.
2. Put the Java 8/17/21 runtime archives (`jre`, `jre-new`, `jre-21` under `app_pojavlauncher/src/main/assets/components/`) and `ltw-release.aar` (under `app_pojavlauncher/libs/`) from the official PojavLauncher release (`gladiolus`) in place. Upstream's own build fetches them from CI artifacts that have since expired.
3. JDK 17+, JDK 8 (for the small `jre_lwjgl3glfw` helper), Android SDK 34 and NDK 25.2.9519653, then:
   ```
   gradlew :app_pojavlauncher:assembleDebug -Porg.gradle.java.installations.paths=<path to JDK 8>
   ```
4. The signed output is `app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk`.

## Your LGPL rights

Source for this app is upstream PojavLauncher (link above) plus the patch in this folder. You may modify and redistribute it under the LGPL v3.
