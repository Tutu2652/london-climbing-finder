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
        # Rise "Board Friday (board only)" £5 must NOT be offered as day entry.
        price, label = self.price("rise", "2026-07-10", "12:00", conc=False)
        self.assertGreater(price, 5.0, "board-only slot leaked into pricing")

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


if __name__ == "__main__":
    unittest.main()
