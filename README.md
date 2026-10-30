# London Climbing Finder

English | [简体中文](README.zh-CN.md)

Day-pass prices, off-peak deals, student rates, memberships and travel times for 33
London climbing gyms. Pick a date and time and it shows the cheapest gym and when to go.

**Website:** https://london-climbing-finder.pages.dev/

## Ways to use it

| | What you need | How |
|---|---|---|
| **Website** | A browser | Open the link above. Works on phones. |
| **iPhone app** | A Mac with Xcode and any Apple ID | Follow [ios/README.md](ios/README.md#run-it-on-your-iphone-free-personal-use), about ten minutes the first time, or ask an AI coding agent to do it ([AGENTS.md](AGENTS.md)). |
| **Offline dashboard** | A browser | Download and double-click `london_climbing_dashboard.html`. |
| **Command line** | Python 3 (no packages) | `python3 london_climbing_prices.py` (see below). |
| **Deal calendars** | A calendar app | Subscribe from the website, or add `london-climbing-deals-all.ics` (or `-no-student.ics`). |

No Mac (for example on Windows) but want it on an iPhone? Open the website in Safari on
the iPhone and tap Share → **Add to Home Screen**. It gets an icon and opens full-screen,
like an app.

### Website and dashboard

- **Cheapest** ranks every gym for a date. By default it uses the cheapest price you can
  get at any time that day and tells you the window to go. Switch "Cheapest mode" to
  price one specific time.
- **Map** plots every gym, sorts by distance from a start location, and links to Google
  Maps transit directions. The **Travel** column shows live public-transport times from
  TfL's Journey Planner.
- **Notes and star ratings** for each gym are saved in your browser. Signing in with
  Google (optional) syncs them across devices and lets you post a public review.
- Each gym also has its own plain page under `gyms/` with prices, deals, opening hours and
  links to the gym's official pages.

### Command line

```
python3 london_climbing_prices.py                                   # interactive menu
python3 london_climbing_prices.py cheapest --date 2026-07-04        # cheapest all day + when to go
python3 london_climbing_prices.py cheapest --date 2026-07-04 --time 19:00 --concession
python3 london_climbing_prices.py price castle --time 14:00         # one gym, with directions and your note
python3 london_climbing_prices.py directions "the arch"             # transit + Citymapper links
python3 london_climbing_prices.py travel --from "WC2N 5DU"           # TfL journey time to every gym
python3 london_climbing_prices.py offpeak                           # all off-peak / cheap slots
python3 london_climbing_prices.py students                          # student and concession rates
python3 london_climbing_prices.py promos                            # live limited-time offers (--all: include expired)
python3 london_climbing_prices.py memberships                       # monthly memberships
python3 london_climbing_prices.py zones                             # gyms by tube zone
python3 london_climbing_prices.py all                               # every gym: prices + facilities
python3 london_climbing_prices.py note castle great cafe, busy pm   # save a note (omit text to clear)
python3 london_climbing_prices.py rate castle 4                     # rate 1-5 (omit value to clear)
```

`travel` and `--from` default to WC2N 5DU.

## How prices work

- **Day passes only.** Prices are single pay-as-you-go entries in GBP, as researched on
  the `last_research_date` in `gyms.json`. Always check the gym's own site before you go.
- **Peak and off-peak.** Each gym has adult and concession rates. Peak is evenings and
  weekends; off-peak is weekday daytime. Where a gym doesn't publish its off-peak hours,
  off-peak is assumed to run on weekdays until 16:00, and the price is marked `~approx`.
- **Super off-peak** covers the special cheap slots that beat both: Bargain, Magic Time,
  Student Saver, super-off-peak windows and buy-one-get-one-free deals.
- **Bank holidays.** Deals that gyms exclude on bank holidays (Castle off-peak, LCC Magic
  Time, Mile End super off-peak, City Bouldering Monday Bargain and others) are dropped on
  those dates. The dates come live from gov.uk (England and Wales), with
  `bank_holidays.json` as the offline copy.
- **Flags.** `verify` means the gym didn't publish the price when it was researched, so
  click through and confirm it.

## Gyms covered

Each site is its own entry with its own prices and off-peak times:

- **Independents:** The Castle, Mile End, Yonder, Sen, Westway, The Reach, Rise, Substation
  Brixton, Gantry (Canada Water), Blocfit (Brixton), Muro (Peckham), Rhino Boulder
  (Bromley), Mount Palace (Crystal Palace), The Nest (Hayes), Yellow Spider (Carshalton)
- **City Bouldering:** Aldgate, White City, Stratford, plus Climbing Co Fulham
- **The Font:** Wandsworth, Borough, Hounslow
- **Climbing District:** Bermondsey (The Arch), Tottenham Hale (Stronghold), London Fields
- **London Climbing Centres:** VauxWall West and East, HarroWall, CroyWall, RavensWall,
  CanaryWall, BethWall, EustonWall

## Updating the data

`gyms.json` is the single source of truth. Every other copy (the website's
`assets/data.js`, the offline dashboard, the gym pages, the calendars and the sitemap) is
generated from it. Never edit those by hand.

After any change to `gyms.json`:

```
python3 tools/sync_data.py                       # regenerate every derived file
python3 -m unittest discover -s tests            # pricing tests (website, CLI and iOS must agree)
npm ci && python3 tools/accessibility_check.py   # optional; needs Playwright + Chromium
```

Commit everything the sync script touched. CI runs `sync_data.py --check` on every push
and fails if a generated file is out of date, so the site can never drift from the data.
Pushing to `main` redeploys the website automatically.

### Adding a gym

1. Copy an existing entry in `gyms.json` and change the fields.
2. Take `lat`/`lng` from the gym's Google Maps place pin (the `!3d…!4d…` numbers in the
   place page URL), not from a postcode, which can be hundreds of metres out.
