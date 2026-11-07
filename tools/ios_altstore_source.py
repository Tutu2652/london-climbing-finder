#!/usr/bin/env python3
"""Write the SideStore/AltStore source for an iPhone release.

The source is a JSON file that SideStore (and AltStore) reads to install the app, renew
it every week and offer new versions. .github/workflows/ios-release.yml runs this after
building the unsigned app and attaches the result to the GitHub release, so the fixed
link .../releases/latest/download/altstore-source.json always describes the newest IPA.

AltStore refuses an app whose bundle ID, version or permissions differ from the source,
so all of those are read from the built app rather than typed in here.

    python3 tools/ios_altstore_source.py --app ClimbingFinder.app \
        --ipa london-climbing-finder.ipa --repo owner/name --tag v0.4 --out source.json
"""

import argparse
import json
import plistlib
import sys
from datetime import date
from pathlib import Path

DESCRIPTION = (
    "Compare pay-as-you-go prices, deals, memberships and travel times across London "
    "climbing gyms, and keep a logbook of your sessions and climbs. Prices update from "
    "the website when you open the app."
)


def read_plist(path: Path) -> dict:
    with path.open("rb") as f:
        return plistlib.load(f)


def privacy_strings(app: Path, info: dict) -> dict:
    """Every NS…UsageDescription in the app and its extensions, as AltStore expects."""
    found = {k: v for k, v in info.items() if k.startswith("NS") and k.endswith("UsageDescription")}
    for plist in sorted(app.glob("PlugIns/*.appex/Info.plist")):
        for k, v in read_plist(plist).items():
            if k.startswith("NS") and k.endswith("UsageDescription"):
                found.setdefault(k, v)
    return dict(sorted(found.items()))


def build_source(app: Path, ipa: Path, repo: str, tag: str, today: str) -> dict:
    info = read_plist(app / "Info.plist")
    owner = repo.split("/")[0]
    raw = f"https://raw.githubusercontent.com/{repo}/{tag}"
    icon = f"{raw}/ios/ClimbingFinder/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
    download = f"https://github.com/{repo}/releases/download/{tag}/{ipa.name}"
    notes = f"Version {info['CFBundleShortVersionString']}. What's new: https://github.com/{repo}/releases/tag/{tag}"
    version = {
        "version": info["CFBundleShortVersionString"],
        "buildVersion": info["CFBundleVersion"],
        "date": today,
        "localizedDescription": notes,
        "downloadURL": download,
        "size": ipa.stat().st_size,
        "minOSVersion": info.get("MinimumOSVersion", "17.0"),
    }
    name = info.get("CFBundleDisplayName") or info.get("CFBundleName") or "Climbing Finder"
    return {
        "name": "London Climbing Finder",
        "identifier": f"{info['CFBundleIdentifier']}.source",
        "subtitle": "Cheapest climbing in London",
        "website": f"https://github.com/{repo}",
        "iconURL": icon,
        "nsfw": False,
        "apps": [
            {
                "name": name,
                "bundleIdentifier": info["CFBundleIdentifier"],
                "developerName": owner,
                "subtitle": "London climbing gym prices and a logbook",
                "localizedDescription": DESCRIPTION,
                "iconURL": icon,
                "category": "lifestyle",
                "versions": [version],
                # The IPA is unsigned, so it carries no entitlements of its own; the
                # signer adds only the standard ones, which AltStore doesn't list.
                "appPermissions": {"entitlements": [], "privacy": privacy_strings(app, info)},
                # Older SideStore/AltStore releases read these instead of "versions".
                "version": version["version"],
                "versionDate": today,
                "versionDescription": notes,
                "downloadURL": download,
                "size": version["size"],
            }
        ],
        "news": [],
    }


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--app", type=Path, required=True, help="the built ClimbingFinder.app")
    p.add_argument("--ipa", type=Path, required=True, help="the packaged IPA")
    p.add_argument("--repo", required=True, help="owner/name on GitHub")
    p.add_argument("--tag", required=True, help="release tag, e.g. v0.4")
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--date", default=date.today().isoformat())
    args = p.parse_args(argv)
    source = build_source(args.app, args.ipa, args.repo, args.tag, args.date)
    args.out.write_text(json.dumps(source, indent=2, ensure_ascii=False) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
