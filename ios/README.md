# London Climbing Finder — iPhone app

A native SwiftUI version of the site. It reads the same `gyms.json` (bundled at build
time, so it works offline) and its pricing code is a line-for-line port of
`assets/app.js`; `tests/test_pricing.py` checks the Swift, JS and Python pricing agree.

Tabs: **Cheapest** (ranked for any date/time, adult or concession, with the time window
to go), **Map**, **Gyms** (by chain / price / distance, searchable), **Deals** (limited
offers, student rates, a weekly calendar of cheap slots), **More** (memberships, classes,
socials & events, your location). Each gym has a detail page with prices, every deal,
opening hours, links, public-transport directions in the Google Maps app and your own
note, star rating and favourite heart, saved on the phone. Prices update from the website
when you open the app and are kept for offline use.

Not ported: Google sign-in sync and public community reviews (they need Firebase in the
app); notes stay on the device.

## Run it on your iPhone (free, personal use)

You need a Mac. Any Apple ID works; no paid developer account is needed.

**Using an AI coding agent** (Claude Code, Codex, Cursor…)? Open it in this repo and ask it
to "install the iPhone app on my phone". [AGENTS.md](../AGENTS.md) walks it through every
step below, and it tells you when you need to tap something in Xcode or on the phone.

1. Install **Xcode** from the Mac App Store, open it once, and let it install the iOS
   platform it offers.
2. Get the code: `git clone https://github.com/Tutu2652/london-climbing-finder.git`
   (or GitHub → Code → Download ZIP).
3. Xcode → Settings → Accounts → **+** → sign in with your Apple ID. Select it →
   **Manage Certificates…** → **+** → **Apple Development**.
4. Find your team ID (10 characters):
   ```sh
   security find-certificate -c "Apple Development" -p | openssl x509 -noout -subject
   ```
   It is the `OU=` value.
5. Create `ios/Signing.local.xcconfig` with your team ID and a prefix that is yours
   (it only has to be unique, e.g. based on your name):
   ```
   DEVELOPMENT_TEAM = ABCDE12345
   BUNDLE_ID_PREFIX = com.yourname
   ```
   The file is git-ignored, so pulling updates later never conflicts with it. Don't set
   the team in Xcode's Signing & Capabilities tab instead: that edits the shared project.
6. Open `ios/ClimbingFinder.xcodeproj`. Plug in the iPhone and tap **Trust**. On the
   phone, turn on Settings → Privacy & Security → **Developer Mode** (it restarts).
7. Pick the iPhone as the run destination at the top of Xcode and press **Run** (⌘R).
8. The first time, the phone blocks the app: Settings → General → VPN & Device
   Management → your Apple ID → **Trust**.

To get new gyms and prices later: `git pull`, then Run again. (The app also refreshes
prices from the website by itself.)

With a free Apple ID the install lasts **7 days**. A paid Apple Developer Program
membership ($99/yr) makes installs last a year and allows TestFlight.

### Keeping the free install alive automatically

`tools/auto-reinstall.sh <device-udid>` renews the 7-day signing profile when it is within
3 days of expiring and reinstalls the app over Wi-Fi (or USB). Run it from a LaunchAgent
twice a day; it does nothing when nothing is due, and retries if the phone is away. The
phone must have been paired with Xcode by cable once, and be on the same Wi-Fi as the Mac.
`--force` reinstalls the current code straight away. Find the UDID with
`xcrun devicectl list devices`; logs go wherever the LaunchAgent sends them.

## Exporting to Google Maps

More → Export to Google Maps writes a KML file of the gyms with your notes, star ratings
and favourites. Import it at mymaps.google.com (on a computer); the map then appears in
the Google Maps app under You → Maps. It is a snapshot: export again after editing notes.

## Updating prices

Edit `gyms.json` as usual, then rebuild in Xcode. The app has no copy of its own.
Bank holidays are fetched live from gov.uk (the bundled `bank_holidays.json` is the
offline fallback); travel times come from TfL's Journey Planner.

## Logbook and visits

The **Log** tab stores sessions and climbs locally with SwiftData. Start a session
(with location access, it suggests the gym you are at, within 500 m), choose Flash / Send / Attempt, then tap grades. Turn on **Comp routes** to tag climbs
as competition problems: tap the grade it felt, or **No grade**. Tap a climb later to
add or change its grade. Ungraded comp climbs count as sends but stay out of grade
stats. Finished sessions open a visit detail
screen with entry price, duration, feeling, notes and climbs; **Edit** changes the visit.
**Visits by gym** lists visit counts and opens each gym's monthly visit history and
totals. The same history is available from a gym page and Stats.