3. Save the gym's logo as a PNG (256 px or smaller) in `assets/logos/` and set `logo` to
   that path. A test rejects web URLs, so the site and app never depend on a gym's website.
4. Set `_meta.last_research_date` to today. The iPhone app ignores a website copy of the
   data that isn't newer than its own, so without this it keeps the old gym list.
5. Run the three commands above.

### Keeping prices fresh

**Every Monday** the `price-check.yml` GitHub Action re-reads each gym's price page (in a
real browser, so JavaScript booking widgets work too) and opens an Issue if anything looks
different. You can also run it from the repo's Actions tab. It never edits data itself.
It reports two things:

- **TO REVIEW:** a stored headline price (adult or concession peak) no longer appears on
  the page, so it has probably changed.
- **NEW OFFERS:** promo wording ("10% off", "sale", "promo", "BOGOF") appears next to GBP
  amounts that aren't anywhere in `gyms.json`, so an offer has probably been added. A
  price check alone can't see this, because an offer leaves the old prices on the page.
  That's how City Bouldering's 10%-off summer sale was missed at first.

Recording a flagged offer in `limited_promotions` makes its prices "known", so it stops
being reported. Membership prices aren't checked this way (most price pages don't list
them), but membership deals still show up in the promo scan.

**To run the check yourself:**

```
python3 london_climbing_prices.py check             # quick: text-only pages
python3 london_climbing_prices.py check --browser   # also renders JavaScript pages (pip install playwright; playwright install chromium)
python3 tools/weekly_offer_check.py                 # check, then --draft / --apply to turn findings into gyms.json entries
```

`weekly_offer_check.py --draft` writes `offers-draft.json` skeletons for you to finish;
`--apply` merges them into `gyms.json`, syncs and tests, but never commits. A gym's
`scrape` block (`needs_js`, URL) controls whether its page needs browser rendering.

`cheapest --scrape` is a separate, best-effort live refresh. It only replaces a stored
price when it can tie a GBP amount (£3–40) to a "peak"/"off-peak" keyword, and keeps the
stored value for anything it can't read.

### Limited-time offers and end dates

Each `limited_promotions` entry has an `expires` date (`YYYY-MM-DD`) or `null`.

- **`null` means "no end date known",** not "runs forever". Most gyms never state one.
- **A date needs a source.** Whenever `expires` is set, `expires_source` must say where it
  came from: a URL, or something like `"in-gym poster, seen 2026-08-05"`. A test enforces
  this. Off-website sources are fine; guessing from an offer's name ("Summer 2026 sale")
  is not.
- **Don't confuse the two kinds of date.** "Valid for 3 months from purchase" is how long a
  pass lasts once bought, not when the offer stops being sold. Only the second is
  `expires`.
- **After `expires`, an offer is hidden everywhere** (`promos --all` still shows it), and
  its prices stop counting as known. If a gym is still advertising it after the end date,
  the weekly check flags it again.

## Repository layout

| Path | What it is |
|---|---|
| `gyms.json` | All the data. Edit only this. |
| `index.html`, `assets/` | The website: `styles.css`, `app.js` (logic), `data.js` (generated), logos and icons. |
| `gyms/*/index.html` | One generated page per gym. |
| `london_climbing_dashboard.html` | Offline single-file version (generated data). |
| `london_climbing_prices.py` | Command-line tool and the price checker. |
| `london-climbing-deals-*.ics` | Generated deal calendars, with and without student-only deals. |
| `ios/` | Native SwiftUI iPhone app; bundles `gyms.json`. See [ios/README.md](ios/README.md). |
| `tools/` | `sync_data.py` (generates everything), `weekly_offer_check.py`, `generate_gym_pages.py`, `accessibility_check.py`. |
| `tests/` | Pricing tests, plus parity tests proving the website (JS), CLI (Python) and app (Swift) price identically. The Swift one is skipped without `swiftc`. |
| `.github/workflows/` | `ci.yml` (tests and drift check on every push), `price-check.yml` (Monday price check). |

## Running your own copy

To host your own version of the website with its own weekly checker, follow
[SETUP.md](SETUP.md) (GitHub plus Cloudflare Pages, about ten minutes). Google sign-in
for synced notes and reviews is optional: see [FIREBASE-SETUP.md](FIREBASE-SETUP.md).

## License

The code is MIT-licensed (see [LICENSE](LICENSE)). Gym names and logos belong to the gyms
and are not covered by it. Prices come from the gyms' public websites and may be out of
date.
