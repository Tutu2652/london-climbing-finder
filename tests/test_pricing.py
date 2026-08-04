#!/usr/bin/env python3
"""
Pricing tests.

1. Golden tests: known gyms/dates must produce known prices (Python CLI logic).
2. Parity test: the JS pricing logic embedded in index.html must agree with the
   Python logic for every gym across a spread of dates (weekday / weekend /
   bank holiday), both adult and concession. This is what stops the two
   implementations drifting apart. Requires node; skipped if unavailable.

Run:  python3 -m unittest discover -s tests -v
"""
import datetime as dt
import importlib.util
import json
import os
import re
import shutil
import subprocess
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Fixed bank-holiday set used by BOTH sides, so tests never touch the network.
BANK_HOLIDAYS = {"2026-05-25", "2026-08-31", "2026-12-25", "2026-12-28"}

TEST_DATES = [
    "2026-07-06",  # Mon
    "2026-07-07",  # Tue
    "2026-07-10",  # Fri
    "2026-07-11",  # Sat
    "2026-07-12",  # Sun
    "2026-08-31",  # Mon, bank holiday
]


def load_cli():
    spec = importlib.util.spec_from_file_location(
        "lcp", os.path.join(ROOT, "london_climbing_prices.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod._BH = set(BANK_HOLIDAYS)  # pre-seed so bank_holidays() never fetches
    return mod


LCP = load_cli()
DATA = LCP.load_data(os.path.join(ROOT, "gyms.json"))
GYM = {g["id"]: g for g in DATA["gyms"]}


class GoldenTests(unittest.TestCase):
    def price(self, gym_id, iso, hhmm, conc=False):
        when = dt.datetime.fromisoformat(iso + " " + hhmm)
        return LCP.applicable_price(GYM[gym_id], when, conc)

    def test_castle_adult_has_no_offpeak(self):
        # Castle's off-peak window is concession-only: adults pay peak all day.
        price, label = self.price("castle", "2026-07-08", "10:00", conc=False)
        self.assertEqual((price, label), (18.0, "peak"))

    def test_castle_concession_offpeak(self):
        price, label = self.price("castle", "2026-07-08", "10:00", conc=True)
        self.assertEqual((price, label), (10.5, "off-peak"))

    def test_mileend_super_offpeak_weekday_morning(self):
        price, _ = self.price("mileend", "2026-07-08", "07:30")
        self.assertEqual(price, 5.0)

    def test_mileend_magic_skipped_on_bank_holiday(self):
        # 2026-08-31 is in the fixed BH set; the £5 slot excludes bank holidays.
        price, _ = self.price("mileend", "2026-08-31", "07:30")
        self.assertGreater(price, 5.0)

    def test_board_only_slot_never_prices_standard_entry(self):
        # Rise "Board Friday (board only)" £7.50 must NOT be offered as day entry.
        price, label = self.price("rise", "2026-07-10", "12:00", conc=False)
        self.assertEqual(price, 12.5)
        self.assertNotIn("Board", label, "board-only slot leaked into pricing")

    def test_rise_current_peak_price(self):
        price, _ = self.price("rise", "2026-07-10", "18:00", conc=False)
        self.assertEqual(price, 16.0)

    def test_student_saver_concession_only(self):
        # CD Bermondsey Monday £5 Student Saver: concession yes, adult no.
        p_conc, _ = self.price("arch_bermondsey", "2026-07-06", "12:00", conc=True)
        p_adult, _ = self.price("arch_bermondsey", "2026-07-06", "12:00", conc=False)
        self.assertEqual(p_conc, 5.0)
        self.assertGreater(p_adult, 5.0)

    def test_best_on_day_reports_window(self):
        best = LCP.best_on_day(GYM["mileend"], dt.date(2026, 7, 8))
        self.assertEqual(best["price"], 5.0)
        self.assertIn("07:00", best["when"])

    def test_every_gym_prices_every_test_date(self):
        for iso in TEST_DATES:
            date = dt.date.fromisoformat(iso)
            for g in DATA["gyms"]:
                self.assertIsNotNone(
                    LCP.best_on_day(g, date),
                    "%s has no achievable price on %s" % (g["id"], iso))


@unittest.skipUnless(shutil.which("node"), "node not installed")
class JsPythonParity(unittest.TestCase):
    """index.html's JS pricing must match the Python CLI for all gyms/dates."""

    @classmethod
    def setUpClass(cls):
        src = open(os.path.join(ROOT, "index.html"), encoding="utf-8").read()
        wd = re.search(r"^const WD = .*$", src, re.M).group(0)
        start = src.index("/* ----- price logic")
        end = src.index("// All the distinct deal options")
        logic = src[start:end]
        harness = """
const DATA = %s;
%s
%s
BANK_HOLIDAYS = new Set(%s);
const out = [];
for (const iso of %s) {
  const [y,mo,d] = iso.split("-").map(Number);
  for (const conc of [false, true]) {
    for (const g of DATA.gyms) {
      const b = bestOnDay(g, new Date(y, mo-1, d), conc);
      const at10 = applicable(g, new Date(y, mo-1, d, 10, 0), conc);
      const at19 = applicable(g, new Date(y, mo-1, d, 19, 0), conc);
      out.push([iso, conc, g.id,
        b ? [b.price, b.label, b.when] : null,
        [at10.price, at10.label === "no data" ? null : at10.label],
        [at19.price, at19.label === "no data" ? null : at19.label]]);
    }
  }
}
console.log(JSON.stringify(out));
""" % (json.dumps(DATA), wd, logic,
            json.dumps(sorted(BANK_HOLIDAYS)), json.dumps(TEST_DATES))
        res = subprocess.run(["node", "-"], input=harness, capture_output=True,
                             text=True, timeout=120)
        if res.returncode != 0:
            raise RuntimeError("node harness failed:\n" + res.stderr[:2000])
        cls.js = json.loads(res.stdout)

    def test_parity(self):
        mismatches = []
        for iso, conc, gym_id, js_best, js_10, js_19 in self.js:
            date = dt.date.fromisoformat(iso)
            g = GYM[gym_id]
            b = LCP.best_on_day(g, date, conc)
            py_best = [b["price"], b["label"], b["when"]] if b else None
            py_10 = list(LCP.applicable_price(
                g, dt.datetime.combine(date, dt.time(10, 0)), conc))
            py_19 = list(LCP.applicable_price(
                g, dt.datetime.combine(date, dt.time(19, 0)), conc))
            for name, js_v, py_v in (("bestOnDay", js_best, py_best),
                                     ("10:00", js_10, py_10),
                                     ("19:00", js_19, py_19)):
                if js_v != py_v:
                    mismatches.append("%s %s conc=%s %s: js=%r py=%r"
                                      % (iso, gym_id, conc, name, js_v, py_v))
        self.assertEqual(mismatches, [],
                         "JS/Python pricing diverged:\n" + "\n".join(mismatches[:40]))


class DataValidation(unittest.TestCase):
    """Schema checks for hand-edited fields in gyms.json."""

    def test_ac_fields(self):
        for g in DATA["gyms"]:
            if "ac" in g:
                self.assertIn(g["ac"], ("full", "partial"),
                              "%s: ac must be 'full' or 'partial' (omit for none)" % g["id"])
                self.assertTrue(g.get("ac_note"),
                                "%s: gyms with ac set need an ac_note" % g["id"])
            if "ac_note" in g:
                self.assertIn("ac", g, "%s: ac_note without ac level" % g["id"])

    def test_no_legacy_ac_text_in_facilities(self):
        # AC lives in the ac/ac_note fields now, not in free-text facilities.
        for g in DATA["gyms"]:
            fac = (g.get("facilities") or "").lower()
            self.assertNotIn("ac available", fac,
                             "%s: move AC info to ac/ac_note" % g["id"])

    def test_limited_promotions_shape(self):
        ids = set()
        for p in DATA.get("limited_promotions", []):
            for field in ("id", "brand", "title", "price", "source", "status"):
                self.assertTrue(p.get(field), "promo %r missing %s" % (p, field))
            self.assertIn(p["status"], ("active", "unverified", "expired"))
            self.assertNotIn(p["id"], ids, "duplicate promo id %s" % p["id"])
            ids.add(p["id"])

    def test_every_promo_has_an_expires_key(self):
        # null is fine and normal (most gyms publish no end date); the key
        # must still be present so "no end date" is explicit, not an omission.
        for p in DATA.get("limited_promotions", []):
            self.assertIn("expires", p, "%s: add expires (null if none)" % p["id"])
            if p["expires"] is not None:
                dt.date.fromisoformat(p["expires"])  # raises if malformed
                # A date with no stated origin is the dangerous case: nobody
                # can later tell a real end date from a guessed one.
                self.assertTrue(p.get("expires_source"),
                                "%s: expires needs expires_source saying where "
                                "the date came from" % p["id"])

    def test_expired_promos_are_marked_expired(self):
        today = dt.date.today()
        for p in DATA.get("limited_promotions", []):
            if p.get("expires") and dt.date.fromisoformat(p["expires"]) < today:
                self.assertEqual(p["status"], "expired",
                                 "%s ended %s but status is %r"
                                 % (p["id"], p["expires"], p["status"]))


# A trimmed copy of the City Bouldering pricing markup as it looked on
# 2026-08-04 - the summer sale the old existence-only check could not see.
PROMO_FIXTURE = """
<div class="pricing_heading"><div>Offers &amp; Promos</div>
  <div>Adult</div><div>Concession</div></div>
<div class="pricing_row">
  <div class="pricing-discount">10% OFF</div>
  <div class="text-weight-bold">SUMMER SALE - 10 Climb Pass</div>
  <div class="membership-price">&pound;133.2</div><div class="compare-price">&pound;148</div>
  <div class="membership-price">&pound;117</div><div class="compare-price">&pound;130</div>
</div>
"""

# Prose that merely uses "offer" as a verb must not count as a promo.
PROSE_FIXTURE = """
<p>Members pay an entry fee of just &pound;7.00 per visit. Our gyms offer
world-class bouldering on weekly reset walls.</p>
"""


class PromoDetection(unittest.TestCase):
    """
    The weekly check must notice offers that are ADDED to a page, not just
    prices that vanish from it. A discount leaves the old prices in place, so
    an existence check alone reports nothing.
    """

    @classmethod
    def setUpClass(cls):
        cls.cli = load_cli()
        # The four CB sites share one pricing page.
        cls.gyms = [g for g in DATA["gyms"] if g.get("brand") == "City Bouldering"]

    def test_detects_unrecorded_sale_prices(self):
        # Baseline that knows the gyms but NOT the summer sale.
        known = self.cli.known_amounts(self.gyms)
        hits = self.cli.promo_candidates(PROMO_FIXTURE, known)
        self.assertTrue(hits, "summer sale block was not flagged")
        found = {a for _, amounts in hits for a in amounts}
        self.assertIn(133.2, found)
        self.assertIn(117.0, found)
        # The pre-sale prices are already in gyms.json, so they are not "new".
        self.assertNotIn(148.0, found)
        self.assertNotIn(130.0, found)

    def test_silent_once_promo_is_recorded(self):
        known = (self.cli.known_amounts(self.gyms)
                 | self.cli.promo_amounts_known(DATA, {"City Bouldering"}))
        self.assertEqual(self.cli.promo_candidates(PROMO_FIXTURE, known), [],
                         "a promo recorded in limited_promotions still reported")

    def test_price_written_with_trailing_zero_still_matches(self):
        # gyms.json records "£133.20"; the page renders "£133.2".
        known = self.cli._amounts_in("£133.20 (£117 concession)")
        self.assertIn(133.2, known)

    def test_offer_as_a_verb_is_not_a_promo(self):
        hits = self.cli.promo_candidates(PROSE_FIXTURE, set())
        self.assertEqual(hits, [], "matched 'offer' used as a verb")

    def test_expired_promo_stops_suppressing_detection(self):
        # A recorded offer normally silences its own re-detection. Once it is
        # past its end date that suppression must lift, so a gym still running
        # the offer shows up again.
        data = {"limited_promotions": [
            {"id": "x", "brand": "City Bouldering", "price": "£133.20",
             "details": "", "valid": "", "expires": "2024-01-31",
             "status": "expired"}]}
        live = self.cli.promo_amounts_known(data, {"City Bouldering"})
        self.assertNotIn(133.2, live)
        data["limited_promotions"][0].update(expires="2099-01-01", status="active")
        self.assertIn(133.2, self.cli.promo_amounts_known(data, {"City Bouldering"}))

    def test_promo_with_no_expiry_is_live(self):
        p = {"id": "x", "status": "active", "expires": None}
        self.assertFalse(self.cli.promo_expired(p, dt.date(2030, 1, 1)))

    def test_overlapping_hits_collapse(self):
        # "10% OFF" and "SUMMER SALE" both match inside one block; the block
        # should be reported once, not twice.
        hits = self.cli.promo_candidates(PROMO_FIXTURE,
                                         self.cli.known_amounts(self.gyms))
        self.assertEqual(len(hits), 1, "same block reported %d times" % len(hits))


if __name__ == "__main__":
    unittest.main()
