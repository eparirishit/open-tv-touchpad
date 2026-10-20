#!/bin/sh

set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
release_dir="$repo_root/android/app/build/outputs/apk/release"
merged_manifest_dir="$repo_root/android/app/build/intermediates/merged_manifest/release"

set -- "$release_dir"/*.apk
if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
    echo "Expected exactly one release APK in $release_dir" >&2
    exit 1
fi
release_apk=$1

merged_manifest=$(find "$merged_manifest_dir" -type f -name AndroidManifest.xml -print | sort | head -n 1)
if [ -z "$merged_manifest" ] || [ ! -f "$merged_manifest" ]; then
    echo "Release merged manifest not found under $merged_manifest_dir" >&2
    exit 1
fi

sdk_dir=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$sdk_dir" ] && [ -f "$repo_root/local.properties" ]; then
    sdk_dir=$(sed -n 's/^sdk\.dir=//p' "$repo_root/local.properties" | head -n 1)
fi
if [ -z "$sdk_dir" ]; then
    echo "ANDROID_HOME, ANDROID_SDK_ROOT, or local.properties sdk.dir is required" >&2
    exit 1
fi

build_tools_dir=$(find "$sdk_dir/build-tools" -mindepth 1 -maxdepth 1 -type d -print | sort | tail -n 1)
aapt2="$build_tools_dir/aapt2"
dexdump="$build_tools_dir/dexdump"
if [ ! -x "$aapt2" ] || [ ! -x "$dexdump" ]; then
    echo "aapt2 and dexdump are required in $build_tools_dir" >&2
    exit 1
fi

assert_absent() {
    label=$1
    pattern=$2
    file=$3
    if grep -E "$pattern" "$file" >/dev/null; then
        echo "Release gate isolation failed: $label found in $file" >&2
        grep -E "$pattern" "$file" >&2
        exit 1
    fi
}

tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/open-tv-touchpad-release-isolation.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

apk_manifest="$tmp_dir/apk-manifest.txt"
dex_strings="$tmp_dir/dex-strings.txt"
resources="$tmp_dir/resources.txt"

"$aapt2" dump xmltree "$release_apk" --file AndroidManifest.xml >"$apk_manifest"
"$dexdump" -s "$release_apk" >"$dex_strings"
"$aapt2" dump resources "$release_apk" >"$resources"

manifest_pattern='dev\.opentvtouchpad\.setup\.GateActivity'
class_pattern='Ldev/opentvtouchpad/setup/Gate|Ldev/opentvtouchpad/setup/ServiceGateRuntime;|Ldev/opentvtouchpad/accessibility/DpadControllerOverlay'
resource_pattern='layout/(activity_gate|controller_overlay)|string/gate_'

assert_absent "GateActivity declaration" "$manifest_pattern" "$merged_manifest"
assert_absent "GateActivity declaration" "$manifest_pattern" "$apk_manifest"
assert_absent "gate class" "$class_pattern" "$dex_strings"
assert_absent "gate resource" "$resource_pattern" "$resources"

echo "Release gate isolation verified: no M0 gate manifest, classes, layouts, or strings"
