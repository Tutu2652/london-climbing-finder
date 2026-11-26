"""Gyms people add in the phone apps stay on the phone, with ids starting "added-".

These checks keep gyms.json from ever using such an id, and keep the iPhone and Android
apps' saved format (also used in logbook backups) the same.
"""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SWIFT = ROOT / "ios/ClimbingFinder/Core/AddedGyms.swift"
KOTLIN = ROOT / "android/core/src/main/kotlin/io/github/tutu2652/climbingfinder/core/AddedGym.kt"


class AddedGymsTest(unittest.TestCase):
    def test_no_listed_gym_uses_the_added_prefix(self):
        gyms = json.loads((ROOT / "gyms.json").read_text())["gyms"]
        clashes = [g["id"] for g in gyms if g["id"].startswith("added-")]
        self.assertEqual(clashes, [], "ids starting with added- are kept for gyms people add on their phones")

    def test_both_apps_use_the_same_prefix_and_brand(self):
        swift, kotlin = SWIFT.read_text(), KOTLIN.read_text()
        self.assertIn('static let idPrefix = "added-"', swift)
        self.assertIn('const val ID_PREFIX = "added-"', kotlin)
        self.assertIn('static let brand = "Added by you"', swift)
        self.assertIn('const val BRAND = "Added by you"', kotlin)

    def test_both_apps_save_the_same_fields(self):
        swift_keys = re.search(r"enum CodingKeys: String, CodingKey \{\s*case ([^}]*)\}", SWIFT.read_text()).group(1)
        swift_fields = {k.strip() for k in swift_keys.split(",")}
        body = re.search(r"data class AddedGym\((.*?)\n\) \{", KOTLIN.read_text(), re.S).group(1)
        kotlin_fields = set(re.findall(r"^\s*val (\w+):", body, re.M))
        self.assertEqual(swift_fields, kotlin_fields)


if __name__ == "__main__":
    unittest.main()
