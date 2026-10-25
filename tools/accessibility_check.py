#!/usr/bin/env python3
"""Browser smoke tests for accessible names, tabs, and the mobile card layout."""

import contextlib
import functools
import http.server
import os
import threading

from playwright.sync_api import sync_playwright


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AXE_PATH = os.path.join(ROOT, "node_modules", "axe-core", "axe.min.js")


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


@contextlib.contextmanager
def local_site():
    handler = functools.partial(QuietHandler, directory=ROOT)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield "http://127.0.0.1:%d" % server.server_port
    finally:
        server.shutdown()
        thread.join(timeout=5)


AUDIT_JS = """
() => {
  const problems = [];
  const visibleText = el => (el.innerText || el.textContent || '').trim();
  const labelled = el => {
    if (el.getAttribute('aria-label')) return true;
    const by = el.getAttribute('aria-labelledby');
    if (by && by.split(/\\s+/).every(id => document.getElementById(id))) return true;
    if (el.id && document.querySelector(`label[for="${CSS.escape(el.id)}"]`)) return true;
    return !!el.closest('label');
  };
  if (!document.documentElement.lang) problems.push('html is missing lang');
  if (!document.title.trim()) problems.push('page is missing a title');
  if (document.querySelectorAll('h1').length !== 1) problems.push('page must have exactly one h1');
  const ids = [...document.querySelectorAll('[id]')].map(el => el.id);
  ids.filter((id, i) => ids.indexOf(id) !== i).forEach(id => problems.push(`duplicate id: ${id}`));
  document.querySelectorAll('input,select,textarea').forEach(el => {
    if (!labelled(el)) problems.push(`unlabelled control: ${el.tagName.toLowerCase()}#${el.id || '?'}`);
  });
  document.querySelectorAll('button').forEach(el => {
    if (!visibleText(el) && !el.getAttribute('aria-label')) problems.push('button has no accessible name');
  });
  document.querySelectorAll('a[href]').forEach(el => {
    if (!visibleText(el) && !el.getAttribute('aria-label')) problems.push(`link has no accessible name: ${el.href}`);
  });
  document.querySelectorAll('[role="tab"]').forEach(el => {
    if (!el.hasAttribute('aria-selected') || !el.getAttribute('aria-controls')) problems.push(`incomplete tab: ${visibleText(el)}`);
  });
  return [...new Set(problems)];
}
"""


def audit_page(page, url):
    page.goto(url, wait_until="domcontentloaded")
    page.wait_for_timeout(300)
    problems = page.evaluate(AUDIT_JS)
    if problems:
        raise AssertionError("%s\n  %s" % (url, "\n  ".join(problems)))
    page.add_script_tag(path=AXE_PATH)
    violations = page.evaluate("""async () => (await axe.run(document, {
      runOnly: {type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']}
    })).violations.filter(v => ['serious', 'critical'].includes(v.impact)).map(v => ({
      id: v.id, impact: v.impact, help: v.help, targets: v.nodes.map(n => n.target.join(' '))
    }))""")
    if violations:
        lines = ["%s (%s): %s [%s]" % (
            item["id"], item["impact"], item["help"], ", ".join(item["targets"][:5]))
            for item in violations]
        raise AssertionError("axe WCAG failures on %s\n  %s" % (url, "\n  ".join(lines)))


def main():
    if not os.path.exists(AXE_PATH):
        raise SystemExit("axe-core is missing; run `npm ci` first")
    with local_site() as site, sync_playwright() as runtime:
        browser = runtime.chromium.launch(headless=True)
        try:
            desktop = browser.new_page(viewport={"width": 1280, "height": 900})
            audit_page(desktop, site + "/")
            tabs = desktop.locator('[role="tab"]')
            assert tabs.count() == 9, "expected nine keyboard tabs"
            tabs.first.focus()
            desktop.keyboard.press("End")
            assert tabs.last.get_attribute("aria-selected") == "true", "End key did not select last tab"

            desktop.goto(site + "/index.html#all", wait_until="domcontentloaded")
            view_width = desktop.locator("#view").evaluate("el => el.getBoundingClientRect().width")
            assert view_width > 1000, "All Gyms desktop view did not expand beyond the reading width"

            mobile = browser.new_page(viewport={"width": 390, "height": 844})
            audit_page(mobile, site + "/index.html#all")
            first_row = mobile.locator("#view .responsive-table tbody tr:not(.section-row)").first
            assert first_row.count(), "mobile results table was not rendered"
            assert first_row.evaluate("el => getComputedStyle(el).display") == "block", "mobile table row did not become a card"
            assert first_row.locator("td[data-label]").count(), "mobile card fields have no visible labels"
            secondary = first_row.locator('td[data-mobile="secondary"]').first
            assert secondary.evaluate("el => getComputedStyle(el).display") == "none", "compact card exposed secondary fields"
            expand = first_row.locator("button.mobile-expand")
            assert expand.count() == 1, "compact card has no details toggle"
            expand.click()
            assert expand.get_attribute("aria-expanded") == "true", "details toggle did not update its state"
            assert secondary.evaluate("el => getComputedStyle(el).display") == "grid", "expanded card did not reveal details"

            mobile.goto(site + "/index.html?layout-check=1#cheapest", wait_until="domcontentloaded")
            compact_layout = mobile.locator("#view tbody tr:not(.section-row) td[data-label=\"Price\"]").first.evaluate("""el => {
              const price=el.querySelector('.price')?.getBoundingClientRect();
              const pill=el.querySelector('.pill')?.getBoundingClientRect();
              const tabs=document.querySelector('.tabs')?.getBoundingClientRect();
              const date=document.querySelector('#date')?.getBoundingClientRect();
              const time=document.querySelector('#time')?.getBoundingClientRect();
              return {priceTop:price?.top,pillTop:pill?.top,tabsHeight:tabs?.height,dateTop:date?.top,timeTop:time?.top};
            }""")
            assert abs(compact_layout["priceTop"] - compact_layout["pillTop"]) < 6, "price and rate pill did not stay on one line"
            assert compact_layout["tabsHeight"] < 48, "mobile tabs wrapped onto multiple rows"
            assert abs(compact_layout["dateTop"] - compact_layout["timeTop"]) < 3, "date and time did not share a compact row"

            nav = browser.new_page(viewport={"width": 1280, "height": 900})
            nav.goto(site + "/index.html#all", wait_until="domcontentloaded")
            gym_link = nav.get_by_role("link", name="EustonWall (LCC)", exact=True)
            assert gym_link.count() == 1, "expected one named EustonWall link"
            gym_link.click()
            nav.wait_for_url("**/gyms/lcc-eustonwall/index.html")
            assert nav.locator("h1").inner_text() == "EustonWall (LCC)", "one click did not open the gym page"
            nav.close()

            pages = []
            for dirpath, _dirs, files in os.walk(os.path.join(ROOT, "gyms")):
                if "index.html" in files:
                    pages.append(os.path.relpath(dirpath, ROOT).replace(os.sep, "/"))
            assert len(pages) == 27, "expected 27 generated gym pages, got %d" % len(pages)
            for rel in sorted(pages):
                audit_page(desktop, "%s/%s/" % (site, rel))
        finally:
            browser.close()
    print("Accessibility smoke checks passed for the finder and 27 gym pages.")


if __name__ == "__main__":
    main()
