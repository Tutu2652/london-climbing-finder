#!/usr/bin/env python3
"""
Makes the App Store / advert screenshots in ios/screenshots/.

    python3 ios/tools/ad_screenshots.py demo      # prepare the booted simulator
    python3 ios/tools/ad_screenshots.py restore   # put the simulator's own data back
    python3 ios/tools/ad_screenshots.py compose   # frame build/screenshots/*.png
    python3 ios/tools/ad_screenshots.py compose stats   # remake just the named shots

`demo` backs up the app's data on the booted simulator, then sets the start location
to Trafalgar Square (WC2N 5DU), fills the logbook with made-up sessions and cleans up
the status bar. Capture each screen with
`xcrun simctl io booted screenshot build/screenshots/<name>.png`, using the names in
SHOTS below, then run `compose`. Run `restore` when you're done.

Needs macOS (Xcode simulator, SF Pro font) and Pillow (`pip install pillow`).
"""
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import uuid
from datetime import datetime, timedelta, timezone

IOS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(IOS)
RAW = os.path.join(ROOT, "build", "screenshots")
OUT = os.path.join(IOS, "screenshots")
ICON = os.path.join(IOS, "ClimbingFinder", "Assets.xcassets", "AppIcon.appiconset", "AppIcon.png")
BACKUP = os.path.expanduser("~/Library/Application Support/ClimbFinderScreenshots")
ORIGIN = {"label": "WC2N 5DU", "lat": 51.508, "lng": -0.1281}

# output name, raw capture, headline, subline, accent ("green" or "amber")
SHOTS = [
    ("01-cheapest", "cheapest", "Find the cheapest\nclimb today", "33 gyms ranked by price, with when to go", "green"),
    ("02-map", "map", "Every London wall\non one map", "Live TfL journey times from where you are", "green"),
    ("03-deals", "deals", "Never miss\na cheap slot", "Off-peak, student and bargain slots", "amber"),
    ("04-gym", "gym", "Know a gym\nbefore you go", "Prices, hours, directions and your notes", "green"),
    ("05-gyms", "gyms", "Search by area,\nzone or wall type", "Sort by chain, price or distance", "green"),
    ("06-logbook", "logbook", "Log every\nsession", "Sends, top grades and what you spent", "green"),
    ("07-stats", "stats", "Watch yourself\nget stronger", "Grade pyramid and progress over time", "amber"),
]

# gym id, date, start, minutes, entry price, climbs (V grade, result)
DEMO_SESSIONS = [
    ("lcc_eustonwall", -28, (18, 30), 110, 17.0, [(2, "flash"), (3, "send"), (3, "send"), (4, "send"), (4, "attempt"), (5, "attempt")]),
    ("mileend", -24, (11, 0), 135, 14.5, [(2, "flash"), (3, "flash"), (3, "send"), (4, "send"), (4, "send"), (5, "attempt")]),
    ("the_font_borough", -19, (19, 0), 100, 16.0, [(3, "send"), (3, "send"), (4, "send"), (4, "send"), (5, "send"), (5, "attempt")]),
    ("castle", -16, (14, 0), 150, 18.0, [(2, "flash"), (3, "send"), (4, "flash"), (4, "send"), (5, "send"), (6, "attempt")]),
    ("cb_aldgate", -11, (10, 30), 120, 7.5, [(3, "flash"), (4, "send"), (4, "send"), (5, "send"), (5, "send"), (6, "attempt")]),
    ("arch_bermondsey", -6, (18, 45), 115, 16.0, [(3, "send"), (4, "flash"), (4, "send"), (5, "send"), (5, "send"), (6, "send"), (6, "attempt")]),
    ("mileend", -2, (12, 0), 140, 14.5, [(3, "flash"), (4, "send"), (4, "send"), (5, "send"), (5, "flash"), (6, "send"), (6, "send"), (7, "attempt")]),
]


