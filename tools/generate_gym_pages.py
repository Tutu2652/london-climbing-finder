#!/usr/bin/env python3
"""Generate crawlable, no-JavaScript detail pages for every gym."""

import html
import json
import os
import re
import sys
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from london_climbing_prices import WEEKDAYS, week_bands  # noqa: E402


SITE = "https://london-climbing-finder.pages.dev"


def slug(gym):
    return gym["id"].replace("_", "-")


def esc(value):
    return html.escape(str(value or ""), quote=True)


def logo_tile(gym):
    """The gym's logo beside the page title, as on the finder; decorative, so alt=""."""
    logo = gym.get("logo")
    if not logo:
        return ""
    bg = ' style="background:%s"' % esc(gym["logo_background"]) if gym.get("logo_background") else ""
    # Logos live in assets/logos/, two levels up from gyms/<slug>/.
    return '<span class="logo"%s><img src="../../%s" alt="" width="56" height="56"></span>' % (bg, esc(logo))


def money(value):
    return "Not listed" if value is None else "£%.2f" % value


def type_label(value):
    return (value or "climbing").replace("+", " and ").title()


def markdown_links(value):
    """Escape free text, then restore its small [label](https://url) subset."""
    safe = esc(value)
    return re.sub(
        r"\[([^\]]+)\]\((https?://[^)\s]+)\)",
        lambda m: '<a href="%s" rel="noopener">%s</a>' % (m.group(2), m.group(1)),
        safe,
    )


