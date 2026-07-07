#!/usr/bin/env python3
"""
London Climbing Gym Price Tool
==============================

Find the price of a London climbing gym on any date/time, see the cheapest gym
today, find off-peak deals, list student/concession discounts, and browse gyms
by tube zone.

Data lives in gyms.json (next to this file). Curated prices were researched on
the date in gyms.json["_meta"]["last_research_date"]. An optional live scraper
tries to refresh prices from each gym's website; anything it cannot read falls
back to the curated value.

USAGE
-----
Interactive menu:        python3 london_climbing_prices.py
Cheapest today:          python3 london_climbing_prices.py cheapest
Cheapest on a date:      python3 london_climbing_prices.py cheapest --date 2026-07-04 --time 19:00 --concession
Price for one gym:       python3 london_climbing_prices.py price castle --date 2026-07-04 --time 14:00
Off-peak / cheap slots:  python3 london_climbing_prices.py offpeak
Student discounts:       python3 london_climbing_prices.py students
Browse by zone:          python3 london_climbing_prices.py zones
Try live scrape:         python3 london_climbing_prices.py cheapest --scrape

No third-party packages are required. `--scrape` uses only the standard library
(urllib) and degrades gracefully when a site blocks it or renders prices in
JavaScript.
"""

import argparse
import datetime as dt
import json
import os
import re
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(HERE, "gyms.json")
WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]


# --------------------------------------------------------------------------- #
# Data loading
# --------------------------------------------------------------------------- #
def load_data(path=DATA_PATH):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def save_data(data, path=DATA_PATH):
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)


# --------------------------------------------------------------------------- #
# UK bank holidays (fetched live from gov.uk, cached locally)
# --------------------------------------------------------------------------- #
BH_CACHE_PATH = os.path.join(HERE, "bank_holidays.json")
_BH = None  # in-memory set of "YYYY-MM-DD" for England & Wales, or None if unknown

# --------------------------------------------------------------------------- #
# Personal notes per gym (saved alongside the script)
# --------------------------------------------------------------------------- #
NOTES_PATH = os.path.join(HERE, "gym_notes.json")
_NOTES = None


def notes():
    global _NOTES
    if _NOTES is None:
        try:
            _NOTES = json.load(open(NOTES_PATH, encoding="utf-8"))
        except Exception:
            _NOTES = {}
    return _NOTES


def set_note(gym_id, text):
    n = notes()
    if text:
        n[gym_id] = text
    else:
        n.pop(gym_id, None)
    with open(NOTES_PATH, "w", encoding="utf-8") as fh:
        json.dump(n, fh, indent=2, ensure_ascii=False)


def note_for(gym):
    return notes().get(gym["id"])


RATINGS_PATH = os.path.join(HERE, "gym_ratings.json")
_RATINGS = None


def ratings():
    global _RATINGS
    if _RATINGS is None:
        try:
            _RATINGS = json.load(open(RATINGS_PATH, encoding="utf-8"))
        except Exception:
            _RATINGS = {}
    return _RATINGS


def set_rating(gym_id, value):
    r = ratings()
    if value:
        r[gym_id] = value
    else:
        r.pop(gym_id, None)
    with open(RATINGS_PATH, "w", encoding="utf-8") as fh:
        json.dump(r, fh, indent=2, ensure_ascii=False)


def rating_for(gym):
    v = ratings().get(gym["id"])
    return ("%d/5" % v) if v else None


def _tfl_depart():
    """
    Real-time departure ("now") when the current time is 8am-11pm; otherwise a
    stable weekday 10:00 departure so overnight numbers stay sensible.
    """
    if 8 <= dt.datetime.now().hour < 23:
        return ""  # no params -> TfL plans from now
    d = dt.date.today()
    while d.weekday() >= 5:  # roll Sat/Sun forward to Monday
        d += dt.timedelta(days=1)
    return "date=%s&time=1000&timeIs=Departing" % d.strftime("%Y%m%d")


def travel_minutes(origin, gym):
    """Public-transport journey time (minutes) via TfL at ~10am weekday, or None."""
    import urllib.parse, urllib.request
    dest = gym.get("postcode") or "%s,%s" % (gym.get("lat"), gym.get("lng"))
    url = "https://api.tfl.gov.uk/Journey/JourneyResults/%s/to/%s?%s" % (
        urllib.parse.quote(origin), urllib.parse.quote(dest), _tfl_depart())
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            j = json.loads(resp.read().decode("utf-8", errors="ignore"))
        js = j.get("journeys") or []
        return js[0].get("duration") if js else None
    except Exception:
        return None