def sh(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def bundle_id():
    out = sh("xcodebuild", "-project", os.path.join(IOS, "ClimbingFinder.xcodeproj"),
             "-target", "ClimbingFinder", "-showBuildSettings")
    for line in out.splitlines():
        k, _, v = line.strip().partition(" = ")
        if k == "PRODUCT_BUNDLE_IDENTIFIER":
            return v
    sys.exit("Could not read the bundle ID")


def app_paths(bid):
    data = sh("xcrun", "simctl", "get_app_container", "booted", bid, "data")
    return (os.path.join(data, "Library", "Preferences", bid + ".plist"),
            os.path.join(data, "Library", "Application Support"))


def demo():
    bid = bundle_id()
    prefs, support = app_paths(bid)
    subprocess.run(["xcrun", "simctl", "terminate", "booted", bid], capture_output=True)
    if os.path.exists(BACKUP):
        sys.exit(f"A backup already exists in {BACKUP}; run `restore` first.")
    os.makedirs(BACKUP)
    shutil.copy2(prefs, BACKUP)
    for f in os.listdir(support):
        if f.startswith("default.store"):
            shutil.copy2(os.path.join(support, f), BACKUP)

    # Through the simulator's defaults, so its preferences cache doesn't undo it.
    origin = json.dumps(ORIGIN, separators=(",", ":")).encode().hex()
    sh("xcrun", "simctl", "spawn", "booted", "defaults", "write", prefs, "origin", "-data", origin)
    sh("xcrun", "simctl", "spawn", "booted", "defaults", "write", prefs, "tab", "cheapest")
    seed_logbook(os.path.join(support, "default.store"))
    sh("xcrun", "simctl", "status_bar", "booted", "override", "--time", "9:41", "--batteryState", "charged",
       "--batteryLevel", "100", "--cellularBars", "4", "--wifiBars", "3", "--dataNetwork", "wifi")
    sh("xcrun", "simctl", "location", "booted", "set", f"{ORIGIN['lat']},{ORIGIN['lng']}")
    sh("xcrun", "simctl", "launch", "booted", bid)
    print(f"Ready. Backup in {BACKUP}. If travel times don't show, pull down on Cheapest.")


def seed_logbook(store):
    """Replaces the SwiftData logbook with DEMO_SESSIONS (Core Data dates count from 2001)."""
    epoch = datetime(2001, 1, 1, tzinfo=timezone.utc)
    today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    db = sqlite3.connect(store)
    db.execute("delete from ZCLIMB")
    db.execute("delete from ZCLIMBSESSION")
    climb = 0
    for pk, (gym, day, (hh, mm), mins, price, climbs) in enumerate(DEMO_SESSIONS, 1):
        start = (today + timedelta(days=day, hours=hh, minutes=mm)).astimezone(timezone.utc)
        t0 = (start - epoch).total_seconds()
        db.execute("insert into ZCLIMBSESSION (Z_PK,Z_ENT,Z_OPT,ZFEELING,ZEND,ZPRICE,ZSTART,ZGYMID,ZNOTES,ZID)"
                   " values (?,2,1,0,?,?,?,?,'',?)", (pk, t0 + mins * 60, price, t0, gym, uuid.uuid4().bytes))
        for i, (grade, result) in enumerate(climbs, 1):
            climb += 1
            db.execute("insert into ZCLIMB (Z_PK,Z_ENT,Z_OPT,ZGRADE,ZSESSION,ZLOGGEDAT,ZDISCIPLINERAW,ZRESULTRAW,ZID,ZISCOMP,ZFELTGRADESET)"
                       " values (?,1,1,?,?,?,'boulder',?,?,0,0)",
                       (climb, grade, pk, t0 + i * mins * 60 / (len(climbs) + 1), result, uuid.uuid4().bytes))
    db.execute("update Z_PRIMARYKEY set Z_MAX=? where Z_NAME='Climb'", (climb,))
    db.execute("update Z_PRIMARYKEY set Z_MAX=? where Z_NAME='ClimbSession'", (len(DEMO_SESSIONS),))
    db.commit()


def restore():
    bid = bundle_id()
    prefs, support = app_paths(bid)
    if not os.path.exists(BACKUP):
        sys.exit("No backup to restore.")
    subprocess.run(["xcrun", "simctl", "terminate", "booted", bid], capture_output=True)
    for f in os.listdir(support):
        if f.startswith("default.store"):
            os.remove(os.path.join(support, f))
    for f in os.listdir(BACKUP):
        if f.startswith("default.store"):
            shutil.copy2(os.path.join(BACKUP, f), support)
    sh("xcrun", "simctl", "spawn", "booted", "defaults", "import", prefs, os.path.join(BACKUP, bid + ".plist"))
    sh("xcrun", "simctl", "status_bar", "booted", "clear")
    shutil.rmtree(BACKUP)
    print("Restored.")


def compose(only=()):
    from PIL import Image, ImageDraw, ImageFilter, ImageFont

    W, H = 1290, 2796                      # App Store 6.7"/6.9" portrait
    top_col, bottom_col = (16, 20, 24), (22, 48, 34)
    colours = {"green": (93, 176, 122), "amber": (226, 161, 60)}
    white, muted = (244, 246, 248), (170, 178, 190)

    def font(size, weight):
        f = ImageFont.truetype("/System/Library/Fonts/SFNS.ttf", size)
        f.set_variation_by_name(weight)
        return f

    def background():
        bg = Image.new("RGB", (W, H))
        d = ImageDraw.Draw(bg)
        for y in range(H):
            t = y / H
            d.line([(0, y), (W, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top_col, bottom_col)))
        glow = Image.new("L", (W, H), 0)
        ImageDraw.Draw(glow).ellipse((W * 0.1, H * 0.35, W * 0.9, H * 0.95), fill=90)
        bg.paste(Image.new("RGB", (W, H), colours["green"]), (0, 0), glow.filter(ImageFilter.GaussianBlur(220)))
        return bg

    def centred(d, y, text, f, fill, max_w=W - 160, gap=14):
        lines = []
        for para in text.split("\n"):
            cur = ""
            for w in para.split():
                trial = (cur + " " + w).strip()
                if d.textlength(trial, font=f) <= max_w:
                    cur = trial
                else:
                    lines.append(cur)
                    cur = w
            lines.append(cur)
        for ln in lines:
            d.text((W / 2, y), ln, font=f, fill=fill, anchor="ma")
            y += f.size + gap
        return y

    def phone(path, screen_w):
        s = Image.open(path).convert("RGB")
        # simctl captures leave out the Dynamic Island; draw it back in
        k = s.width / 1206
        ImageDraw.Draw(s).rounded_rectangle((460 * k, 42 * k, 746 * k, 152 * k), 55 * k, fill=(0, 0, 0))
        s = s.resize((screen_w, round(s.height * screen_w / s.width)), Image.LANCZOS)
        bez, r = 26, 118
        body = Image.new("RGBA", (s.width + 2 * bez, s.height + 2 * bez), (0, 0, 0, 0))
        ImageDraw.Draw(body).rounded_rectangle((0, 0, body.width - 1, body.height - 1), r + bez,
                                               fill=(10, 10, 12, 255), outline=(70, 76, 88, 255), width=4)
        mask = Image.new("L", s.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, s.width - 1, s.height - 1), r, fill=255)
        body.paste(s, (bez, bez), mask)
        return body

    os.makedirs(OUT, exist_ok=True)
    shots = [s for s in SHOTS if not only or s[1] in only]
    if only and len(shots) != len(set(only)):
        sys.exit("Unknown shot name; use " + ", ".join(s[1] for s in SHOTS))
    if not only:
        compose_cover(background, centred, font, colours, white, muted)
    for name, raw, title, sub, accent in shots:
        bg = background()
        d = ImageDraw.Draw(bg)
        y = centred(d, 190, title, font(112, "Bold"), white, gap=12)
        centred(d, y + 34, sub, font(52, "Medium"), colours[accent], gap=12)
        p = phone(os.path.join(RAW, raw + ".png"), 920)
        x, top = (W - p.width) // 2, 700
        shadow = Image.new("L", (W, H), 0)
        ImageDraw.Draw(shadow).rounded_rectangle((x + 10, top + 40, x + p.width - 10, top + p.height + 40), 150, fill=170)
        bg.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), shadow.filter(ImageFilter.GaussianBlur(50)))
        bg.paste(p, (x, top), p)
        bg.save(os.path.join(OUT, name + ".png"), optimize=True)
    print(f"Wrote {len(shots) + (0 if only else 1)} images to {os.path.relpath(OUT, ROOT)}/")


