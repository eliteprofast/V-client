# V-Client

V-Client is a native Windows desktop launcher built with Java 21 and JavaFX. It installs and launches Minecraft inside its own private game folder, can launch installed vanilla, Fabric, or Forge profiles, lets you choose the game's maximum memory, and saves launcher preferences locally.

The launcher uses a live, animated pixel-night background with twinkling and drifting stars, a glowing crescent moon, and layered dusk hills. The midnight navy and violet interface uses warm gold highlights and translucent panels; the animation is drawn natively with JavaFX and does not load a web page or remote background.

The left navigation provides Home, Library, Mods, Boost, Wardrobe, Friends, Console, Settings, and Discord pages. Minecraft game files are kept in V-Client's private `%APPDATA%\V-Client\minecraft` folder; account/settings data stays in `%APPDATA%\V-Client`. The Home version dropdown refreshes the Mojang catalog dynamically and lists its latest release, latest snapshot, all available releases/snapshots, and installed loader profiles with Installed, Not Installed, or Downloading status. Selecting a version that is not installed automatically downloads its manifest, client, libraries, assets, and required Java runtime into V-Client's private folder. **REFRESH VERSIONS** reloads the catalog and installed profiles. V-Client does not need another launcher's folder. **IMPORT EXISTING GAME FILES** in Settings is optional; it copies local `versions`, `libraries`, and `assets` without deleting the source. Library scans only V-Client's private game folder. Mods lets you pick an installed Fabric, Forge, NeoForge, or Quilt profile, search Modrinth for compatible mods, and click **INSTALL** to download the verified JAR straight into the game folder's `mods` directory (created automatically on the first install); it also lists installed mods and can open the folder or remove a mod. To play a server, select the version supported by that server and use **JOIN OUR SMP**. Boost saves the Minecraft memory limit and does not modify Windows or promise FPS gains. Wardrobe previews the skin and cape already saved on the selected account. Friends is a local-only list of Minecraft usernames, saved in `%APPDATA%\V-Client\friends.json`. Console shows the current session's launcher/game output and can copy or clear it. Discord lets you save and open an invite URL you provide; V-Client does not invent or automatically contact an invite.

## Download and run

Download `AzyrClient.zip` and extract the complete archive to a folder. Open the extracted `Release` folder and double-click `V-Client.exe`. Keep the `app` and `runtime` folders next to the executable; they contain files required to run the launcher and Minecraft. The included runtimes mean you do not need to install Java.

On first launch, add a Microsoft Premium, Ely.by, or offline account. Microsoft uses Microsoft's device-code sign-in; Ely.by uses its authentication service and supports two-factor verification. Ely.by sign-in completes independently of skin/cape lookups, which load in the background; a skin-service delay will not block account sign-in. Offline accounts use a username and generated offline UUID. Multiple accounts can be saved, switched, and removed in Settings. Microsoft and Ely.by access tokens are encrypted with Windows DPAPI for the current Windows user; account passwords are never saved. Minecraft requires a valid Premium account for online-mode servers. Offline accounts are for offline-mode servers and single-player.

If Windows DPAPI takes longer than 15 seconds to save a newly signed-in account, the launcher reports that sign-in succeeded and keeps the account available for the current session; the secure save continues in the background. If Windows eventually completes the save, the launcher confirms it. If secure storage remains blocked, the account is not guaranteed to persist after closing the launcher.

The Windows release includes one Azul Zulu Java 21 x64 runtime, used by both the launcher and Minecraft versions that need Java 21 (including the V-Client 1.21.11 profile). Build the release with `JAVA_HOME` set to an Azul Zulu JDK 21 so the bundled runtime passes the launcher's vendor check. Minecraft 1.21.1 uses Java 25. Settings can use the Minecraft profile's default runtime (Java 25 is automatic for 1.21.1) or force Java 25 for every version. V-Client detects compatible Java 25 installations in `JAVA_HOME`, `PATH`, and common Windows installation folders; when Java 25 is not installed, it downloads the official Azul Zulu x64 JRE into `%APPDATA%\V-Client\runtimes`. For versions requiring Java 8, 17, or 21, V-Client downloads the matching official Azul Zulu x64 JRE if needed. Downloaded runtimes are checked for vendor, version, and architecture before launching.

Installed vanilla, Fabric, and Forge profiles in V-Client's private folder appear in the version list. You can choose a different existing game folder in Settings or optionally import its `versions`, `libraries`, and `assets` into V-Client's folder. Profile folder names with spaces, including `Fabric 1.21.11`, are supported. **JOIN OUR SMP** launches with the selected account as-is and passes `neuron.zeroxhost.space:19145` to Minecraft for automatic multiplayer connection. Neither main button refreshes account credentials. Missing Minecraft files and Java runtimes are downloaded into V-Client's own folders when needed; Minecraft itself must connect to the SMP to join it, and server authentication requirements still apply. When using an Ely.by account, V-Client uses the cached authlib-injector with Ely.by's `https://authserver.ely.by` API URL, downloading it into V-Client's libraries folder if needed.