def bank_holidays(force=False):
    """
    Return a set of England & Wales bank-holiday dates. Fetched from
    https://www.gov.uk/bank-holidays.json and cached to disk so later runs work
    offline. Returns None if the list could not be obtained at all.
    """
    global _BH
    if _BH is not None and not force:
        return _BH
    raw = None
    try:
        import urllib.request
        req = urllib.request.Request(
            "https://www.gov.uk/bank-holidays.json",
            headers={"User-Agent": "Mozilla/5.0 (price-checker)"},
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = json.loads(resp.read().decode("utf-8", errors="ignore"))
        with open(BH_CACHE_PATH, "w", encoding="utf-8") as fh:
            json.dump(raw, fh)
    except Exception:
        if os.path.exists(BH_CACHE_PATH):
            try:
                raw = json.load(open(BH_CACHE_PATH, encoding="utf-8"))
            except Exception:
                raw = None
    if not raw:
        _BH = None
        return None
    events = raw.get("england-and-wales", {}).get("events", [])
    _BH = {e["date"] for e in events}
    return _BH


def is_bank_holiday(when):
    """True if `when` falls on a bank holiday, False if not, None if unknown."""
    bh = bank_holidays()
    if bh is None:
        return None
    return when.strftime("%Y-%m-%d") in bh


# --------------------------------------------------------------------------- #
# Price logic
# --------------------------------------------------------------------------- #
def _to_minutes(hhmm):
    h, m = hhmm.split(":")
    return int(h) * 60 + int(m)


def _in_window(day_name, minutes, window):
    if not window:
        return False
    if day_name not in window.get("days", []):
        return False
    return _to_minutes(window["start"]) <= minutes < _to_minutes(window["end"])


def _offpeak_windows(gym):
    """offpeak_window may be a single object, a list of objects, or None."""
    w = gym.get("offpeak_window")
    if not w:
        return []
    return w if isinstance(w, list) else [w]


def applicable_price(gym, when, concession=False):
    """
    Return (price, label) for a gym at datetime `when`.

    label is one of: 'magic', 'off-peak', 'peak', or None if no price is known.
    `concession=True` uses student/concession rates where available.
    """
    day_name = WEEKDAYS[when.weekday()]
    minutes = when.hour * 60 + when.minute
    payg = gym["payg"]
    is_bh = is_bank_holiday(when) is True  # None (unknown) is treated as not-BH

    # 1) Special cheap "magic" slots win if we are inside one.
    best_magic = None
    for slot in gym.get("magic_slots", []):
        if day_name in slot.get("days", []) and _in_window(
            day_name, minutes, {"days": slot["days"], "start": slot["start"], "end": slot["end"]}
        ):
            # Month-scoped slots (e.g. a promo that only runs in June).
            if slot.get("months") and when.month not in slot["months"]:
                continue
            # Slots that don't run on bank holidays.
            if slot.get("except_bank_holidays") and is_bh:
                continue
            label = slot.get("label", "magic")
            # Student / concession slots only count for concession holders.
            if "concession" in label.lower() and not concession:
                continue
            # Board-only slots aren't standard entry — don't price them.
            # (Keeps parity with the site's JS `applicable()`.)
            if re.search(r"board[- ]?only", label, flags=re.I):
                continue
            if "off peak" in label.lower() or "off-peak" in label.lower():
                full = label + (" (pre-book)" if slot.get("prebook") else "")
            else:
                full = "super off-peak (%s%s)" % (label, ", pre-book" if slot.get("prebook") else "")
            if best_magic is None or slot["price"] < best_magic[0]:
                best_magic = (slot["price"], full)
    if best_magic:
        return best_magic

    # 2) Off-peak vs peak (a gym may have several off-peak windows).
    windows = _offpeak_windows(gym)
    is_offpeak = any(
        _in_window(day_name, minutes, w)
        and not (w.get("except_bank_holidays") and is_bh)
        for w in windows
    )
    concession_only = any(w.get("concession_only") for w in windows)

    if concession:
        off = payg.get("concession_offpeak")
        peak = payg.get("concession_peak")
    else:
        off = payg.get("adult_offpeak")
        peak = payg.get("adult_peak")
        if concession_only:
            off = None  # adult cannot use the off-peak rate at this gym

    if is_offpeak and off is not None:
        return (off, "off-peak")
    if peak is not None:
        return (peak, "peak")
    # No peak price known: only fall back for flat-priced gyms (no off-peak window).
    if off is not None and not windows:
        return (off, "off-peak")
    return (None, None)


# --------------------------------------------------------------------------- #
# Queries
# --------------------------------------------------------------------------- #
def cheapest(data, when, concession=False, gym_type=None, zone=None):
    rows = []
    for gym in data["gyms"]:
        if gym_type and gym_type not in gym["type"]:
            continue
        if zone is not None and gym["zone"] != zone:
            continue
        price, label = applicable_price(gym, when, concession)
        if price is None:
            continue
        rows.append((price, label, gym))
    rows.sort(key=lambda r: r[0])
    return rows


def best_on_day(gym, date, concession=False, step=15):
    """
    Scan a whole day and return the cheapest price achievable, the rate label,
    and the time window(s) when it applies. This answers "what's the cheapest I
    can pay on this date and when should I go".
    """
    base = dt.datetime.combine(date, dt.time(0, 0))
    samples = []
    for m in range(0, 24 * 60, step):
        price, label = applicable_price(gym, base + dt.timedelta(minutes=m), concession)
        if price is not None:
            samples.append((m, price, label))
    if not samples:
        return None
    minp = min(p for _, p, _ in samples)
    label = next(l for _, p, l in samples if p == minp)
    # Merge consecutive sample points that hit the minimum price into ranges.
    ranges, cur = [], None
    for m, p, _ in samples:
        if p == minp:
            cur = [m, m] if cur is None else [cur[0], m]
        elif cur:
            ranges.append(cur); cur = None
    if cur:
        ranges.append(cur)
    hhmm = lambda x: "%02d:%02d" % (x // 60, x % 60)
    # "all day" if the min price covers essentially the whole opening spread
    if len(ranges) == 1 and ranges[0][0] == 0 and ranges[0][1] >= 24 * 60 - step:
        when = "any time"
    else:
        when = "; ".join("%s-%s" % (hhmm(a), hhmm(min(b + step, 24 * 60 - 1))) for a, b in ranges)
    return {"price": minp, "label": label, "when": when}


def cheapest_day(data, date, concession=False, gym_type=None, zone=None):
    """Cheapest achievable price per gym across the whole date, ranked."""
    rows = []
    for gym in data["gyms"]:
        if gym_type and gym_type not in gym["type"]:
            continue
        if zone is not None and gym["zone"] != zone:
            continue
        best = best_on_day(gym, date, concession)
        if best:
            rows.append((best["price"], best["label"], best["when"], gym))
    rows.sort(key=lambda r: r[0])
    return rows


def maps_links(gym):
    """Return the Google Maps public-transport directions URL for a gym."""
    import urllib.parse
    dest = gym.get("address") or gym.get("postcode") or "%s,%s" % (gym.get("lat"), gym.get("lng"))
    return ("https://www.google.com/maps/dir/?api=1&destination=%s&travelmode=transit"
            % urllib.parse.quote(dest))


def hours_summary(gym):
    h = gym.get("hours")
    if not h:
        return None
    days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    parts, i = [], 0
    while i < 7:
        cur = h[days[i]]; j = i
        while j + 1 < 7 and h[days[j + 1]] == cur:
            j += 1
        lbl = days[i] if i == j else "%s-%s" % (days[i], days[j])
        parts.append("%s %s" % (lbl, ("%s-%s" % (cur[0], cur[1])) if cur else "closed"))
        i = j + 1
    return "; ".join(parts) + (" (approx)" if gym.get("hours_assumed") else "")


def open_state(gym, when):
    h = gym.get("hours")
    if not h:
        return None
    t = h.get(WEEKDAYS[when.weekday()])
    mins = when.hour * 60 + when.minute
    if not t:
        return "closed today"
    if mins < _to_minutes(t[0]):
        return "opens %s" % t[0]
    if mins >= _to_minutes(t[1]):
        return "closed"
    return "open till %s" % t[1]


def off_peak_deals(data):
    """All off-peak windows and magic slots, cheapest first."""
    deals = []
    for gym in data["gyms"]:
        payg = gym["payg"]
        off = payg.get("concession_offpeak") or payg.get("adult_offpeak")
        if off is not None:
            for win in _offpeak_windows(gym):
                deals.append(
                    {
                        "gym": gym["name"],
                        "price": off,
                        "kind": "off-peak",
                        "when": "%s %s-%s"
                        % (",".join(win["days"]), win["start"], win["end"]),
                        "assumed": gym.get("offpeak_assumed", False),
                    }
                )
        for slot in gym.get("magic_slots", []):
            deals.append(
                {
                    "gym": gym["name"],
                    "price": slot["price"],
                    "kind": slot.get("label", "magic"),
                    "when": "%s %s-%s"
                    % (",".join(slot["days"]), slot["start"], slot["end"]),
                    "assumed": False,
                }
            )
    deals.sort(key=lambda d: d["price"])
    return deals


def student_discounts(data):
    out = []
    for gym in data["gyms"]:
        out.append(
            {
                "gym": gym["name"],
                "concession_peak": gym["payg"].get("concession_peak"),
                "concession_offpeak": gym["payg"].get("concession_offpeak"),
                "note": gym.get("student_note", ""),
            }
        )
    return out, data.get("student_promotions", [])


def by_zone(data):
    zones = {}
    for gym in data["gyms"]:
        zones.setdefault(gym["zone"], []).append(gym)
    return dict(sorted(zones.items()))


# --------------------------------------------------------------------------- #
# Live scraper (best-effort, std-lib only)
# --------------------------------------------------------------------------- #
def _fetch(url, timeout=15):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (price-checker)"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
    return raw.decode("utf-8", errors="ignore")


def _prices_near(html, keyword, radius=160):
    """
    Return GBP amounts appearing within `radius` chars of a keyword.
    `keyword` may be a plain string or a compiled regex (so callers can e.g.
    match 'peak' without also matching the 'peak' inside 'off-peak').
    """
    text = re.sub(r"<[^>]+>", " ", html)
    text = re.sub(r"\s+", " ", text)
    if isinstance(keyword, str):
        keyword = re.compile(re.escape(keyword), re.I)
    found = []
    for m in keyword.finditer(text):
        chunk = text[max(0, m.start() - radius): m.end() + radius]
        for pm in re.finditer(r"£\s?(\d{1,3}(?:\.\d{1,2})?)", chunk):
            found.append(float(pm.group(1)))
    return found


def scrape_gym(gym, verbose=False):
    """
    Best-effort refresh. Returns dict of what it could read, or {} on failure.
    This is deliberately conservative: it only reports numbers it can tie to a
    keyword like 'off-peak' / 'peak', and never overwrites curated data with a
    clearly wrong value.
    """
    url = gym.get("scrape", {}).get("url")
    if not url:
        return {}
    try:
        html = _fetch(url)
    except Exception as exc:  # network blocked, 403, timeout, etc.
        if verbose:
            print("   ! could not fetch %s (%s)" % (url, exc))
        return {}

    if gym.get("scrape", {}).get("needs_js"):
        # Price is rendered by JS in a booking widget; static HTML won't have it.
        if verbose:
            print("   - %s loads prices via JavaScript; keeping curated value" % gym["name"])
        return {}

    result = {}
    offpeak = _prices_near(html, "off-peak") + _prices_near(html, "off peak")
    # 'peak' must not match the tail of 'off-peak' / 'off peak'.
    peak_re = re.compile(r"(?<!off-)(?<!off )peak", re.I)
    peak = [p for p in _prices_near(html, peak_re) if p not in offpeak]
    sane = lambda xs: [x for x in xs if 3 <= x <= 40]
    offpeak, peak = sane(offpeak), sane(peak)
    if offpeak:
        result["adult_offpeak_scraped"] = min(offpeak)
    if peak:
        result["adult_peak_scraped"] = max(peak)
    if verbose and result:
        print("   + %s scraped: %s" % (gym["name"], result))
    return result


def refresh_prices(data, verbose=True):
    print("Attempting live price refresh (best-effort)...\n")
    updated = 0
    for gym in data["gyms"]:
        info = scrape_gym(gym, verbose=verbose)
        if info.get("adult_offpeak_scraped") is not None:
            gym["payg"]["adult_offpeak"] = info["adult_offpeak_scraped"]
            updated += 1
        if info.get("adult_peak_scraped") is not None:
            gym["payg"]["adult_peak"] = info["adult_peak_scraped"]
            updated += 1
    print("\nRefreshed %d price field(s) from live sites." % updated)
    print("Anything not refreshed kept its curated value (research date: %s).\n"
          % data["_meta"]["last_research_date"])
    return data


def _all_amounts(html):
    """Every GBP amount on a page, as a set of normalised strings."""
    text = re.sub(r"<[^>]+>", " ", html)
    return set(re.findall(r"£\s?(\d{1,3}(?:\.\d{1,2})?)", text))


def _amount_variants(v):
    out = {"%g" % v, "%.2f" % v}
    if v == int(v):
        out.add(str(int(v)))
    return out


def check_prices(data, verbose=True):
    """
    Robust price re-check. For each gym whose price page is server-rendered
    (not a JS booking widget), fetch it and confirm the stored headline prices
    (adult peak and concession peak) still appear on the page. Anything that no
    longer matches is reported as a likely change to review. Read-only.
    """
    changed, unreachable, skipped, ok = [], [], 0, 0
    for gym in data["gyms"]:
        sc = gym.get("scrape", {})
        url = sc.get("url") or gym.get("prices_url")
        if sc.get("needs_js") or not url:
            skipped += 1
            continue
        try:
            amounts = _all_amounts(_fetch(url))
        except Exception:
            unreachable.append(gym["name"])
            continue
        if not amounts:
            unreachable.append(gym["name"])
            continue
        missing = []
        for key in ("adult_peak", "concession_peak"):
            v = gym["payg"].get(key)
            if v is not None and not (_amount_variants(v) & amounts):
                missing.append("%s %s" % (key.replace("_", " "), fmt_price(v)))
        if missing:
            changed.append((gym["name"], missing, url))
        else:
            ok += 1
    print("\nPrice re-check (%s)\n%s" % (dt.date.today().isoformat(), "-" * 50))
    print("%d matched, %d to review, %d unreachable, %d skipped (JS-only pages)."
          % (ok, len(changed), len(unreachable), skipped))
    if changed:
        print("\nTO REVIEW - stored price no longer found on page:")
        for name, miss, url in changed:
            print("  * %s: %s\n      %s" % (name, "; ".join(miss), url))
    if unreachable and verbose:
        print("\nCould not fetch: %s" % ", ".join(unreachable))
    if not changed:
        print("\nNo headline price changes detected on the readable pages.")
    return changed


# --------------------------------------------------------------------------- #
# Pretty printing
# --------------------------------------------------------------------------- #
def fmt_price(p):
    return "n/a" if p is None else "£%.2f" % p


def _print_extras(gym):
    bits = []
    if rating_for(gym):
        bits.append("rated " + rating_for(gym))
    if note_for(gym):
        bits.append("note: " + note_for(gym))
    if bits:
        print("      " + " | ".join(bits))


def print_cheapest(rows, when, concession):
    who = "student/concession" if concession else "adult"
    print("\nCheapest %s day-pass for %s\n%s" % (
        who, when.strftime("%a %d %b %Y, %H:%M"), "-" * 48))
    if is_bank_holiday(when) is True:
        print("** UK bank holiday - some off-peak / Magic / Bargain deals do not apply. **")
    if not rows:
        print("No priced gyms match.")
        return
    for i, (price, label, gym) in enumerate(rows, 1):
        flag = "  (verify)" if not gym["verified"] else ""
        print("%2d. %-34s %8s  %-18s zone %s%s" % (
            i, gym["name"], fmt_price(price), label, gym["zone"], flag))
        _print_extras(gym)


def print_cheapest_day(rows, date, concession):
    who = "student/concession" if concession else "adult"
    print("\nCheapest %s day-pass on %s (best price any time of day)\n%s" % (
        who, date.strftime("%a %d %b %Y"), "-" * 66))
    if is_bank_holiday(dt.datetime.combine(date, dt.time(12, 0))) is True:
        print("** UK bank holiday - some off-peak / Magic / Bargain deals do not apply. **")
    if not rows:
        print("No priced gyms match.")
        return
    for i, (price, label, when, gym) in enumerate(rows, 1):
        flag = "  (verify)" if not gym["verified"] else ""
        print("%2d. %-40s %8s  go %-14s %-26s zone %s%s" % (
            i, gym["name"], fmt_price(price), when, label, gym["zone"], flag))
        _print_extras(gym)


def print_offpeak(deals):
    print("\nOff-peak & special cheap slots (cheapest first)\n%s" % ("-" * 62))
    for d in deals:
        flag = "  ~approx" if d["assumed"] else ""
        print("%8s  %-30s %-7s %s%s" % (
            fmt_price(d["price"]), d["gym"], d["kind"], d["when"], flag))


def print_students(rows, promos):
    print("\nStudent / concession rates\n%s" % ("-" * 60))
    for r in rows:
        print("%-34s peak %s  off-peak %s" % (
            r["gym"], fmt_price(r["concession_peak"]), fmt_price(r["concession_offpeak"])))
        if r["note"]:
            print("    %s" % r["note"])
    if promos:
        print("\nGroup-wide student promotions:")
        for p in promos:
            print("  * %s\n      %s" % (p["offer"], p["scope"]))


def print_zones(zones):
    print("\nGyms by tube zone\n%s" % ("-" * 50))
    for z, gyms in zones.items():
        print("\nZone %s:" % z)
        for g in gyms:
            print("  - %-32s %-22s %s" % (g["name"], g["area"], g["postcode"]))


# --------------------------------------------------------------------------- #
# Argument parsing / CLI
# --------------------------------------------------------------------------- #
def parse_when(date_str, time_str):
    if date_str:
        d = dt.datetime.strptime(date_str, "%Y-%m-%d").date()
    else:
        d = dt.date.today()
    if time_str:
        t = dt.datetime.strptime(time_str, "%H:%M").time()
    else:
        t = dt.datetime.now().time().replace(second=0, microsecond=0)
    return dt.datetime.combine(d, t)


def find_gym(data, key):
    key = key.lower()
    for gym in data["gyms"]:
        if gym["id"] == key or key in gym["name"].lower():
            return gym
    return None


def cmd_price(data, args):
    gym = find_gym(data, args.gym)
    if not gym:
        print("No gym matching '%s'. Try: %s" % (
            args.gym, ", ".join(g["id"] for g in data["gyms"])))
        return
    when = parse_when(args.date, args.time)
    price, label = applicable_price(gym, when, args.concession)
    print("\n%s" % gym["name"])
    print("  %s at %s" % (when.strftime("%a %d %b %Y"), when.strftime("%H:%M")))
    print("  %s rate: %s (%s)" % (
        "concession" if args.concession else "adult", fmt_price(price), label or "no data"))
    if is_bank_holiday(when) is True:
        print("  note: this is a UK bank holiday - some off-peak/Magic/Bargain deals don't apply.")
    if gym.get("offpeak_assumed"):
        print("  note: off-peak window is an assumption, not confirmed for this gym.")
    if gym.get("facilities"):
        print("  facilities: %s" % gym["facilities"])
    if hours_summary(gym):
        st = open_state(gym, when)
        print("  hours: %s%s" % (hours_summary(gym), ("  [%s]" % st) if st else ""))
    mem = gym.get("membership") or {}
    if mem.get("full_monthly") is not None:
        c = mem.get("concession_monthly")
        print("  membership: %s/mo%s" % (
            fmt_price(mem["full_monthly"]),
            (" (concession %s/mo)" % fmt_price(c)) if c is not None else ""))
    if gym.get("student_note"):
        print("  student: %s" % gym["student_note"])
    if rating_for(gym):
        print("  your rating: %s" % rating_for(gym))
    if note_for(gym):
        print("  your note: %s" % note_for(gym))
    origin = getattr(args, "from_", None) or "E14 9AJ"
    mins = travel_minutes(origin, gym)
    if mins is not None:
        print("  travel: ~%d min by public transport from %s" % (mins, origin))
    print_directions(gym)


def print_directions(gym):
    print("  getting there: %s (%s)" % (
        gym.get("address") or gym.get("area", ""), gym.get("postcode") or "no postcode"))
    print("    directions (public transport): %s" % maps_links(gym))
    if gym.get("route_setting_url"):
        print("    route setting: %s" % gym["route_setting_url"])


def interactive(data):
    menu = """
London Climbing Gym Prices
==========================
1) Cheapest on a date (best price any time, tells you when to go)
2) Cheapest at a specific date + time
3) Price for one gym
4) Off-peak & cheap slots
5) Student / concession discounts
6) Browse by tube zone
7) Try live price refresh
8) Directions to a gym
0) Quit
"""
    while True:
        print(menu)
        choice = input("Choose: ").strip()
        if choice == "0":
            break
        elif choice == "1":
            date_str = input("Date (YYYY-MM-DD, blank=today): ").strip() or None
            conc = input("Student/concession? (y/N): ").strip().lower() == "y"
            date = (dt.datetime.strptime(date_str, "%Y-%m-%d").date()
                    if date_str else dt.date.today())
            print_cheapest_day(cheapest_day(data, date, conc), date, conc)
        elif choice == "2":
            date_str = input("Date (YYYY-MM-DD, blank=today): ").strip() or None
            time_str = input("Time (HH:MM, blank=now): ").strip() or None
            conc = input("Student/concession? (y/N): ").strip().lower() == "y"
            when = parse_when(date_str, time_str)
            print_cheapest(cheapest(data, when, conc), when, conc)
        elif choice == "8":
            gym = find_gym(data, input("Gym name or id: ").strip())
            if gym:
                print("\n%s" % gym["name"]); print_directions(gym)
            else:
                print("Not found.")
        elif choice == "3":
            key = input("Gym name or id: ").strip()
            gym = find_gym(data, key)
            if not gym:
                print("Not found.")
                continue
            date_str = input("Date (YYYY-MM-DD, blank=today): ").strip() or None
            time_str = input("Time (HH:MM, blank=now): ").strip() or None
            conc = input("Student/concession? (y/N): ").strip().lower() == "y"
            when = parse_when(date_str, time_str)
            price, label = applicable_price(gym, when, conc)
            print("\n%s -> %s (%s)" % (gym["name"], fmt_price(price), label or "no data"))
        elif choice == "4":
            print_offpeak(off_peak_deals(data))
        elif choice == "5":
            rows, promos = student_discounts(data)
            print_students(rows, promos)
        elif choice == "6":
            print_zones(by_zone(data))
        elif choice == "7":
            refresh_prices(data)
        else:
            print("?")


def main(argv=None):
    p = argparse.ArgumentParser(description="London climbing gym price tool")
    sub = p.add_subparsers(dest="cmd")

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--date", help="YYYY-MM-DD (default today)")
    common.add_argument("--time", help="HH:MM 24h (default now)")
    common.add_argument("--concession", action="store_true", help="use student/concession rate")
    common.add_argument("--scrape", action="store_true", help="try live price refresh first")
    common.add_argument("--from", dest="from_", default="E14 9AJ",
                        help="start location for travel time (default E14 9AJ)")

    sp = sub.add_parser("cheapest", parents=[common],
                        help="cheapest per gym across the whole date; add --time to price a specific moment")
    sp.add_argument("--type", choices=["bouldering", "ropes"], help="filter by gym type")
    sp.add_argument("--zone", type=int, help="filter by tube zone")

    pp = sub.add_parser("price", parents=[common])
    pp.add_argument("gym", help="gym id or name fragment, e.g. castle")

    dp = sub.add_parser("directions")
    dp.add_argument("gym", help="gym id or name fragment")

    np = sub.add_parser("note", help="add/replace/clear your note for a gym")
    np.add_argument("gym", help="gym id or name fragment")
    np.add_argument("text", nargs="*", help="note text (omit to clear)")

    rp = sub.add_parser("rate", help="rate a gym 1-5 (omit value to clear)")
    rp.add_argument("gym", help="gym id or name fragment")
    rp.add_argument("value", nargs="?", type=int, help="1-5, or omit to clear")

    tp = sub.add_parser("travel", help="public-transport time from a start to every gym")
    tp.add_argument("--from", dest="from_", default="E14 9AJ", help="start location (default E14 9AJ)")

    sub.add_parser("offpeak")
    sub.add_parser("students")
    sub.add_parser("zones")
    sub.add_parser("all", help="every gym with key prices + facilities")
    sub.add_parser("memberships", help="monthly membership prices (full + concession)")
    sub.add_parser("refresh")
    sub.add_parser("check", help="re-check stored prices against live pages, report changes")
    sub.add_parser("promos", help="limited-time / seasonal offers")

    args = p.parse_args(argv)
    data = load_data()

    if getattr(args, "scrape", False):
        data = refresh_prices(data)

    if args.cmd == "cheapest":
        date = (dt.datetime.strptime(args.date, "%Y-%m-%d").date()
                if args.date else dt.date.today())
        if args.time:
            # Secondary mode: rank by the exact time requested.
            when = parse_when(args.date, args.time)
            print_cheapest(cheapest(data, when, args.concession, args.type, args.zone),
                           when, args.concession)
        else:
            # Primary mode: cheapest achievable any time on the date, with when to go.
            rows = cheapest_day(data, date, args.concession, args.type, args.zone)
            print_cheapest_day(rows, date, args.concession)
    elif args.cmd == "price":
        cmd_price(data, args)
    elif args.cmd == "directions":
        gym = find_gym(data, args.gym)
        if gym:
            print("\n%s" % gym["name"])
            print_directions(gym)
        else:
            print("No gym matching '%s'." % args.gym)
    elif args.cmd == "note":
        gym = find_gym(data, args.gym)
        if not gym:
            print("No gym matching '%s'." % args.gym)
        else:
            text = " ".join(args.text).strip()
            set_note(gym["id"], text)
            print("%s note %s." % (gym["name"], "saved" if text else "cleared"))
    elif args.cmd == "rate":
        gym = find_gym(data, args.gym)
        if not gym:
            print("No gym matching '%s'." % args.gym)
        elif args.value is not None and not (1 <= args.value <= 5):
            print("Rating must be between 1 and 5.")
        else:
            set_rating(gym["id"], args.value)
            print("%s rating %s." % (gym["name"], "saved" if args.value else "cleared"))
    elif args.cmd == "travel":
        origin = args.from_
        print("\nPublic-transport time from %s to each gym (via TfL)\n%s" % (origin, "-" * 54))
        rows = []
        for gym in data["gyms"]:
            m = travel_minutes(origin, gym)
            rows.append((m if m is not None else 10 ** 9, m, gym))
        rows.sort(key=lambda r: r[0])
        for _, m, gym in rows:
            print("  %-42s %s" % (gym["name"], ("~%d min" % m) if m is not None else "no route"))
    elif args.cmd == "offpeak":
        print_offpeak(off_peak_deals(data))
    elif args.cmd == "students":
        rows, promos = student_discounts(data)
        print_students(rows, promos)
    elif args.cmd == "zones":
        print_zones(by_zone(data))
    elif args.cmd == "all":
        print("\nAll gyms (adult peak / off-peak, concession peak)\n%s" % ("-" * 74))
        for gym in sorted(data["gyms"], key=lambda g: (g["zone"], g["name"])):
            p = gym["payg"]
            print("Z%s  %-40s peak %s  off %s  conc %s" % (
                gym["zone"], gym["name"], fmt_price(p["adult_peak"]),
                fmt_price(p["adult_offpeak"]), fmt_price(p["concession_peak"])))
            if gym.get("facilities"):
                print("      %s" % gym["facilities"])
            if hours_summary(gym):
                print("      hours: %s" % hours_summary(gym))
    elif args.cmd == "memberships":
        print("\nMemberships (full / concession per month) and multi-visit passes\n%s" % ("-" * 64))
        rows = sorted(data["gyms"],
                      key=lambda g: (g.get("membership") or {}).get("full_monthly") or 10 ** 9)
        for gym in rows:
            m = gym.get("membership") or {}
            full = m.get("full_monthly")
            conc = m.get("concession_monthly")
            print("  %-40s %-12s %s" % (
                gym["name"],
                (fmt_price(full) + "/mo") if full is not None else "not listed",
                ("conc " + fmt_price(conc) + "/mo") if conc is not None else ""))
            if gym.get("passes"):
                print("      passes: %s" % gym["passes"])
    elif args.cmd == "refresh":
        refresh_prices(data)
    elif args.cmd == "check":
        check_prices(data)
    elif args.cmd == "promos":
        ps = data.get("limited_promotions", [])
        print("\nLimited-time / seasonal offers\n%s" % ("-" * 50))
        if not ps:
            print("None tracked right now.")
        for p in ps:
            tag = " (verify)" if p.get("status") == "unverified" else ""
            print("  %-34s %-18s %s%s" % (
                p.get("title", ""), p.get("price", ""), p.get("valid", ""), tag))
            if p.get("details"):
                print("      %s  [%s]" % (p["details"], p.get("brand", "")))
    else:
        interactive(data)


if __name__ == "__main__":
    main()