def compose_cover(background, centred, font, colours, white, muted):
    from PIL import Image, ImageDraw

    W, H = 1290, 2796
    bg = background()
    d = ImageDraw.Draw(bg)
    icon = Image.open(ICON).convert("RGBA").resize((380, 380), Image.LANCZOS)
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 379, 379), 86, fill=255)
    bg.paste(icon, ((W - 380) // 2, 640), mask)
    y = centred(d, 1130, "London\nClimbing Finder", font(118, "Bold"), white, gap=10)
    y = centred(d, y + 40, "The cheapest climb in London,\nand when to go.", font(58, "Medium"), colours["green"])
    y += 150
    for big, small in [("33", "gyms"), ("Peak, off-peak", "and student prices"), ("Live", "TfL travel times")]:
        d.text((W / 2, y), big, font=font(80, "Heavy"), fill=white, anchor="ma")
        d.text((W / 2, y + 96), small, font=font(46, "Regular"), fill=muted, anchor="ma")
        y += 230
    d.text((W / 2, H - 170), "Free · iPhone · web", font=font(44, "Semibold"), fill=muted, anchor="ma")
    bg.save(os.path.join(OUT, "00-cover.png"), optimize=True)


if __name__ == "__main__":
    cmds = {"demo": demo, "restore": restore, "compose": compose}
    if len(sys.argv) < 2 or sys.argv[1] not in cmds or (len(sys.argv) > 2 and sys.argv[1] != "compose"):
        sys.exit(__doc__)
    if sys.argv[1] == "compose":
        compose(sys.argv[2:])
    else:
        cmds[sys.argv[1]]()