def hours_rows(gym):
    hours = gym.get("temporary_hours") or gym.get("hours") or {}
    return "".join(
        "<tr><th scope=\"row\">%s</th><td>%s</td></tr>"
        % (day, "%s–%s" % tuple(hours[day]) if hours.get(day) else ("Closed" if hours else "Not listed"))
        for day in ("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    )


def deal_items(gym):
    items = []
    payg = gym["payg"]
    if payg.get("adult_offpeak") is not None:
        items.append("Adult off-peak: <strong>%s</strong>" % money(payg["adult_offpeak"]))
    if payg.get("concession_offpeak") is not None:
        items.append("Concession off-peak: <strong>%s</strong>" % money(payg["concession_offpeak"]))
    for slot in gym.get("magic_slots") or []:
        days = ", ".join(slot.get("days") or [])
        when = "all day" if slot.get("start") == "00:00" and slot.get("end") == "23:59" else "%s–%s" % (slot.get("start", ""), slot.get("end", ""))
        items.append("<strong>%s</strong> — %s, %s (%s)" % (
            money(slot.get("price")), esc(slot.get("label") or "Deal"), esc(days), esc(when)))
    return "".join("<li>%s</li>" % item for item in items) or "<li>No separate off-peak deal is currently listed.</li>"


def promo_items(data, gym):
    def applies(promo):
        if promo.get("status") == "expired" or promo.get("brand") != gym.get("brand"):
            return False
        if "not fulham" in (promo.get("details") or "").lower() and gym["id"] == "climbing_co_fulham":
            return False
        if gym.get("brand") != "Independent":
            return True
        promo_host = urllib.parse.urlparse(promo.get("source") or "").netloc.removeprefix("www.")
        gym_host = urllib.parse.urlparse(gym.get("website") or "").netloc.removeprefix("www.")
        return bool(promo_host and gym_host and (promo_host.endswith(gym_host) or gym_host.endswith(promo_host)))

    promos = [p for p in data.get("limited_promotions", []) if applies(p)]
    if not promos:
        return ""
    rows = "".join(
        '<li><a href="%s" rel="noopener"><strong>%s</strong></a> — %s. %s</li>'
        % (esc(p.get("source")), esc(p.get("title")), esc(p.get("price")), esc(p.get("valid")))
        for p in promos
    )
    return "<section><h2>Current limited offers</h2><ul>%s</ul></section>" % rows


# --------------------------------------------------------------------------- #
# Prices through the week
# --------------------------------------------------------------------------- #
# Block colours, cheapest first: (background, text). Each pair passes WCAG AA.
WEEK_RAMP = [("#75ca91", "#06210f"), ("#5aa877", "#06210f"), ("#2f7350", "#ffffff"),
             ("#2b5a4a", "#ffffff"), ("#2f4a4d", "#ffffff"), ("#343a47", "#e8ebf0")]

WEEK_CSS = """
.sr{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap}
.who{display:flex;gap:8px;margin:0 0 12px}
.who label{padding:6px 12px;border:1px solid var(--line);border-radius:999px;cursor:pointer;font-size:.9rem;color:var(--mut)}
#who-a:checked~.who label[for=who-a],#who-c:checked~.who label[for=who-c]{background:var(--accent);border-color:var(--accent);color:#06210f;font-weight:700}
#who-a:focus-visible~.who label[for=who-a],#who-c:focus-visible~.who label[for=who-c]{outline:3px solid var(--accent2);outline-offset:2px}
#who-a:checked~.wk-c,#who-c:checked~.wk-a{display:none}
.ro{display:flex;flex-wrap:wrap;align-items:baseline;gap:4px 12px;background:var(--bg);border:1px solid var(--line);border-radius:10px;padding:10px 12px;margin:0 0 14px;min-height:3.4rem}
.ro strong{font-size:1.5rem;line-height:1.2}.ro .rk,.ro .rl{color:var(--mut);font-size:.9rem}
.ax,.dr{display:grid;grid-template-columns:2.6rem minmax(0,1fr);align-items:center}
.ax div{position:relative;height:1.2rem}.ax div span{position:absolute;bottom:0;font-size:.75rem;color:var(--mut);border-left:1px solid var(--line);padding-left:3px;line-height:1.1}
.dr{margin-bottom:3px}.dn{color:var(--mut);font-size:.9rem}
.tr{display:flex;gap:2px;height:30px}.tr i{min-width:0}
.tr button{min-width:24px;padding:0;border:0;border-radius:4px;font:inherit;font-size:.8rem;font-weight:650;cursor:pointer;overflow:hidden;white-space:nowrap}
.tr button:focus-visible{outline:3px solid var(--accent2);outline-offset:1px}.tr button.on{outline:2px solid #fff;outline-offset:-2px}
.tr .cl{color:var(--mut);font-size:.85rem;align-self:center}
.tr .t0{display:none}.wkn .js{display:none}.js .wkn .js{display:inline}
@media(max-width:600px){.tr .t1{display:none}}
"""

WEEK_JS = """<script>
document.documentElement.classList.add('js');
document.querySelectorAll('.wk').forEach(function(wk){
  var ro=wk.querySelector('.ro');
  function show(b){
    wk.querySelectorAll('button.on').forEach(function(x){x.classList.remove('on')});
    b.classList.add('on');
    ro.textContent='';
    [['strong',b.dataset.p],['span',b.dataset.w],['span',b.dataset.l]].forEach(function(part,i){
      var el=document.createElement(part[0]); el.textContent=part[1]; if(i===2) el.className='rl'; ro.appendChild(el);
    });
  }
  wk.addEventListener('click',function(e){var b=e.target.closest('button'); if(b) show(b);});
  wk.addEventListener('mouseover',function(e){var b=e.target.closest('button'); if(b) show(b);});
  wk.addEventListener('focusin',function(e){var b=e.target.closest('button'); if(b) show(b);});
});
</script>"""


def short_money(price):
    """£8 rather than £8.00, to fit inside a block; pence are kept (£10.50)."""
    return "£%d" % price if price == int(price) else "£%.2f" % price


def hhmm(minutes):
    return "%02d:%02d" % (minutes // 60, minutes % 60)


def band_name(band, holiday_note=True):
    """A readable name for a band's rate: 'Peak', 'Off-peak', or the deal's own name."""
    label = band["label"]
    deal = re.fullmatch(r"super off-peak \((.*)\)", label)
    name = deal.group(1) if deal else label[:1].upper() + label[1:]
    name = name.replace(" (concession)", "").replace("(concession, ", "(")
    return name + (" · not on bank holidays" if holiday_note and band["not_bank_holidays"] else "")


def day_runs(days):
    """[Mon,Tue,Wed,Sat] -> 'Mon–Wed, Sat'."""
    idx = sorted(WEEKDAYS.index(d) for d in days)
    runs = []
    for i in idx:
        if runs and i == runs[-1][-1] + 1:
            runs[-1].append(i)
        else:
            runs.append([i])
    return ", ".join(
        "%s–%s" % (WEEKDAYS[r[0]], WEEKDAYS[r[-1]]) if len(r) >= 3 else ", ".join(WEEKDAYS[i] for i in r)
        for r in runs)


def cheapest_summary(week):
    """The week's lowest price, when it applies and what it is called."""
    bands = [(day, b) for day in WEEKDAYS for b in week.get(day, [])]
    low = min(b["price"] for _, b in bands)
    times, names = {}, []
    for day, b in bands:
        if b["price"] == low:
            times.setdefault((b["start"], b["end"]), []).append(day)
            if band_name(b, False) not in names:
                names.append(band_name(b, False))
    when = "; ".join("%s %s–%s" % (day_runs(days), hhmm(a), hhmm(z)) for (a, z), days in times.items())
    return low, when, ", ".join(names)


def week_grid(week, who, colours, first, last):
    """One climber's week: a readout, an hour scale and a row of price blocks per day."""
    span = last - first
    ticks = "".join('<span style="left:%.2f%%">%d</span>' % ((h * 60 - first) * 100 / span, h)
                    for h in range(first // 60, last // 60 + 1)
                    if h % 3 == 0 and (h * 60 - first) / span < 0.94)
    rows = []
    for day in WEEKDAYS:
        bands = week.get(day)
        if not bands:
            rows.append('<div class="dr"><span class="dn">%s</span><div class="tr"><span class="cl">Closed</span></div></div>' % day)
            continue
        cells, at = [], first
        for b in bands:
            if b["start"] > at:
                cells.append('<i style="flex:%d"></i>' % (b["start"] - at))
            when = "%s %s–%s" % (day, hhmm(b["start"]), hhmm(b["end"]))
            text = short_money(b["price"])
            share = (b["end"] - b["start"]) / span
            need = len(text) * 7.5 + 8  # px the price needs inside its block
            fit = "" if share * 280 >= need else "t1" if share * 620 >= need else "t0"
            bg, ink = colours[b["price"]]
            name = band_name(b)
            cells.append(
                '<button type="button" style="flex:%d;background:%s;color:%s" title="%s" aria-label="%s" '
                'data-p="%s" data-w="%s" data-l="%s"><span%s>%s</span></button>' % (
                    b["end"] - b["start"], bg, ink, esc("%s · %s · %s" % (when, money(b["price"]), name)),
                    esc("%s, %s, %s" % (when, money(b["price"]), name)), money(b["price"]), esc(when), esc(name),
                    ' class="%s"' % fit if fit else "", text))
            at = b["end"]
        if at < last:
            cells.append('<i style="flex:%d"></i>' % (last - at))
        rows.append('<div class="dr"><span class="dn">%s</span><div class="tr">%s</div></div>' % (day, "".join(cells)))
    low, when, names = cheapest_summary(week)
    readout = ('<p class="ro" aria-live="polite"><span class="rk">Cheapest</span><strong>%s</strong>'
               '<span>%s</span><span class="rl">%s</span></p>' % (money(low), esc(when), esc(names)))
    return ('<div class="wk wk-%s">%s<div class="ax" aria-hidden="true"><span></span><div>%s</div></div>%s</div>'
            % (who, readout, ticks, "".join(rows)))


def week_section(gym):
    """'Prices through the week': what a day pass costs at every opening hour.

    Returns (html, needs_script). Gyms without opening hours get nothing; gyms that
    charge one price all week get a sentence instead of a grid.
    """
    adult, conc = week_bands(gym, False), week_bands(gym, True)
    if not adult and not conc:
        return "", False
    prices = lambda week: sorted({b["price"] for bands in week.values() for b in bands})
    pa, pc = prices(adult), prices(conc)
    if len(pa) <= 1 and len(pc) <= 1:
        parts = ["%s adult" % money(pa[0])] if pa else []
        if pc and pc != pa:
            parts.append("%s student / concession" % money(pc[0]))
        return ("<section><h2>Prices through the week</h2><p>The same price whenever it is open: %s.</p></section>"
                % ", ".join(parts)), False
    every = sorted(set(pa) | set(pc))
    steps = len(WEEK_RAMP) - 1
    colours = {p: WEEK_RAMP[round(i * steps / (len(every) - 1))] for i, p in enumerate(every)}
    both = [b for week in (adult, conc) for bands in week.values() for b in bands]
    first = min(b["start"] for b in both) // 60 * 60
    last = -(-max(b["end"] for b in both) // 60) * 60
    toggle = ""
    if adult == conc:
        grids = week_grid(adult, "a", colours, first, last)
    else:
        toggle = ('<input class="sr" type="radio" name="who" id="who-a" checked>'
                  '<input class="sr" type="radio" name="who" id="who-c">'
                  '<div class="who"><label for="who-a">Adult</label><label for="who-c">Student / concession</label></div>')
        grids = week_grid(adult, "a", colours, first, last) + week_grid(conc, "c", colours, first, last)
    return ('<section><h2>Prices through the week</h2>%s%s<p class="muted wkn">An ordinary week; bank holidays '
            'can differ.<span class="js"> Tap a block for its price and times.</span></p></section>'
            % (toggle, grids)), True


def gym_page(data, gym):
    page_url = "%s/gyms/%s/" % (SITE, slug(gym))
    description = ("Compare day-pass, concession, off-peak and membership prices for %s "
                   "in %s, London. Last checked %s." % (
                       gym["name"], gym.get("area") or gym.get("borough"),
                       gym.get("last_checked") or data["_meta"]["last_research_date"]))
    payg = gym["payg"]
    membership = gym.get("membership") or {}
    checked = gym.get("last_checked") or data["_meta"]["last_research_date"]
    directions = "https://www.google.com/maps/dir/?api=1&destination=%s,%s&travelmode=transit" % (gym["lat"], gym["lng"])
    schema = {
        "@context": "https://schema.org",
        "@type": "SportsActivityLocation",
        "name": gym["name"],
        "url": page_url,
        "sameAs": gym.get("website"),
        "description": description,
        "address": {"@type": "PostalAddress", "streetAddress": gym.get("address", ""),
                    "postalCode": gym.get("postcode", ""), "addressLocality": "London",
                    "addressCountry": "GB"},
        "geo": {"@type": "GeoCoordinates", "latitude": gym["lat"], "longitude": gym["lng"]},
        "priceRange": "%s–%s" % (money(min(v for v in payg.values() if v is not None)),
                                   money(max(v for v in payg.values() if v is not None))),
    }
    facilities = esc(gym.get("facilities") or "Not listed")
    if gym.get("ac_note"):
        facilities += " · " + esc(gym["ac_note"])
    student = ("<section><h2>Student and concession information</h2><p>%s</p></section>"
               % esc(gym["student_note"])) if gym.get("student_note") else ""
    classes = ("<section><h2>Classes</h2><p>%s</p></section>"
               % markdown_links(gym["class_info"])) if gym.get("class_info") else ""
    about = ('<p class="about">%s</p>' % esc(gym["about"])) if gym.get("about") else ""
    if gym.get("notice"):
        about = '<p class="notice"><strong>%s</strong></p>' % esc(gym["notice"]) + about
    if gym.get("temporarily_closed"):
        about = '<p class="closed-notice"><strong>%s</strong></p>' % esc(gym["temporarily_closed"]) + about
    hours_title = "Opening hours (temporary)" if gym.get("temporary_hours") else "Opening hours"
    passes = esc(gym.get("passes") or "Not publicly listed")
    verified_label = "Official price source checked" if gym.get("verified") else "Needs verification"
    week, week_script = week_section(gym)
    return """<!doctype html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{name} prices, deals and opening hours</title>
<link rel="icon" href="../../assets/icon.svg" type="image/svg+xml"><link rel="icon" href="../../assets/favicon-32.png" sizes="32x32" type="image/png">
<link rel="apple-touch-icon" href="../../assets/apple-touch-icon.png">
<meta name="description" content="{description}"><link rel="canonical" href="{page_url}">
<meta property="og:type" content="website"><meta property="og:title" content="{name} prices and deals">
<meta property="og:description" content="{description}"><meta property="og:url" content="{page_url}">
<meta property="og:image" content="{site}/assets/og-image.png"><meta name="twitter:card" content="summary_large_image">
<script type="application/ld+json">{schema}</script>
<style>
:root{{--bg:#0f1115;--card:#181b22;--ink:#e8ebf0;--mut:#aeb6c3;--line:#343a47;--accent:#75ca91;--accent2:#e9b45e}}
*{{box-sizing:border-box}} body{{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 system-ui,sans-serif}}
a{{color:var(--accent)}} a:focus-visible{{outline:3px solid var(--accent2);outline-offset:3px;border-radius:3px}}
.skip{{position:absolute;left:-9999px;top:8px;background:#fff;color:#111;padding:8px;z-index:2}}.skip:focus{{left:8px}}
header,main,footer{{max-width:800px;margin:auto;padding:20px}} header{{border-bottom:1px solid var(--line)}}
h1{{font-size:clamp(1.8rem,5vw,2.7rem);line-height:1.12;margin:.5rem 0}} h2{{font-size:1.25rem;margin-top:0}}
.crumb,.muted{{color:var(--mut);font-size:.9rem}} .summary{{font-size:1.08rem;color:var(--mut)}}
.notice{{background:#3a2c12;border:1px solid #e2a13c;color:#ffe7bf;border-radius:10px;padding:10px 12px;margin:12px 0}}
.closed-notice{{background:#3a1d1a;border:1px solid #c96a5a;color:#ffd9d2;border-radius:10px;padding:10px 12px;margin:12px 0}}
.about{{font-size:1.05rem;line-height:1.55;border-left:3px solid var(--accent);padding:2px 0 2px 12px;margin:12px 0}}
.grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px}}
section{{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:18px;margin:14px 0}}
dl{{display:grid;grid-template-columns:max-content 1fr;gap:8px 14px;margin:0}}dt{{color:var(--mut)}}dd{{margin:0;font-weight:650}}
table{{border-collapse:collapse;width:100%}}th,td{{padding:7px 10px;border-bottom:1px solid var(--line);text-align:left}}th{{color:var(--mut)}}
.actions{{display:flex;flex-wrap:wrap;gap:10px;margin:18px 0}}.button{{display:inline-block;padding:10px 14px;border-radius:8px;background:var(--accent);color:#06210f;text-decoration:none;font-weight:700}}.button.alt{{background:var(--card);color:var(--ink);border:1px solid var(--line)}}
.title{{display:flex;align-items:center;gap:14px}}.title h1{{min-width:0}}
.logo{{flex:0 0 56px;width:56px;height:56px;border-radius:12px;background:#fff;border:1px solid var(--line);overflow:hidden;display:grid;place-items:center}}.logo img{{width:100%;height:100%;object-fit:contain;padding:4px}}
@media(max-width:480px){{dl{{grid-template-columns:1fr}}dd{{margin-bottom:7px}}.title{{gap:10px}}.logo{{flex-basis:44px;width:44px;height:44px;border-radius:10px}}}}{week_css}</style></head><body><a class="skip" href="#main">Skip to gym information</a>
<header><nav class="crumb" aria-label="Breadcrumb"><a href="../../index.html">London Climbing Finder</a> / {name}</nav>
<div class="title">{logo}<h1>{name}</h1></div>{about}<p class="summary">{type_label} in {area}, zone {zone}. {facilities}</p>
<p class="muted">{verified_label} · data checked {checked}. Prices can change—confirm before travelling.</p></header>
<main id="main" tabindex="-1"><div class="actions"><a class="button" href="{prices_url}" rel="noopener">Check official prices</a><a class="button alt" href="{directions}" rel="noopener">Public-transport directions</a><a class="button alt" href="../../index.html#all">Compare all gyms</a></div>
<div class="grid"><section><h2>Day-pass prices</h2><dl><dt>Adult peak</dt><dd>{adult_peak}</dd><dt>Adult off-peak</dt><dd>{adult_offpeak}</dd><dt>Concession peak</dt><dd>{conc_peak}</dd><dt>Concession off-peak</dt><dd>{conc_offpeak}</dd><dt>Registration</dt><dd>{registration}</dd></dl></section>
<section><h2>Membership and passes</h2><dl><dt>Adult monthly</dt><dd>{monthly}</dd><dt>Concession monthly</dt><dd>{conc_monthly}</dd><dt>Multi-visit</dt><dd>{passes}</dd></dl></section></div>
{week}<section><h2>Off-peak and recurring deals</h2><ul>{deals}</ul></section>{student}{promos}
<section><h2>Address and facilities</h2><p><strong>{address}</strong></p><p>{facilities}</p></section>
<section><h2>{hours_title}</h2><table><tbody>{hours}</tbody></table></section>{classes}
</main><footer><p>Independent comparison data from <a href="../../index.html">London Climbing Finder</a>. <a href="{website}" rel="noopener">Visit {name}'s official website</a>.</p></footer>{week_js}</body></html>
""".format(
        name=esc(gym["name"]), logo=logo_tile(gym), description=esc(description), page_url=page_url, site=SITE,
        schema=json.dumps(schema, ensure_ascii=False).replace("</", "<\\/"), type_label=type_label(gym.get("type")),
        area=esc(gym.get("area")), zone=esc(gym.get("zone")), facilities=facilities, about=about,
        verified_label=verified_label, checked=esc(checked), prices_url=esc(gym.get("prices_url") or gym.get("website")),
        directions=esc(directions), adult_peak=money(payg.get("adult_peak")), adult_offpeak=money(payg.get("adult_offpeak")),
        conc_peak=money(payg.get("concession_peak")), conc_offpeak=money(payg.get("concession_offpeak")),
        registration=money(gym.get("registration_fee")), monthly=money(membership.get("full_monthly")),
        conc_monthly=money(membership.get("concession_monthly")), passes=passes, deals=deal_items(gym),
        student=student, promos=promo_items(data, gym), address=esc(gym.get("address") or gym.get("postcode")),
        hours=hours_rows(gym), hours_title=hours_title, classes=classes, website=esc(gym.get("website")),
        week=week, week_css=WEEK_CSS if week_script else "\n", week_js=WEEK_JS if week_script else "",
    )


def sitemap(data):
    checked = data["_meta"]["last_research_date"]
    urls = [(SITE + "/", "1.0")] + [("%s/gyms/%s/" % (SITE, slug(g)), "0.8") for g in data["gyms"]]
    rows = "\n".join("  <url><loc>%s</loc><lastmod>%s</lastmod><changefreq>weekly</changefreq><priority>%s</priority></url>" % (url, checked, priority) for url, priority in urls)
    return '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n%s\n</urlset>\n' % rows


def build_targets(data):
    targets = {os.path.join("gyms", slug(g), "index.html"): gym_page(data, g) for g in data["gyms"]}
    targets["sitemap.xml"] = sitemap(data)
    return targets


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, "gyms.json"), encoding="utf-8") as fh:
        dataset = json.load(fh)
    for rel, content in build_targets(dataset).items():
        path = os.path.join(root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(content)
    print("Generated %d gym pages and sitemap.xml." % len(dataset["gyms"]))
