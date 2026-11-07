# Instructions for AI coding agents

This file is for coding agents (Claude Code, Codex, Cursor and similar) working in this
repo. Humans: see [README.md](README.md), [ios/README.md](ios/README.md) and
[android/README.md](android/README.md).

## Installing the iPhone app for the user

If the user asks you to set up, build, install or "import" the app onto their iPhone,
follow these steps in order. Do as much as you can yourself. Some steps can only be done
by the user, in Xcode or on the phone; they are marked **USER**. When you reach one,
stop, tell the user exactly what to tap, and wait for them to confirm before carrying on.

**Never** ask for, type or store the user's Apple ID password. Signing in happens only in
Xcode's own window.

### 0. Check the machine

This needs macOS. On Windows or Linux, don't build: follow
[Installing the iPhone app without a Mac](#installing-the-iphone-app-without-a-mac) instead.

```sh
xcodebuild -version          # fails if full Xcode isn't installed
xcode-select -p              # must point inside Xcode.app, not CommandLineTools
xcodebuild -showsdks | grep iphoneos
```

- No Xcode: **USER** installs Xcode from the Mac App Store and opens it once.
- `xcode-select` points at CommandLineTools: **USER** runs
  `sudo xcode-select -s /Applications/Xcode.app` (it needs their password).
- No `iphoneos` SDK: run `xcodebuild -runFirstLaunch`, then `xcodebuild -downloadPlatform iOS`
  (several GB; run it in the background).

### 1. Get the code

If you aren't already in a clone of this repo:

```sh
git clone https://github.com/Tutu2652/london-climbing-finder.git
cd london-climbing-finder
```

### 2. Find the user's team ID

```sh
security find-certificate -a -c "Apple Development" -p | awk '/BEGIN CERT/{c=""} {c=c $0 "\n"} /END CERT/{printf "%s", c | "openssl x509 -noout -subject"; close("openssl x509 -noout -subject")}'
```

The team ID is the 10-character `OU=` value. If more than one appears, show them (with
the `CN=` names) and ask the user which to use.

If nothing is printed, the user hasn't signed in to Xcode yet. **USER**: Xcode →
Settings → Accounts → **+** → Apple ID → sign in. Then select the account →
**Manage Certificates…** → **+** → **Apple Development**. Run the command again.

A certificate showing up doesn't prove the user is still signed in: it stays in the
keychain after the Apple ID is removed from Xcode. Step 5 catches that case.

### 3. Write the signing config

Create `ios/Signing.local.xcconfig` (it is git-ignored):

```
DEVELOPMENT_TEAM = <team ID from step 2>
BUNDLE_ID_PREFIX = com.<something unique to the user>
```

Ask the user for the prefix, or suggest one from their name (for example
`com.janedoe`). Choose it once and keep it. A free Apple ID can only register 10 app IDs
per week, and this app uses two (the app and its Live Activity extension), so changing
the prefix repeatedly can lock the user out for a week.

Do **not** set the team or bundle ID in the Xcode project (`project.pbxproj`) or in
Xcode's Signing & Capabilities tab. The project reads both from `ios/Signing.xcconfig`,
which loads the local file above.

Check that it took effect:

```sh
xcodebuild -project ios/ClimbingFinder.xcodeproj -target ClimbingFinder -showBuildSettings | grep -E ' (DEVELOPMENT_TEAM|PRODUCT_BUNDLE_IDENTIFIER) ='
```

### 4. Connect the iPhone

**USER**:
1. Plug the iPhone into the Mac with a cable, unlock it, and tap **Trust**.
2. On the phone: Settings → Privacy & Security → **Developer Mode** → on. The phone
   restarts; confirm when asked.

Then find the device identifier:

```sh
xcrun devicectl list devices
```

Use the `Identifier` column of the iPhone's row. If it isn't listed, or its state isn't
available/connected, ask the user to unlock the phone and check the cable.

### 5. Build

```sh
xcodebuild -project ios/ClimbingFinder.xcodeproj -scheme ClimbingFinder \
  -destination 'generic/platform=iOS' -derivedDataPath build/agent \
  -allowProvisioningUpdates build
```

`-allowProvisioningUpdates` lets Xcode create the signing profile with the user's Apple
ID. Common failures:

- "No Accounts: Add a new account in Accounts settings": no Apple ID is signed in to
  Xcode. **USER**: Xcode → Settings → Accounts → **+** → Apple ID → sign in. Then rebuild.
- "No Account for Team" / "No signing certificate": go back to step 2.
- "The app identifier … cannot be registered" / "is not available": the bundle ID is
  taken. Ask the user for a different `BUNDLE_ID_PREFIX` (see the limit in step 3).
- "Maximum number of apps" (free accounts allow 3 sideloaded apps per device): **USER**
  deletes one they installed this way, then rebuild.

### 6. Install and launch

```sh
xcrun devicectl device install app --device <identifier> build/agent/Build/Products/Debug-iphoneos/ClimbingFinder.app
xcrun devicectl device process launch --device <identifier> <PRODUCT_BUNDLE_IDENTIFIER from step 3>
```

The first launch fails because the phone doesn't trust the developer yet. **USER**: on
the phone, Settings → General → VPN & Device Management → their Apple ID → **Trust**.
Then run the launch command again; it should open the app.

### 7. Tell the user

- With a free Apple ID the app stops opening after **7 days**. To renew it, connect the
  phone and repeat steps 5 and 6, or set up `ios/tools/auto-reinstall.sh` (see
  ios/README.md) to do it automatically.
- To get new gyms later: `git pull`, then steps 5 and 6. The app also refreshes prices
  from the website on its own.

Don't commit anything during setup. `ios/Signing.local.xcconfig` and `build/` must stay
out of git.

## Installing the iPhone app without a Mac

On Windows or Linux (or a Mac without Xcode, if the user prefers), the user installs the
release file with SideStore, which renews it every 7 days on the phone by itself. Nothing
is built here. Most steps happen on the phone or in iloader's window, so most are
**USER**: say exactly what to do and wait.

**Never** ask for, type or store the user's Apple ID password. They type it into iloader
and SideStore themselves. If they're uneasy about that, suggest a second Apple ID made
just for this.

1. Check a release has the iPhone file:
   `gh release view -R Tutu2652/london-climbing-finder --json assets -q '.assets[].name'`
   must list `london-climbing-finder.ipa` and `altstore-source.json`. If not, tell the
   user it isn't available yet and stop.
2. **USER** installs SideStore on the phone with the official guide,
   https://docs.sidestore.io/docs/installation/prerequisites (iloader on the computer,
   LocalDevVPN on the phone, Wi-Fi). On Windows, iloader also needs iTunes; on Linux,
   `usbmuxd`. You may install those for the user if they agree. Follow the guide's current
   steps rather than guessing; read the page if unsure.
