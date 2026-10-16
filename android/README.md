# V-Client for Android

Kotlin + Jetpack Compose (Material 3). Package `com.vclient.android`, minSdk 26 (Android 8.0), target/compile SDK 35.

## What it does

- **Home:** two play modes with a preferred-edition switch. **Java Edition** detects PojavLauncher (a separate app you install), shows a version picker from Mojang's release list, and opens PojavLauncher. **Bedrock Edition** detects Minecraft, launches it, and has a one-tap **Join VSMP** button. Also announcements, update banner, Discord and website buttons.
- **Servers:** Bedrock server settings: the VSMP address (editable) and a list of any other servers you add and join.- **News:** the same feed as the website and the Windows launcher, filterable by Launcher / Server.
- **Demo World:** a free, offline voxel sandbox built from original V-Client code and textures (no Minecraft content). Walk, look, jump, break and place blocks on a generated island; the world saves on the phone. Source: pp/src/main/java/com/vclient/android/demo/, screen: ui/screens/DemoScreen.kt. It is ray-cast on the CPU at low resolution, so it needs no GPU code and has no extra files.
- **Account:** sign in or create the shared V-Client account and link your Minecraft / Ely.by names.
- **Settings:** backend address, update-check toggle, manual update check, version.
- **Updates:** checks `GET /api/launcher/update`, downloads the APK from your backend only, verifies size + SHA-256, then opens Android's installer (the first time, Android asks you to allow installs from V-Client).

Android can only run Minecraft Bedrock natively, so V-Client opens the Bedrock app and connects with a `minecraft://connect` link. Java Edition does not run inside V-Client: it is launched through PojavLauncher. PojavLauncher has no launch API, so V-Client can open it but cannot pass it a version or account; the version picker records your choice and you select it in PojavLauncher.

## Build

Open the `android` folder in Android Studio and press Run, or from a terminal (JDK 17+, Android SDK API 35, `ANDROID_HOME` or `local.properties` with `sdk.dir`):

```powershell
.\gradlew.bat assembleDebug          # app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat testDebugUnitTest      # unit tests
.\gradlew.bat assembleRelease -Pvclient.backendUrl=https://vclient.example.com
```

`vclient.backendUrl` (default in `gradle.properties`) is the backend address compiled into the app; players can still change it in Settings. The emulator reaches a backend on your PC at `http://10.0.2.2:8080`. Release builds block plain `http://`, so use HTTPS in production.

### Signing a release APK

```powershell
keytool -genkeypair -v -keystore vclient-release.jks -alias vclient -keyalg RSA -keysize 2048 -validity 10000
```

Create `android/keystore.properties` (it is git-ignored; keep the `.jks` and passwords safe, because **you must sign every future update with the same key** or phones will refuse to update):

```properties
storeFile=C:/path/to/vclient-release.jks
storePassword=...
keyAlias=vclient
keyPassword=...
```

Then `.\gradlew.bat assembleRelease` produces a signed `app\build\outputs\apk\release\app-release.apk`.

### Publishing

Raise `versionCode` and `versionName` in `app/build.gradle.kts`, build the signed APK, and upload it through the management API (see backend/README.md; platform `android`, the same version string as `versionName`). Phones on an older version are offered the update at next launch.

## Layout

```
app/src/main/java/com/vclient/android/
├── MainActivity.kt        bottom-navigation shell
├── MainViewModel.kt       state: status, news, account, updates
├── Minecraft.kt           Bedrock deep links, clipboard, safe link opening
├── data/                  ApiClient (HttpURLConnection + org.json), Prefs, models
├── update/Updater.kt      verified APK download + installer hand-off
└── ui/                    theme (shared V-Client palette), components, screens
```
