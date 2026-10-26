#!/usr/bin/env python3
"""Generate crawlable, no-JavaScript detail pages for every gym."""

import html
import json
import os
import re
import urllib.parse


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
@media(max-width:480px){{dl{{grid-template-columns:1fr}}dd{{margin-bottom:7px}}.title{{gap:10px}}.logo{{flex-basis:44px;width:44px;height:44px;border-radius:10px}}}}
</style></head><body><a class="skip" href="#main">Skip to gym information</a>
<header><nav class="crumb" aria-label="Breadcrumb"><a href="../../index.html">London Climbing Finder</a> / {name}</nav>
<div class="title">{logo}<h1>{name}</h1></div>{about}<p class="summary">{type_label} in {area}, zone {zone}. {facilities}</p>
<p class="muted">{verified_label} · data checked {checked}. Prices can change—confirm before travelling.</p></header>
<main id="main" tabindex="-1"><div class="actions"><a class="button" href="{prices_url}" rel="noopener">Check official prices</a><a class="button alt" href="{directions}" rel="noopener">Public-transport directions</a><a class="button alt" href="../../index.html#all">Compare all gyms</a></div>
<div class="grid"><section><h2>Day-pass prices</h2><dl><dt>Adult peak</dt><dd>{adult_peak}</dd><dt>Adult off-peak</dt><dd>{adult_offpeak}</dd><dt>Concession peak</dt><dd>{conc_peak}</dd><dt>Concession off-peak</dt><dd>{conc_offpeak}</dd><dt>Registration</dt><dd>{registration}</dd></dl></section>
<section><h2>Membership and passes</h2><dl><dt>Adult monthly</dt><dd>{monthly}</dd><dt>Concession monthly</dt><dd>{conc_monthly}</dd><dt>Multi-visit</dt><dd>{passes}</dd></dl></section></div>
<section><h2>Off-peak and recurring deals</h2><ul>{deals}</ul></section>{student}{promos}
<section><h2>Address and facilities</h2><p><strong>{address}</strong></p><p>{facilities}</p></section>
<section><h2>{hours_title}</h2><table><tbody>{hours}</tbody></table></section>{classes}
</main><footer><p>Independent comparison data from <a href="../../index.html">London Climbing Finder</a>. <a href="{website}" rel="noopener">Visit {name}'s official website</a>.</p></footer></body></html>
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
