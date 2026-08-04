#!/usr/bin/env python3
"""
Local weekly offer check - detect, draft, review, then commit yourself.

This is the local equivalent of the price-check GitHub Action, split so that
nothing reaches git without you reading it first:

    python3 tools/weekly_offer_check.py              # 1. what changed on the gyms' sites?
    python3 tools/weekly_offer_check.py --draft      # 2. write offers-draft.json skeletons
    $EDITOR offers-draft.json                        # 3. fill in the TODOs, delete junk
    python3 tools/weekly_offer_check.py --apply      # 4. merge into gyms.json + sync + test
    git diff                                         # 5. review
    git commit                                       #    ...and commit if you're happy

Steps 4 and 5 are deliberately separate: --apply edits your working tree and
stops. It never runs git.

Why a draft file rather than writing gyms.json directly: the checker can tell
you an offer exists and what the amounts are, but not which amount is the adult
rate, what the offer is called, or what its conditions are. That needs a human
(or the cloud routine) to read the page. The draft is a skeleton with the facts
it *can* determine prefilled and TODOs where judgement is required.

No third-party packages. Run from anywhere; paths are resolved relative to the
repo, not your shell's cwd.
"""

import argparse
import datetime as dt
import importlib.util
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GYMS = os.path.join(ROOT, "gyms.json")
DRAFT = os.path.join(ROOT, "offers-draft.json")