**PLAY** starts the selected profile for singleplayer using whichever account is selected, including Ely.by or Offline. If nothing is installed, it installs the latest stable Vanilla release first. Missing version files, libraries, assets, and Java runtimes are downloaded as needed. An optional logging configuration can be omitted when it is not available. Minecraft itself may still make network connections, such as connecting to the selected multiplayer server; V-Client does not change Windows firewall settings or promise to block the game's network traffic.

For launch troubleshooting, V-Client writes Java version details, redacted JVM/game arguments, manifest and classpath validation, library/asset progress, and Minecraft process output to `%APPDATA%\V-Client\minecraft\logs\vclient-launch.log` (or the configured game folder's `logs` directory). Failed launches show the underlying exception and include recent Minecraft output and the latest crash report when present. Authentication tokens are redacted from recorded launch arguments.

V-Client explicitly enables TLS 1.3 and TLS 1.2 and validates HTTPS certificate chains against both the Windows trusted-root store and Java's standard trust store. Mojang and Azul metadata/download requests retry transient connection failures and temporary HTTP server responses; certificate/TLS failures are not retried as transient errors. System proxy settings are preferred, with HTTP(S)_PROXY environment variables detected as a fallback. Proxy credentials are never written to the launcher log.

Use **Test Connection** on the home screen to check DNS resolution, general HTTPS connectivity, Mojang's certificate chain, and the official Mojang version manifest. The dialog and launch output show the selected proxy route, enabled TLS protocols, response status, and underlying network/TLS error. If HTTPS inspection by a proxy, firewall, or antivirus causes a certificate failure, configure that product's verified root certificate in the Windows trusted store or ask your administrator; V-Client never disables certificate verification.

**OFFLINE MODE** on the Home screen makes V-Client contact no one: it skips the Mojang version list, Modrinth, skin/cape downloads, and all game-file and Java downloads, and launches installed versions using only local files. Turn it off once if you still need to download a version or Java runtime. Even with Offline mode off, if Mojang cannot be reached the version list falls back to your installed versions instead of blocking you.

The first time you run the unsigned application, Windows SmartScreen may ask you to confirm that you trust it.

## Build the complete Windows release

On Windows, install a 64-bit JDK 21 with `JAVA_HOME` set and Maven on `PATH`. In PowerShell, run:

```powershell
.\package-release.ps1
```

This compiles the project without deleting Maven's existing dependency files, builds the self-contained Windows app image with its JavaFX application, Java runtime, and custom icon, stages the complete release in `Release`, copies the source into `Release\Source`, and generates `AzyrClient.zip` in the repository root. Existing files in `Release` that are not part of the new build are preserved. The resulting executable is `Release\V-Client.exe`. Build on Windows; `jpackage` produces an app for its host operating system.

Run the desktop launcher directly from source with:

```powershell
mvn javafx:run
```

## Local data

Settings are saved as JSON in `%APPDATA%\V-Client\settings.json`. Microsoft and Ely.by credentials are DPAPI-protected in `%APPDATA%\V-Client\accounts.dat` and can only be decrypted by the same Windows user on the same device. Offline usernames are saved separately in `%APPDATA%\V-Client\offline-accounts.json`; they contain no passwords or access tokens.

## V-Client in-game mod (Minecraft 1.21.11)

When Minecraft 1.21.11 is installed in V-Client's game folder, the launcher adds a **V-Client 1.21.11** profile and selects it by default. The profile runs Fabric Loader with the V-Client mod, and the launcher copies both from files bundled inside it, so nothing is downloaded. Mods installed from the Mods tab load in this profile too.

- Title screen and menus use the launcher's animated night sky (stars, crescent moon, comet, hills).
- **Right Shift** in game opens the V-Client mod menu: FPS, CPS, Ping, Coordinates, Keystrokes, Armor Status, Zoom (hold **C**), Fullbright, Toggle Sprint, Toggle Sneak, and the night-sky background. Click a card to toggle it; **EDIT HUD** lets you drag HUD elements, and **RESET HUD** restores default positions. Settings are saved in the game folder's `config/vclient.json`.

The mod's source is in `mod/`. `mod/build-mod.sh` (Git Bash, with `JAVA_HOME` set to a JDK 21) builds Fabric Loader 0.19.5 and its tools from source using only GitHub and Maven Central, compiles the mod against the local 1.21.11 client jar with Yarn names, and stages the result in `src/main/resources/vclient-fabric`. Run it before `package-release.ps1` whenever the mod changes.
