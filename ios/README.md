# London Climbing Finder — iPhone app

A native SwiftUI version of the site. It reads the same `gyms.json` (bundled at build
time, so it works offline) and its pricing code is a line-for-line port of
`assets/app.js`; `tests/test_pricing.py` checks the Swift, JS and Python pricing agree.

Tabs: **Cheapest** (ranked for any date/time, adult or concession, with the time window
to go), **Map**, **Gyms** (by chain / price / distance, searchable), **Deals** (limited
offers, student rates, a weekly calendar of cheap slots), **More** (memberships, classes,
socials & events, your location). Each gym has a detail page with prices, every deal,
opening hours, links, public-transport directions in the Google Maps app and your own
note and star rating, saved on the phone.

Not ported: Google sign-in sync and public community reviews (they need Firebase in the
app); notes stay on the device.

## Run it on your iPhone (free, personal use)

1. Install **Xcode** from the Mac App Store, open it once, and let it install the iOS
   platform it offers.
2. Open `ios/ClimbingFinder.xcodeproj`.
3. Xcode → Settings → Accounts → **+** → sign in with your Apple ID.
4. Select the **ClimbingFinder** target → **Signing & Capabilities** → Team: your
   "(Personal Team)". If Xcode says the bundle ID is taken, change it to anything unique,
   e.g. `com.yourname.climbfinder`.
5. Plug in the iPhone and tap **Trust**. On the phone, turn on
   Settings → Privacy & Security → **Developer Mode** (it restarts).
6. Pick the iPhone as the run destination at the top of Xcode and press **Run** (⌘R).
7. The first time, the phone blocks the app: Settings → General → VPN & Device
   Management → your Apple ID → **Trust**.

With a free Apple ID the install lasts **7 days**; after that, plug in and press Run again.
A paid Apple Developer Program membership ($99/yr) makes installs last a year and allows
TestFlight.

## Updating prices

Edit `gyms.json` as usual, then rebuild in Xcode. The app has no copy of its own.
Bank holidays are fetched live from gov.uk (the bundled `bank_holidays.json` is the
offline fallback); travel times come from TfL's Journey Planner.
