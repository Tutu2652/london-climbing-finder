# Auto-updating London climbing price site — one-time setup

This repo is a ready-to-publish website (Cloudflare Pages) + a weekly price-checker
(GitHub Actions).

- `gyms.json` — all the gym data. **The single source of truth: edit only this.**
- `tools/sync_data.py` — generates `assets/data.js`, updates the offline dashboard,
  and regenerates the gym pages, sitemap and two `.ics` deal calendars. Run it after
  every data edit; CI fails if you forget.
- `index.html` — the site shell (static, no build step), with code and data split into
  `assets/styles.css`, `assets/app.js`, and generated `assets/data.js`.
- `london_climbing_prices.py` — the command-line tool + the checker used by the weekly job.
- `firebase-config.js` — config for optional "Sign in with Google" notes/ratings sync
  (see `FIREBASE-SETUP.md`); the site works without it.
- `.github/workflows/price-check.yml` — re-checks live prices every Monday and opens an
  Issue if anything changed.
- `.github/workflows/ci.yml` — on every push: pricing tests + a check that the HTML/ICS
  files are in sync with `gyms.json`.

## How updates flow (once set up)

1. Every Monday, GitHub runs the price checker (on GitHub's servers, which have full internet).
2. If a gym's published price no longer matches `gyms.json`, it **opens a GitHub Issue**
   listing the changes (you get an email/notification).
3. You edit `gyms.json`, run `python3 tools/sync_data.py`, and commit.
4. Cloudflare Pages sees the push and **auto-deploys** — the live site updates within a minute.

## One-time setup (about 10 minutes)

### 1. Put this folder on GitHub

```bash
cd this-folder
git init && git add . && git commit -m "Initial site"
git branch -M main
git remote add origin https://github.com/<you>/london-climbing-finder.git
git push -u origin main
```

(Or use github.com/new and drag the files in — include the `.github` folder so the
workflows keep their paths.)

### 2. Point Cloudflare Pages at the repo

1. Cloudflare dashboard → **Workers & Pages → Create → Pages → Connect to Git**, pick the repo.
2. Build command: leave blank. Build output directory: `/` (it's a static site, no build step).
3. Deploy. The site appears at `london-climbing-finder.pages.dev` (the project name sets
   the subdomain). If you use a different name or a custom domain, update the canonical /
   og:url / sitemap / robots URLs to match.

From now on, every push auto-deploys.

### 3. Turn on the weekly checker

- Both Actions run automatically once the repo exists. Nothing to configure — they use the
  built-in `GITHUB_TOKEN`.
- To test it now: repo → **Actions** tab → "Weekly price check" → **Run workflow**. Check
  the run summary for the report; if it finds changes it opens an Issue.

## Notes

- The Monday Action only reads text price pages (most gyms). Gyms whose prices are images
  (e.g. Yonder) or JavaScript booking widgets are skipped by the text check — verify those
  by clicking through to the gym's price page now and then.
- Editing prices: open `gyms.json`, change the number under `payg` (or `membership` /
  `passes`), run `python3 tools/sync_data.py`, commit both. Keep it valid JSON.