Stats includes grade pyramids, progress, weekly sessions and a membership comparison
using logged entry prices and the listed membership price. Independent gyms are
compared separately. Membership/pass visits do not count as paid day entries.

Use **More → Back up logbook** to export JSON or restore missing sessions. Restore
validates the file before importing and skips duplicate session IDs. Live sessions
in backups are restored as finished at their last logged climb (or start if empty).

Run the in-memory logbook regression checks on a Mac with Xcode:

```sh
xcrun swiftc -parse-as-library ios/ClimbingFinder/Core/Logbook.swift ios/ClimbingFinder/Core/ColourGrades.swift ios/Tests/LogbookChecks.swift -o /tmp/climbing-logbook-checks
/tmp/climbing-logbook-checks
```

### Route photos and climb videos

Tap a climb in **Climbs & media** during a session, or **Climbs in order** on a
finished visit. Link one route photo and one video from Photos. Both flashes/sends
and attempts support media. Each link can be replaced or removed; unlinking never
deletes the original from Photos.

**Highlights** at the top of the Log tab shows the ten newest linked photos and videos;
**See all** opens a grid of every one, filterable by photos or videos, gym, and sends
only. Tapping an item opens its climb. A climb with both a photo and a video shows twice.
An original that is deleted or outside limited access shows as a placeholder; tap it to
relink or remove the link. The grid asks for Photos access when it opens if the app
hasn't asked yet; the strip never asks. Only thumbnails are loaded.

Grant Photos access when first linking an item. Limited access works: use **Manage
allowed photos & videos** to allow the item, then select it with **Link from Photos**.
The app stores only Photos asset identifiers. Photos handles display/playback and
may download iCloud originals; the app does not export or retain duplicate media files.
A missing original or revoked permission shows recovery controls instead of removing
the link. These local identifiers may need relinking on a different phone/library.

JSON backups include the references, not media files. Version 3 adds the comp tag;
version 4 adds colour grades (each climb's colour and the range it was logged with, plus
any gym colours you changed). Version 1–3 backups remain supported. Run `zsh ios/Tests/check-logbook.sh` for backup, on-disk migration,
and reference persistence checks. The migration fixture freezes the pre-media schema.

Manual device checks (requires Photos access and sample media):

- Open a live or finished visit, tap a climb, link a route photo and video, then reopen
  the app and verify both remain linked and display/play.
- Try limited Photos access, adding an item via Manage allowed photos & videos.
- Cancel either picker; verify any previous link remains unchanged.
- Replace and unlink each media type; verify originals remain in Photos.
- Remove Photos access or delete a test original; verify the unavailable message and
  replace/remove controls. Try an iCloud item offline, then Reload when online.

## Screenshots

`ios/screenshots/` holds the App Store / advert images (1290×2796, the 6.7" size): a cover
plus ten framed screens with headlines. To remake them after the app or prices change
(needs a Mac and `pip install pillow`):

1. Build and run the app in a simulator, then run
   `python3 ios/tools/ad_screenshots.py demo`. This backs up the simulator's app data,
   sets the start to Trafalgar Square, fills the logbook with made-up sessions and sets a
   clean 9:41 status bar.
2. Capture each screen with `xcrun simctl io booted screenshot build/screenshots/<name>.png`,
   where `<name>` is `cheapest`, `map`, `deals` (More → Deals → Weekly), `gym`,
   `gyms`, `logbook` or `stats`. For the logging shots, start a session at Climbing
   District Bermondsey (its colours have V ranges) and log a few climbs: `session` is the
   top of that screen, `session-tally` is scrolled down to the tally, and `climb` is one
   climb opened with a felt grade set. The app saves new climbs a few seconds after you
   tap, so wait before quitting it.
3. `python3 ios/tools/ad_screenshots.py compose` writes the framed images. To remake only
   some, name them: `compose stats`.
4. `python3 ios/tools/ad_screenshots.py restore` puts the simulator's own data back.

Headlines and the demo logbook are at the top of `ios/tools/ad_screenshots.py`. Never
use a real home address as the start location in these images.
