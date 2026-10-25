#!/bin/sh

set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/release-gate-isolation-test.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

fixture_root="$tmp_dir/repo"
sdk_dir="$tmp_dir/sdk"
build_tools_dir="$sdk_dir/build-tools/1.0.0"
dex_fixture="$tmp_dir/dex-strings.txt"
aapt_fixture="$tmp_dir/aapt-output.txt"
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
cat "$AAPT_FIXTURE"
EOF
cat >"$build_tools_dir/dexdump" <<'EOF'
#!/bin/sh
cat "$DEX_FIXTURE"
EOF
chmod +x "$build_tools_dir/aapt2" "$build_tools_dir/dexdump"

run_verifier() {
    ANDROID_HOME="$sdk_dir" DEX_FIXTURE="$dex_fixture" AAPT_FIXTURE="$aapt_fixture" \
        sh "$fixture_root/scripts/verify-release-gate-isolation.sh" >"$output" 2>&1
}

assert_class_rejected() {
    class_descriptor=$1
    printf '%s\n' 'fixture output' >"$aapt_fixture"
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
    echo "Rejected class mutation: $class_descriptor"
}

assert_resource_rejected() {
    resource_name=$1
    printf '%s\n' 'Ldev/opentvtouchpad/input/GestureCoordinator;' >"$dex_fixture"
    printf '%s\n' "$resource_name" >"$aapt_fixture"

    if run_verifier; then
        echo "Expected release verifier to reject $resource_name" >&2
        cat "$output" >&2
        exit 1
    fi
    if ! grep -F 'Release gate isolation failed: gate resource' "$output" >/dev/null; then
        echo "Release verifier failed for the wrong reason: $resource_name" >&2
        cat "$output" >&2
        exit 1
    fi
    echo "Rejected resource mutation: $resource_name"
}

assert_resource_accepted() {
    resource_name=$1
    printf '%s\n' 'Ldev/opentvtouchpad/input/GestureCoordinator;' >"$dex_fixture"
    printf '%s\n' "$resource_name" >"$aapt_fixture"

    if ! run_verifier; then
        echo "Expected release verifier to accept $resource_name" >&2
        cat "$output" >&2
        exit 1
    fi
    echo "Accepted safe resource: $resource_name"
}

assert_manifest_rejected() {
    activity_name=$1
    printf '%s\n' "<activity android:name=\"$activity_name\" />" > \
        "$fixture_root/android/app/build/intermediates/merged_manifest/release/AndroidManifest.xml"
    printf '%s\n' 'Ldev/opentvtouchpad/input/GestureCoordinator;' >"$dex_fixture"
    printf '%s\n' 'fixture output' >"$aapt_fixture"

    if run_verifier; then
        echo "Expected release verifier to reject $activity_name" >&2
        cat "$output" >&2
        exit 1
    fi
    if ! grep -F 'Release gate isolation failed: GateActivity declaration' "$output" >/dev/null; then
        echo "Release verifier failed for the wrong reason: $activity_name" >&2
        cat "$output" >&2
        exit 1
    fi
    echo "Rejected manifest mutation: $activity_name"
    printf '%s\n' '<manifest />' > \
        "$fixture_root/android/app/build/intermediates/merged_manifest/release/AndroidManifest.xml"
}

assert_manifest_rejected 'dev.opentvtouchpad.setup.GateActivity'
assert_manifest_rejected 'dev.opentvtouchpad.setup.CalibrationActivity'

for class_descriptor in \
    'Ldev/opentvtouchpad/setup/SettingsActionPolicy;' \
    'Ldev/opentvtouchpad/setup/SettingsNavigator;' \
    'Ldev/opentvtouchpad/setup/SettingsNavigator$ActivityUnavailableException;' \
    'Ldev/opentvtouchpad/setup/TapProbePolicy;' \
    'Ldev/opentvtouchpad/accessibility/ControllerHandoffMachine;' \
    'Ldev/opentvtouchpad/setup/CalibrationActivity;' \
    'Ldev/opentvtouchpad/accessibility/AndroidControllerHandoffExecutor;' \
    'Ldev/opentvtouchpad/accessibility/AndroidFrameScheduler;' \
    'Ldev/opentvtouchpad/accessibility/AndroidMonotonicDeadlineScheduler;' \
    'Ldev/opentvtouchpad/accessibility/AndroidMainLoopScheduler;' \
    'Ldev/opentvtouchpad/setup/ManualTargetResult;' \
    'Ldev/opentvtouchpad/setup/ProbeCallbackOutcome;' \
    'Ldev/opentvtouchpad/setup/ProbeControllerState;'
do
    assert_class_rejected "$class_descriptor"
done

for resource_name in \
    'layout/activity_calibration' \
    'layout/activity_gate' \
    'layout/controller_overlay' \
    'string/m0_1_manual_activated' \
    'string/probe_mode_value' \
    'id/calibration_target_center' \
    'id/probe_mode_controller_handoff' \
    'id/manual_not_activated' \
    'id/launch_calibration'
do
    assert_resource_rejected "$resource_name"
done

for resource_name in \
    'id/launch_calibration_extra' \
    'id/launch_calibrations' \
    'id/launch_calibration2'
do
    assert_resource_accepted "$resource_name"
done

printf '%s\n' 'Ldev/opentvtouchpad/input/GestureCoordinator;' >"$dex_fixture"
printf '%s\n' 'fixture output' >"$aapt_fixture"
run_verifier

echo "Release gate isolation verifier regression test passed"
