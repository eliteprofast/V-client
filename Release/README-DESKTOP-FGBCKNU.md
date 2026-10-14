# V-Client

V-Client is a native Windows desktop launcher built with Java 21 and JavaFX. It discovers Minecraft installations in the standard `.minecraft` folder, launches installed vanilla, Fabric, or Forge profiles, lets you choose the game's maximum memory, and saves launcher preferences locally.

## Download and run

Download `AzyrClient.zip` and extract the complete archive to a folder. Open the extracted `Release` folder and double-click `V-Client.exe`. Keep the `app`, `runtime`, and `runtimes` folders next to the executable; they contain files required to run the launcher and Minecraft. The included runtimes mean you do not need to install Java.

On first launch, add a Microsoft Premium, Ely.by, or offline account. Microsoft uses Microsoft's device-code sign-in; Ely.by uses its authentication service and supports two-factor verification; offline accounts use a username and generated offline UUID. Multiple accounts can be saved, switched, and removed in Settings. Microsoft and Ely.by access tokens are encrypted with Windows DPAPI for the current Windows user; account passwords are never saved. Minecraft requires a valid Premium account for online-mode servers. Offline accounts are for offline-mode servers and single-player.

The Windows release includes an Azul Zulu Java 21 x64 runtime for Minecraft versions that require Java 21. The launcher itself has a separate bundled Java runtime. When an installed Minecraft profile requires Java 8 or 17, V-Client downloads the matching official Azul Zulu x64 JRE and verifies its vendor, version, and architecture before launching.

Installed vanilla, Fabric, and Forge profiles in the selected `.minecraft` folder appear in the version list. Select a profile and press **Play**: V-Client resolves the profile's version inheritance, checks and downloads missing Mojang game, library, and asset files, then starts Minecraft with the matching 64-bit Azul Zulu Java runtime and the profile's official JVM and game arguments. An internet connection is required for missing files and account sign-in. If no profile is installed, the home screen offers **Install Latest Minecraft Release**; install that profile before pressing Play. Fabric and Forge profiles can be installed using their official installers.

V-Client explicitly enables TLS 1.3 and TLS 1.2 and validates HTTPS certificate chains against both the Windows trusted-root store and Java's standard trust store. Mojang and Azul metadata/download requests retry transient connection failures and temporary HTTP server responses; certificate/TLS failures are not retried as transient errors. System proxy settings are preferred, with HTTP(S)_PROXY environment variables detected as a fallback. Proxy credentials are never written to the launcher log.

Use **Test Connection** on the home screen to check DNS resolution, general HTTPS connectivity, Mojang's certificate chain, and the official Mojang version manifest. The dialog and launch output show the selected proxy route, enabled TLS protocols, response status, and underlying network/TLS error. If HTTPS inspection by a proxy, firewall, or antivirus causes a certificate failure, configure that product's verified root certificate in the Windows trusted store or ask your administrator; V-Client never disables certificate verification.

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