def load_cli():
    spec = importlib.util.spec_from_file_location(
        "lcp", os.path.join(ROOT, "london_climbing_prices.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def slugify(text, limit=24):
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return slug[:limit].strip("-") or "offer"


def brand_prefix(brand):
    """City Bouldering -> cb, Climbing District -> cd, Independent -> ind."""
    words = [w for w in re.split(r"\s+", brand or "") if w]
    if len(words) >= 2:
        return "".join(w[0] for w in words[:3]).lower()
    return slugify(brand or "gym", 6)


def find_offers(cli, data, verbose=True):
    """Run the live check and return its NEW OFFERS findings."""
    if not verbose:
        buf, sys.stdout = sys.stdout, open(os.devnull, "w")
        try:
            changed, offers = cli.check_prices(data)
        finally:
            sys.stdout.close()
            sys.stdout = buf
    else:
        changed, offers = cli.check_prices(data)
    return changed, offers


def offer_name(cli, snippet):
    """
    Best guess at what the offer is called: from the promo wording up to the
    first price. "...10% OFF SUMMER SALE - 10 Climb Pass £133.2..." gives
    "10% OFF SUMMER SALE - 10 Climb Pass". Only used to seed the id; the human
    still writes the real title.
    """
    m = cli.PROMO_RE.search(snippet)
    if not m:
        return snippet[:40]
    tail = snippet[m.start():]
    return tail.split("£")[0].strip() or tail[:40]


def build_draft(cli, offers, existing_ids):
    """Turn check findings into skeleton limited_promotions entries."""
    today = dt.date.today().isoformat()
    out = []
    for brand, snippet, amounts, url in offers:
        base = "%s-%s" % (brand_prefix(brand),
                          slugify(offer_name(cli, snippet), 32))
        pid, n = base, 2
        while pid in existing_ids:
            pid, n = "%s-%d" % (base, n), n + 1
        existing_ids.add(pid)
        out.append({
            "id": pid,
            "brand": brand,
            "title": "TODO - short name for this offer",
            "details": "TODO - what it is, conditions, and the pre-discount price",
            "price": "TODO - e.g. £133.20 (£117 concession)",
            "valid": "TODO - e.g. Summer 2026 sale",
            "expires": None,        # ISO date if an end date is known, else null
            "expires_source": None,  # required whenever expires is set: where the date came from
            "source": url,
            "first_seen": today,
            "status": "active",
            "_amounts_detected": amounts,
            "_snippet": snippet[:400],
        })
    return out


def cmd_draft(cli, data):
    changed, offers = find_offers(cli, data)
    if not offers:
        print("\nNothing to draft - no unrecorded offers found.")
        return 0
    existing = {p.get("id") for p in data.get("limited_promotions", [])}
    draft = build_draft(cli, offers, existing)
    with open(DRAFT, "w", encoding="utf-8") as fh:
        json.dump(draft, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print("\nWrote %d skeleton entr%s to %s"
          % (len(draft), "y" if len(draft) == 1 else "ies",
             os.path.relpath(DRAFT, os.getcwd())))
    print("""
Next:
  1. Open it and replace every TODO. Check each offer against its source URL -
     _amounts_detected tells you the numbers, not which is adult vs concession.
  2. Delete any entry that is not a real offer (false positives happen).
  3. Put the prices in the 'price' and 'details' fields with a £ prefix - that
     is what stops the offer being reported again next week.
  4. python3 tools/weekly_offer_check.py --apply""")
    return 0


def cmd_apply(cli, data):
    if not os.path.exists(DRAFT):
        print("No %s - run --draft first." % os.path.relpath(DRAFT, os.getcwd()))
        return 1
    with open(DRAFT, encoding="utf-8") as fh:
        draft = json.load(fh)
    if not isinstance(draft, list):
        print("%s must contain a JSON array." % DRAFT)
        return 1

    problems, ids = [], {p.get("id") for p in data.get("limited_promotions", [])}
    for entry in draft:
        if "expires" not in entry:
            problems.append("%s: missing expires (use null if the page states "
                            "no end date)" % entry.get("id", "?"))
        elif entry["expires"] is not None:
            try:
                dt.date.fromisoformat(entry["expires"])
            except (ValueError, TypeError):
                problems.append("%s: expires must be YYYY-MM-DD or null"
                                % entry.get("id", "?"))
            if not entry.get("expires_source"):
                problems.append("%s: expires is set, so expires_source must say "
                                "where the date came from (a URL, or e.g. "
                                "'in-gym poster, seen 2026-08-05')"
                                % entry.get("id", "?"))
        for field in ("id", "brand", "title", "details", "price", "valid",
                      "source", "first_seen", "status"):
            val = entry.get(field, "")
            if not val:
                problems.append("%s: missing %s" % (entry.get("id", "?"), field))
            elif isinstance(val, str) and val.strip().startswith("TODO"):
                problems.append("%s: %s still says TODO" % (entry["id"], field))
        if entry.get("status") not in ("active", "unverified", "expired"):
            problems.append("%s: status must be active/unverified/expired"
                            % entry.get("id", "?"))
        if entry.get("id") in ids:
            problems.append("%s: id already in gyms.json" % entry["id"])
        if "£" not in (entry.get("price", "") + entry.get("details", "")):
            problems.append("%s: no £ amount in price/details, so the checker "
                            "will report this offer again next week" % entry["id"])
        ids.add(entry.get("id"))
    if problems:
        print("Not applying - fix these in %s first:\n  %s"
              % (os.path.relpath(DRAFT, os.getcwd()), "\n  ".join(problems)))
        return 1

    clean = [{k: v for k, v in e.items() if not k.startswith("_")} for e in draft]
    data.setdefault("limited_promotions", [])[:0] = clean
    with open(GYMS, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
    print("Added %d offer(s) to gyms.json." % len(clean))

    for step in (["python3", "tools/sync_data.py"],
                 ["python3", "-m", "unittest", "discover", "-s", "tests"]):
        print("\n$ %s" % " ".join(step))
        sys.stdout.flush()  # keep our output in order with the subprocess's
        if subprocess.call(step, cwd=ROOT) != 0:
            print("\nFAILED. gyms.json is edited but not verified - review "
                  "before committing, or 'git checkout gyms.json' to undo.")
            return 1

    os.remove(DRAFT)
    print("""
Applied and verified. Nothing has been committed.

  git diff --stat     # what changed
  git diff gyms.json  # the offers you added

Then commit when you're happy. Re-run this script with no flags to confirm the
new offers no longer come up as unrecorded.""")
    return 0


def main():
    ap = argparse.ArgumentParser(
        description="Local weekly offer check for london-climbing-finder",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__)
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--draft", action="store_true",
                   help="write skeleton entries for each new offer to offers-draft.json")
    g.add_argument("--apply", action="store_true",
                   help="merge the edited draft into gyms.json, then sync and test")
    args = ap.parse_args()

    cli = load_cli()
    data = cli.load_data()

    if args.apply:
        return cmd_apply(cli, data)
    if args.draft:
        return cmd_draft(cli, data)

    changed, offers = find_offers(cli, data)
    if offers:
        print("\n-> %d unrecorded offer(s). Draft entries for them with:"
              "\n     python3 tools/weekly_offer_check.py --draft" % len(offers))
    if changed:
        print("\n-> %d stored price(s) no longer on the page. These are NOT "
              "auto-fixed:\n   check each against its URL above and edit "
              "gyms.json by hand." % len(changed))
    return 0


if __name__ == "__main__":
    sys.exit(main())
