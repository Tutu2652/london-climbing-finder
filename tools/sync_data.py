#!/usr/bin/env python3
"""
Single-source-of-truth sync for the London Climbing Finder.

gyms.json is the ONLY place data should be edited. This script pushes it into
every derived copy:

  * index.html                     — replaces the embedded `const DATA = {...};`
  * london_climbing_dashboard.html — same (offline version)
  * london-climbing-deals-all.ics  — regenerated from magic_slots
  * london-climbing-deals-no-student.ics — same, minus student/concession slots

Usage:
    python3 tools/sync_data.py            # rewrite all derived files
    python3 tools/sync_data.py --check    # exit 1 if any derived file is stale
                                          # (used by CI; writes nothing)
"""
import datetime as dt
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GYMS_JSON = os.path.join(ROOT, "gyms.json")
HTML_FILES = ["index.html", "london_climbing_dashboard.html"]
ICS_ALL = "london-climbing-deals-all.ics"
ICS_NS = "london-climbing-deals-no-student.ics"

DATA_START = "const DATA = "
DATA_END = "\n};"


# --------------------------------------------------------------------------- #
# HTML injection
# --------------------------------------------------------------------------- #
def data_block(data):
    return DATA_START + json.dumps(data, indent=2, ensure_ascii=False) + ";"


def inject_html(src, data):
    """Replace the embedded DATA object in an HTML source string."""
    start = src.index(DATA_START)
    end = src.index(DATA_END, start) + len(DATA_END)
    return src[:start] + data_block(data) + src[end:]


# --------------------------------------------------------------------------- #
# ICS generation
# --------------------------------------------------------------------------- #
WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
ICAL_DAY = {"Mon": "MO", "Tue": "TU", "Wed": "WE", "Thu": "TH",
            "Fri": "FR", "Sat": "SA", "Sun": "SU"}
ANCHOR = dt.date(2024, 1, 1)  # a Monday; recurring events start here


def is_student_only(slot):
    # A slot is student/concession-only when its label (ignoring parenthetical
    # asides like BOGOF's "(£17 for 2, students £15 for 2)") says so.
    label = (slot.get("label") or "").lower()
    if "concession" in label:
        return True
    main = re.sub(r"\([^)]*\)", "", label)
    return "student" in main


def short_name(gym):
    n = gym["name"]
    n = n.replace(" (LCC)", "")
    n = n.replace("Climbing District - ", "CD ")
    n = n.replace("City Bouldering ", "")
    n = n.replace("The Font ", "Font ")
    n = n.replace(" (City Bouldering)", "")
    return n


def summary_label(label):
    # Drop "(concession …)" qualifiers from the summary; keep others (e.g. BOGOF prices).
    return re.sub(r"\s*\(concession[^)]*\)", "", label, flags=re.I).strip()


def ics_escape(text):
    return (text.replace("\\", "\\\\").replace(";", "\\;")
                .replace(",", "\\,").replace("\n", "\\n"))


def fold(line):
    """RFC 5545 line folding: max 75 octets, continuation lines start with a space."""
    raw = line.encode("utf-8")
    if len(raw) <= 74:
        return [line]
    out, cur, cur_len = [], "", 0
    limit = 74
    for ch in line:
        chlen = len(ch.encode("utf-8"))
        if cur_len + chlen > limit:
            out.append(cur)
            cur, cur_len, limit = " " + ch, 1 + chlen, 74
        else:
            cur += ch
            cur_len += chlen
    if cur:
        out.append(cur)
    return out


def first_weekday_on_or_after(anchor, day_name):
    target = WEEKDAYS.index(day_name)
    delta = (target - anchor.weekday()) % 7
    return anchor + dt.timedelta(days=delta)


