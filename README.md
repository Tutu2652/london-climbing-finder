# London Climbing Gym Prices

Find day-pass prices for London climbing gyms on any date/time, see the cheapest
gym, find off-peak deals, list student/concession discounts, and browse by tube zone.

## Files

- **gyms.json** — THE single source of truth for all data. Edit only this, then run
  `python3 tools/sync_data.py` to push the change into every derived file below.
- **index.html** — the deployed site (Cloudflare Pages: https://london-climbing-finder.pages.dev/).
  Embeds a copy of `gyms.json` (kept in sync by `tools/sync_data.py`).
- **london_climbing_dashboard.html** — offline version; open in any browser, no install.
  Also embeds `gyms.json` via the sync script.
- **london_climbing_prices.py** — command-line / interactive version (Python 3, no packages).
- **london-climbing-deals-\*.ics** — subscribable deal calendars, generated from `gyms.json`
  by `tools/sync_data.py` (one with student-only deals, one without).
- **tools/sync_data.py** — injects `gyms.json` into the HTML files and regenerates the
  calendars. `--check` (used by CI) fails if any derived file is stale.
- **tests/** — golden pricing tests plus a JS↔Python parity test that proves the site's
  pricing logic matches the CLI's for every gym. Run: `python3 -m unittest discover -s tests`
- **.github/workflows/** — `ci.yml` (tests + drift check on every push) and
  `price-check.yml` (weekly live price re-check, opens an Issue on changes).

## Quick start

Dashboard: double-click `london_climbing_dashboard.html`.

Command line:
```
python3 london_climbing_prices.py                       # interactive menu
python3 london_climbing_prices.py cheapest --date 2026-07-04        # cheapest ACHIEVABLE all day + when to go
python3 london_climbing_prices.py cheapest --date 2026-07-04 --time 19:00 --concession  # specific time
python3 london_climbing_prices.py price castle --time 14:00         # one gym (incl. directions + your note)
python3 london_climbing_prices.py directions "the arch"             # transit + Citymapper links
python3 london_climbing_prices.py travel --from "E14 9AJ"           # TfL transit time to every gym, sorted
python3 london_climbing_prices.py note castle great cafe, busy pm   # save a personal note (omit text to clear)
python3 london_climbing_prices.py rate castle 4                     # rate 1-5 (omit value to clear)
python3 london_climbing_prices.py offpeak                           # all off-peak / cheap slots
python3 london_climbing_prices.py students                          # student & concession rates
python3 london_climbing_prices.py zones                             # gyms grouped by tube zone
python3 london_climbing_prices.py all                               # every gym: prices + facilities
python3 london_climbing_prices.py memberships                       # monthly membership (full + concession)
python3 london_climbing_prices.py check                             # re-check stored prices vs live pages, report changes
python3 london_climbing_prices.py cheapest --scrape                 # try a live price refresh first
```

## Keeping prices fresh

`check` fetches each gym's server-rendered price page and confirms the stored headline
prices (adult peak, concession peak) still appear; anything that no longer matches is
reported as a likely change to review (it never edits data itself). Pages that load prices
through JavaScript booking widgets are skipped. The **`price-check.yml` GitHub Action**
runs this automatically every Monday ~9am and opens a GitHub Issue if anything changed
(you can also trigger it from the repo's Actions tab). Opening hours are stored per gym; the tool shows an
open / closed / "opens HH:MM" indicator for the date and time you pick.

## Cheapest by date vs by time

`cheapest` with only `--date` (or no args) ranks each gym by the **cheapest price you can get
any time that day**, and tells you the time window to go. Add `--time` to instead price a
specific moment. The dashboard has the same toggle ("Cheapest mode").

## Bank holidays

Deals that gyms exclude on bank holidays (Castle off-peak, LCC Magic Time, Mile End super
off-peak, City Bouldering Monday Bargain, etc.) are automatically dropped on those dates.
The bank-holiday dates are fetched live from `https://www.gov.uk/bank-holidays.json`
(England & Wales) and cached to `bank_holidays.json`; nothing is hard-coded.

## Map, directions and notes

The dashboard's **Map** tab plots every gym, lets you set a start location (default E14 9AJ),
sorts gyms by distance, and links to Google Maps transit directions. A **Travel** column shows
the live public-transport journey time from your start location, via the free TfL Journey
Planner API. Per-gym **notes** and 1-5 **star ratings** are saved in the browser (and via the
CLI `note` / `rate` commands) and shown in the results.

**Super off-peak** is the discount category covering all special cheap slots: Bargain, Magic
Time, Student Saver, super-off-peak windows and BOGOF. Regular weekday-daytime pricing is
still shown as plain "off-peak".

## How prices work

Prices are researched pay-as-you-go single day-pass entries (GBP), current as of the
`last_research_date` in `gyms.json`. Each gym has adult and concession rates, split into
**peak** (evenings + weekends) and **off-peak** (weekday daytime). Some gyms have special
cheap windows ("magic" slots) that override both.

Where a gym does not publish exact off-peak times, the tool assumes off-peak = weekday
until 16:00; those entries are flagged `~approx` / `offpeak_assumed`. Gyms whose prices
were not publicly listed at research time are flagged `verify` — click through to the
gym's price page to confirm.

## Live scraping

`--scrape` (CLI) tries to refresh prices from each gym's website using only the Python
standard library. It is best-effort: many gyms load prices through JavaScript booking
widgets (RockGymPro, Momence, etc.) that a simple fetch cannot read, and some sites block
automated requests. Anything it cannot read keeps its curated value. It only overwrites a
price when it can tie a GBP amount to an "off-peak"/"peak" keyword and the value is sane
(GBP 3–40), so it will not clobber good data with noise.

To make scraping more reliable you can add `requests` + `beautifulsoup4` and, for the
JavaScript sites, a headless browser (Playwright). The `scrape` block in each gym record
(`needs_js` flag, URL) is where per-gym rules live.

## Adding or updating a gym

Edit `gyms.json` — copy an existing entry, change the fields — then run:

```
python3 tools/sync_data.py                    # push into HTML + calendars
python3 -m unittest discover -s tests         # make sure pricing still agrees
```

Commit everything the sync script touched. CI fails the build if you forget, so the
site can never show data that differs from `gyms.json`.

## Data covered

26 gyms, each site as its own entry with its own prices and off-peak times: The Castle,
Mile End, Yonder, Sen, Westway, The Reach, Rise, Substation Brixton; City Bouldering
(Aldgate, White City, Stratford) plus Climbing Co Fulham; The Font (Wandsworth, Borough,
Hounslow — Hounslow is cheaper); Climbing District (Bermondsey/Arch, Tottenham
Hale/Stronghold, London Fields); and all eight London Climbing Centres sites (VauxWall
West/East, HarroWall, CroyWall, RavensWall, CanaryWall, BethWall, EustonWall).

Prices merged from web research (2026-07-01) and the user's Google Sheet. Where the two
disagreed the sheet was preferred, except The Castle, where the live website showed
£18 / £12.50 versus the sheet's £16.50 / £11.50 — flagged in that gym's note to verify.
