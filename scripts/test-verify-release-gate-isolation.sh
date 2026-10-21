#!/bin/sh

set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/release-gate-isolation-test.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

fixture_root="$tmp_dir/repo"
sdk_dir="$tmp_dir/sdk"
build_tools_dir="$sdk_dir/build-tools/1.0.0"
dex_fixture="$tmp_dir/dex-strings.txt"
output="$tmp_dir/output.txt"

mkdir -p \
    "$fixture_root/scripts" \
    "$fixture_root/android/app/build/outputs/apk/release" \
    "$fixture_root/android/app/build/intermediates/merged_manifest/release" \
    "$build_tools_dir"
cp "$repo_root/scripts/verify-release-gate-isolation.sh" "$fixture_root/scripts/"
touch "$fixture_root/android/app/build/outputs/apk/release/app-release.apk"
printf '%s\n' '<manifest />' > \
    "$fixture_root/android/app/build/intermediates/merged_manifest/release/AndroidManifest.xml"

cat >"$build_tools_dir/aapt2" <<'EOF'
#!/bin/sh
printf '%s\n' 'fixture output'
EOF
cat >"$build_tools_dir/dexdump" <<'EOF'
#!/bin/sh
cat "$DEX_FIXTURE"
EOF
chmod +x "$build_tools_dir/aapt2" "$build_tools_dir/dexdump"

run_verifier() {
    ANDROID_HOME="$sdk_dir" DEX_FIXTURE="$dex_fixture" \
        sh "$fixture_root/scripts/verify-release-gate-isolation.sh" >"$output" 2>&1
}

assert_rejected() {
    class_descriptor=$1
    printf '%s\n' "$class_descriptor" >"$dex_fixture"

    if run_verifier; then
        echo "Expected release verifier to reject $class_descriptor" >&2
        cat "$output" >&2
        exit 1
    fi
    if ! grep -F 'Release gate isolation failed: gate class' "$output" >/dev/null; then
        echo "Release verifier failed for the wrong reason: $class_descriptor" >&2
        cat "$output" >&2
        exit 1
    fi
}

for class_descriptor in \
    'Ldev/opentvtouchpad/setup/SettingsActionPolicy;' \
    'Ldev/opentvtouchpad/setup/SettingsNavigator;' \
    'Ldev/opentvtouchpad/setup/SettingsNavigator$ActivityUnavailableException;'
do
    assert_rejected "$class_descriptor"
done

printf '%s\n' 'Ldev/opentvtouchpad/input/GestureCoordinator;' >"$dex_fixture"
run_verifier

echo "Release gate isolation verifier regression test passed"
