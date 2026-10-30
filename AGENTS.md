# Instructions for AI coding agents

This file is for coding agents (Claude Code, Codex, Cursor and similar) working in this
repo. Humans: see [README.md](README.md) and [ios/README.md](ios/README.md).

## Installing the iPhone app for the user

If the user asks you to set up, build, install or "import" the app onto their iPhone,
follow these steps in order. Do as much as you can yourself. Some steps can only be done
by the user, in Xcode or on the phone; they are marked **USER**. When you reach one,
stop, tell the user exactly what to tap, and wait for them to confirm before carrying on.

**Never** ask for, type or store the user's Apple ID password. Signing in happens only in
Xcode's own window.

### 0. Check the machine

This needs macOS. On Windows or Linux, stop: tell the user the iPhone app can't be built
there, and suggest opening https://london-climbing-finder.pages.dev/ in Safari on the
iPhone, then Share → **Add to Home Screen**.

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

## Changing the code or data

- `gyms.json` is the only source of data. After editing it, run
  `python3 tools/sync_data.py` and `python3 -m unittest discover -s tests`, and commit the
  generated files with it. Never hand-edit `assets/data.js`, `gyms/*/index.html`, the
  `.ics` calendars, `sitemap.xml` or the data inside `london_climbing_dashboard.html`.
- Pricing logic exists three times (`assets/app.js`, `london_climbing_prices.py`,
  `ios/ClimbingFinder/Core/Pricing.swift`) and the tests check they agree. Change all three
  together.
- See README.md → "Updating the data" for the rules on adding gyms and offer end dates.
