# London Climbing Gym Prices

Find day-pass prices for London climbing gyms on any date/time, see the cheapest
gym, find off-peak deals, list student/concession discounts, and browse by tube zone.

## Files

- **gyms.json** — THE single source of truth for all data. Edit only this, then run
  `python3 tools/sync_data.py` to push the change into every derived file below.
- **index.html** — the deployed site (Cloudflare Pages: https://london-climbing-finder.pages.dev/).
  It is a small HTML shell: styling lives in `assets/styles.css`, interaction and pricing
  logic in `assets/app.js`, and the generated data copy in `assets/data.js`. On phones,
  the All Gyms comparison uses compact expandable cards.
- **gyms/*/index.html** — one crawlable, no-JavaScript detail page per gym, including
  prices, deals, hours, structured data and links to official sources. Generated from
  `gyms.json`; do not edit these pages by hand.
- **london_climbing_dashboard.html** — offline version; open in any browser, no install.
  Also embeds `gyms.json` via the sync script.
- **london_climbing_prices.py** — command-line / interactive version (Python 3, no packages).
- **london-climbing-deals-\*.ics** — subscribable deal calendars, generated from `gyms.json`
  by `tools/sync_data.py` (one with student-only deals, one without).
- **tools/sync_data.py** — generates `assets/data.js`, updates the offline dashboard, and
  regenerates the calendars, gym pages and sitemap. `--check` (used by CI) fails if a
  derived file is stale.
- **tests/** — golden pricing tests plus a JS↔Python parity test that proves the site's
  pricing logic matches the CLI's for every gym. Run: `python3 -m unittest discover -s tests`
- **tools/accessibility_check.py** — opens the live interface and all generated pages in
  Chromium, runs axe WCAG rules, checks accessible names and keyboard tabs, and verifies
  that the phone layout has become labelled cards.
- **.github/workflows/** — `ci.yml` (tests + drift check on every push) and
  `price-check.yml` (weekly live price re-check, opens an Issue on changes).

## Quick start

Dashboard: double-click `london_climbing_dashboard.html`.

Deployed-site layout locally: run `python3 -m http.server 8000` and open
`http://localhost:8000/`. Directly opening `index.html` also works, including one-click
gym links, but browser APIs behave more like production through a local server.

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
python3 london_climbing_prices.py check                             # fast static-page check
python3 london_climbing_prices.py check --browser                   # also render JS-heavy booking pages (needs Playwright)
python3 london_climbing_prices.py cheapest --scrape                 # try a live price refresh first
```

## Keeping prices fresh

`check` fetches each gym's server-rendered price page (once per page — several gyms share
one) and reports two different things, never editing data itself:

- **TO REVIEW** — a stored headline price (adult peak, concession peak) no longer appears
  on the page, so it probably *changed*.
- **NEW OFFERS** — promo wording ("10% off", "sale", "summer offer", "promo", "BOGOF")
  sits next to GBP amounts that are *not* recorded anywhere in `gyms.json`, so an offer
  was probably *added*.

The second check exists because the first cannot see a discount: a new offer leaves the
old prices on the page untouched, so an existence check alone reports "all matched". That
is exactly how City Bouldering's 10%-off summer sale went unnoticed.

Once you record a flagged offer in `limited_promotions`, its prices count as known and it
stops being reported. That "record it to silence it" loop is what keeps the report short.

## Limited-time offers and expiry

Each `limited_promotions` entry carries an **`expires`** date (`YYYY-MM-DD`), or `null`.
`null` is the common case and means *no end date known* — not "runs forever". Most gyms
never state one on their website: of the offers tracked here, only Westway's does, and
that one turned out to have ended on 2024-01-31 while still being advertised.

Whenever `expires` is set, **`expires_source`** must say where the date came from — a URL,
or something like `"in-gym poster, seen 2026-08-05"`. A date with no stated origin is the
dangerous case, because later nobody can tell a real end date from a guess. A test enforces
this. Off-website sources are fine and often the only ones there are: City Bouldering's
summer sale ends 2026-08-31 according to a poster in the gym, and nothing on their site
says so. What is *not* fine is inferring a date from an offer's name — "Summer 2026 sale"
is not evidence that it stops on 31 August.

Watch out for the two different things a page can mean by a date. Climbing District's
Summer Pass says *"valid for 3 months from the date of purchase"* — that is how long your
pass lasts once bought, not when the offer stops being sold. Only the latter is `expires`.

Once past its `expires` date an offer is treated as expired everywhere: hidden on the site
and in `promos` (use `promos --all` to see it), and — deliberately — its prices stop
counting as "known", so if a gym is still advertising it past the stated end date the
weekly check surfaces it again rather than staying quiet.

```
python3 london_climbing_prices.py promos          # live offers only
python3 london_climbing_prices.py promos --all    # include expired
```
Membership prices are not existence-checked: `prices_url` is the day-pass page and several
brands don't publish membership rates there at all, so absence would mean "not listed" far
more often than "changed". Membership deals still surface via the promo scan.

By default, pages that need JavaScript are listed under **MANUAL CHECK NEEDED**. Add
`--browser` to render them with Playwright. The **`price-check.yml` GitHub Action** installs
Chromium and runs browser mode automatically every Monday ~9am; its summary names every
unreadable gym, and it opens a GitHub Issue for detected price changes or new offers. You
can also trigger it from the repo's Actions tab. Opening hours are stored per gym; the tool
shows an open / closed / "opens HH:MM" indicator for the date and time you pick.

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

The browser checker is intentionally separate from `--scrape`: it is read-only and reports
possible changes for review. Install it locally with `pip install playwright` followed by
`playwright install chromium`. The `scrape` block in each gym record (`needs_js` flag, URL)
controls which pages use browser rendering.

## Adding or updating a gym

Edit `gyms.json` — copy an existing entry, change the fields — then run:

```
python3 tools/sync_data.py                    # HTML, calendars, gym pages + sitemap
python3 -m unittest discover -s tests         # make sure pricing still agrees
npm ci && python3 tools/accessibility_check.py # requires Playwright + Chromium
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