3. **USER**, in SideStore: **Sources** → **+** → add
   `https://github.com/Tutu2652/london-climbing-finder/releases/latest/download/altstore-source.json`,
   then install **Climb Finder** from it with LocalDevVPN on.
4. Tell the user what ios/README.md → "Install it without a Mac" says about renewing,
   updates, the 3-app limit and moving a logbook from a Mac install.

## Installing on Android

If the user asks you to build or install the Android app, follow these steps. Steps on
the phone are marked **USER**: stop, say exactly what to tap, and wait for them.

### 0. Check the machine

Any OS works. You need JDK 17 or newer and the Android SDK.

```sh
java -version
echo $ANDROID_HOME     # else look in ~/Library/Android/sdk (macOS), ~/Android/Sdk (Linux)
```

If either is missing, ask the user before downloading: the JDK and SDK together are
about 1.5 GB. Install a JDK (`brew install openjdk@17`, or the OS package), then Google's
command-line tools from developer.android.com/studio#command-tools, unzipped to
`$ANDROID_HOME/cmdline-tools/latest`. Then:

```sh
sdkmanager --licenses      # the user accepts Google's SDK licence
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### 1. Build

```sh
cd android && ./gradlew assembleDebug
```

The APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

### 2. Install

Without a cable, send the APK to the phone and walk the user through the steps in
[android/README.md](android/README.md#install-it-on-your-phone-no-mac-needed).

With a cable:

1. **USER**: Settings → About phone → tap **Build number** seven times. Then Settings →
   System → Developer options → turn on **USB debugging**. Plug in the phone and tap
   **Allow** on the prompt.
2. Run `adb devices` and find the phone's serial. Always pass `-s <serial>`: TVs and
   other devices on the network can show up in adb too.
3. `adb -s <serial> install -r android/app/build/outputs/apk/debug/app-debug.apk`

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the installed copy has a different signing
key: one of the two was built without the shared key (see android/README.md → Signing
key; check `~/.gradle/gradle.properties`). Uninstalling deletes the user's logbook, so first
ask them to back it up (More → Back up logbook), and only uninstall
(`adb -s <serial> uninstall io.github.tutu2652.climbingfinder`) once they say so.

### 3. Tell the user

- The install doesn't expire.
- To get new gyms later: `git pull`, then steps 1 and 2. The app also refreshes prices
  from the website on its own.

Don't commit anything during setup. `android/local.properties`, build folders and any
signing key or password stay out of git.

## Changing the code or data

- `gyms.json` is the only source of data. After editing it, run
  `python3 tools/sync_data.py` and `python3 -m unittest discover -s tests`, and commit the
  generated files with it. Never hand-edit `assets/data.js`, `gyms/*/index.html`, the
  `.ics` calendars, `sitemap.xml` or the data inside `london_climbing_dashboard.html`.
- Pricing logic exists four times (`assets/app.js`, `london_climbing_prices.py`,
  `ios/ClimbingFinder/Core/Pricing.swift`,
  `android/core/src/main/kotlin/io/github/tutu2652/climbingfinder/core/Pricing.kt`) and the
  tests check they agree. Change all four together.
- See README.md → "Updating the data" for the rules on adding gyms and offer end dates.
- `README.md` has a Simplified Chinese copy, `README.zh-CN.md`, with the same sections in
  the same order. When you change one, make the same change in the other in the same
  commit. Keep gym names, file and field names, and commands in English in both.
  `README.md` is authoritative if the two disagree.

## Releasing a new version

Friends install the APK from GitHub Releases through one fixed link,
`https://github.com/Tutu2652/london-climbing-finder/releases/latest/download/london-climbing-finder.apk`,
and the app tells them about updates through `gyms.json`. Follow these steps in order, and
ask the user before publishing a release (it is public).

1. Raise `versionCode` (by 1) and `versionName` in `android/app/build.gradle.kts`.
   Commit and push to `main`, and wait for CI to pass.
2. Download that CI run's APK (`gh run download <run id> -n climbing-finder-android`).
   Check it with `apksigner verify --print-certs`: it must be signed with the shared key,
   SHA-256 `2c22927244f368e02a392f0d98dc032b92097cec6eea3e1ef2a18691c3cae932`.
   An APK signed with any other key won't install over earlier versions.
3. Rename it `london-climbing-finder.apk` and create the release:
   `gh release create v<versionName> london-climbing-finder.apk --target <full commit SHA> --title "…" --latest`.
   - Don't mark it as a pre-release: `releases/latest` skips pre-releases, so the link
     would break.
   - Versions below 1.0 are test versions; say so in the title.
   - Publishing the release also builds the iPhone file: the **iPhone release file**
     workflow attaches `london-climbing-finder.ipa` and `altstore-source.json` within
     about 5 minutes. Check it passed (`gh run list -w ios-release.yml`). The tag must
     look like `v0.4`: the iPhone app's version is taken from it.
4. Only after the release exists, set `_meta.android_app` in `gyms.json` (`version_code`,
   `version_name`) to the new version. Then run `python3 tools/sync_data.py` and the tests,
   commit and push. Once the website redeploys, older copies of the app show an
   **Update** banner. A test stops `version_code` going above the one in
   `build.gradle.kts`. Announcing a version before its release exists sends people to a
   missing or older file.

To check it worked, run
`curl -sL <the link above> | shasum -a 256`. The hash must match the file you uploaded.
