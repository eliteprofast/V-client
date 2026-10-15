# V-Client for Android

Kotlin + Jetpack Compose (Material 3). Package `com.vclient.android`, minSdk 26 (Android 8.0), target/compile SDK 35.

## What it does

- **Home:** big **PLAY VSMP** button (opens Minecraft Bedrock and connects to the server), live online state + player count, announcements, Java/Bedrock addresses with copy buttons, Discord and website buttons.
- **News:** the same feed as the website and the Windows launcher, filterable by Launcher / Server.
- **Account:** sign in or create the shared V-Client account and link your Minecraft / Ely.by names.
- **Settings:** backend address, update-check toggle, manual update check, version.
- **Updates:** checks `GET /api/launcher/update`, downloads the APK from your backend only, verifies size + SHA-256, then opens Android's installer (the first time, Android asks you to allow installs from V-Client).

Minecraft Java Edition cannot run on Android, so **Play** uses the `minecraft://connect` link of Minecraft Bedrock (`com.mojang.minecraftpe`). If Minecraft is not installed the app offers the Play Store page.

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

Raise `versionCode` and `versionName` in `app/build.gradle.kts`, build the signed APK, and upload it in the admin panel (**Updates**, platform `android`, the same version string as `versionName`). Phones on an older version are offered the update at next launch.

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