def slot_events(gym, slot, variant, counter, dtstamp):
    """One VEVENT per recurrence day of a magic slot."""
    events = []
    price = slot["price"]
    label = slot.get("label") or "deal"
    url = gym.get("prices_url") or gym.get("website") or ""
    desc_bits = ["£%.2f" % price]
    if is_student_only(slot):
        desc_bits.append("Students/concession only (ID).")
    if slot.get("except_bank_holidays"):
        desc_bits.append("Not valid on bank holidays.")
    if slot.get("prebook"):
        desc_bits.append("Pre-book.")
    if url:
        desc_bits.append(url)
    description = " ".join(desc_bits)
    summary = "£%.2f %s — %s" % (price, summary_label(label), short_name(gym))
    location = gym.get("address") or gym.get("postcode") or ""
    all_day = slot.get("start") == "00:00" and slot.get("end") in ("23:59", "24:00")

    for day in slot["days"]:
        counter[0] += 1
        start_date = first_weekday_on_or_after(ANCHOR, day)
        lines = ["BEGIN:VEVENT",
                 "UID:cld-%s-%s-%d@londonclimbingfinder" % (variant, gym["id"], counter[0]),
                 "DTSTAMP:%s" % dtstamp]
        if all_day:
            lines.append("DTSTART;VALUE=DATE:%s" % start_date.strftime("%Y%m%d"))
            lines.append("DTEND;VALUE=DATE:%s" %
                         (start_date + dt.timedelta(days=1)).strftime("%Y%m%d"))
        else:
            lines.append("DTSTART:%sT%s00" %
                         (start_date.strftime("%Y%m%d"), slot["start"].replace(":", "")))
            lines.append("DTEND:%sT%s00" %
                         (start_date.strftime("%Y%m%d"), slot["end"].replace(":", "")))
        lines.append("RRULE:FREQ=WEEKLY;BYDAY=%s" % ICAL_DAY[day])
        lines.append("SUMMARY:%s" % ics_escape(summary))
        lines.append("DESCRIPTION:%s" % ics_escape(description))
        if location:
            lines.append("LOCATION:%s" % ics_escape(location))
        lines.append("END:VEVENT")
        events.append(lines)
    return events


def build_ics(data, variant):
    """variant: 'all' (every deal) or 'ns' (skip student/concession-only)."""
    stamp_date = data.get("_meta", {}).get("last_research_date", "2026-01-01")
    dtstamp = stamp_date.replace("-", "") + "T000000Z"
    name = ("London Climbing Deals" if variant == "all"
            else "London Climbing Deals (no student-only)")
    lines = ["BEGIN:VCALENDAR", "VERSION:2.0",
             "PRODID:-//London Climbing Finder//Deals//EN",
             "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
             "X-WR-CALNAME:%s" % name,
             "REFRESH-INTERVAL;VALUE=DURATION:P1W", "X-PUBLISHED-TTL:P1W",
             "X-WR-CALDESC:Recurring special cheap climbing slots across London gyms.",
             "X-WR-TIMEZONE:Europe/London"]
    counter = [0]
    for gym in data["gyms"]:
        for slot in gym.get("magic_slots", []):
            if variant == "ns" and is_student_only(slot):
                continue
            for ev in slot_events(gym, slot, variant, counter, dtstamp):
                lines.extend(ev)
    lines.append("END:VCALENDAR")
    folded = []
    for line in lines:
        folded.extend(fold(line))
    return "\r\n".join(folded) + "\r\n"


# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #
def main():
    check = "--check" in sys.argv
    with open(GYMS_JSON, encoding="utf-8") as fh:
        data = json.load(fh)

    targets = {}  # path -> new content
    for name in HTML_FILES:
        path = os.path.join(ROOT, name)
        with open(path, encoding="utf-8") as fh:
            src = fh.read()
        targets[path] = inject_html(src, data)
    targets[os.path.join(ROOT, ICS_ALL)] = build_ics(data, "all")
    targets[os.path.join(ROOT, ICS_NS)] = build_ics(data, "ns")

    stale = []
    for path, new in targets.items():
        try:
            with open(path, encoding="utf-8", newline="") as fh:
                old = fh.read()
        except FileNotFoundError:
            old = None
        if old != new:
            stale.append(path)
            if not check:
                with open(path, "w", encoding="utf-8", newline="") as fh:
                    fh.write(new)

    rel = [os.path.relpath(p, ROOT) for p in stale]
    if check:
        if stale:
            print("STALE derived files (run `python3 tools/sync_data.py` and commit):")
            for r in rel:
                print("  - " + r)
            sys.exit(1)
        print("All derived files are in sync with gyms.json.")
    else:
        if stale:
            print("Updated: " + ", ".join(rel))
        else:
            print("Nothing to do — everything already in sync.")


if __name__ == "__main__":
    main()
