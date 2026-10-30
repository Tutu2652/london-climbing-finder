#!/bin/zsh
# Keeps a free Apple ID install of the app alive.
#
# Free accounts sign apps with a profile that expires after 7 days. When the
# profile is within RENEW_DAYS of expiring, this deletes it, rebuilds (Xcode
# makes a fresh 7-day profile) and reinstalls on the iPhone over Wi-Fi or USB.
# If the phone can't be reached it tries again on the next run.
# --force rebuilds and reinstalls now (e.g. after changing the app), renewing
# the profile only if it is due.
#
#   ios/tools/auto-reinstall.sh <device-udid> [--force]
#
# Meant to run from a LaunchAgent a couple of times a day; see ios/README.md.

set -uo pipefail

DEVICE=${1:?usage: auto-reinstall.sh <device-udid> [--force]}
FORCE=${2:-}
RENEW_DAYS=3
IOS_DIR=${0:A:h:h}
BUNDLE_ID=$(xcodebuild -project "$IOS_DIR/ClimbingFinder.xcodeproj" -target ClimbingFinder -showBuildSettings 2>/dev/null | awk -F" = " '$1 ~ /^ *PRODUCT_BUNDLE_IDENTIFIER$/ {print $2; exit}')
[[ -n $BUNDLE_ID ]] || { print -u2 "Could not read the bundle ID; is ios/Signing.local.xcconfig set up?"; exit 1 }
PROFILES="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"
STATE="$HOME/Library/Application Support/ClimbFinderReinstall"
mkdir -p "$STATE"

log() { print -r -- "$(date '+%F %T') $*"; }

# The newest profile for this app: "<uuid> <expiry epoch> <path>".
current_profile() {
  local f plist best="" best_exp=0 exp
  for f in "$PROFILES"/*.mobileprovision(N); do
    plist=$(security cms -D -i "$f" 2>/dev/null) || continue
    [[ $(plutil -extract Entitlements.application-identifier raw - <<<"$plist" 2>/dev/null) == *".$BUNDLE_ID" ]] || continue
    exp=$(date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$(plutil -extract ExpirationDate raw - <<<"$plist")" +%s)
    if (( exp > best_exp )); then
      best_exp=$exp
      best="$(plutil -extract UUID raw - <<<"$plist") $exp $f"
    fi
  done
  print -r -- "$best"
}

profile=$(current_profile)
uuid=${${(s: :)profile}[1]:-}
expires=${${(s: :)profile}[2]:-0}
renew_at=$(( $(date +%s) + RENEW_DAYS * 86400 ))

if [[ -n $uuid && $FORCE != --force && $expires -gt $renew_at && $(cat "$STATE/installed-profile" 2>/dev/null) == "$uuid" ]]; then
  log "OK: installed profile valid until $(date -r "$expires" '+%F %H:%M'); nothing to do."
  exit 0
fi

if [[ -n $uuid && $expires -le $renew_at ]]; then
  log "Profile expires $(date -r "$expires" '+%F %H:%M'); removing it so Xcode issues a new one."
  rm -f "${profile#* * }"
fi

log "Building $BUNDLE_ID…"
if ! xcodebuild -project "$IOS_DIR/ClimbingFinder.xcodeproj" -scheme ClimbingFinder \
     -destination 'generic/platform=iOS' -derivedDataPath "$STATE/build" \
     -allowProvisioningUpdates build >"$STATE/last-build.log" 2>&1; then
  log "FAILED: build (see $STATE/last-build.log)."
  exit 1
fi
uuid=${${(s: :)$(current_profile)}[1]:-}

if ! xcrun devicectl device info details --device "$DEVICE" --timeout 30 >/dev/null 2>&1; then
  log "iPhone not reachable (needs to be on the same Wi-Fi and unlocked recently); will retry next run."
  exit 2
fi

log "Installing on the iPhone…"
if xcrun devicectl device install app --device "$DEVICE" --timeout 300 \
     "$STATE/build/Build/Products/Debug-iphoneos/ClimbingFinder.app" >"$STATE/last-install.log" 2>&1; then
  print -r -- "$uuid" >"$STATE/installed-profile"
  new_exp=${${(s: :)$(current_profile)}[2]:-0}
  log "Installed. Good until $(date -r "$new_exp" '+%F %H:%M')."
else
  log "FAILED: install (see $STATE/last-install.log); will retry next run."
  exit 3
fi
