#!/bin/sh

set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
verifier="$repo_root/scripts/verify-m0-1-report.sh"
fixtures="$repo_root/scripts/testdata"
checklist="$repo_root/docs/gates/m0-1-checklist.md"
tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/m0-1-verifier-test.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

output="$tmp_dir/output.txt"

assert_accepted() {
    fixture=$1

    if ! sh "$verifier" "$fixtures/$fixture" >"$output" 2>&1; then
        echo "Expected $fixture to be accepted" >&2
        cat "$output" >&2
        exit 1
    fi
}

assert_rejected() {
    fixture=$1
    reason=$2

    if sh "$verifier" "$fixtures/$fixture" >"$output" 2>&1; then
        echo "Expected $fixture to be rejected" >&2
        cat "$output" >&2
        exit 1
    fi
    if ! grep -F "$reason" "$output" >/dev/null; then
        echo "Expected $fixture rejection to contain: $reason" >&2
        cat "$output" >&2
        exit 1
    fi
}

assert_mutation_rejected() {
    name=$1
    reason=$2
    mutated="$tmp_dir/$name.md"

    if sh "$verifier" "$mutated" >"$output" 2>&1; then
        echo "Expected parser mutation $name to be rejected" >&2
        cat "$output" >&2
        exit 1
    fi
    if ! grep -F "$reason" "$output" >/dev/null; then
        echo "Expected parser mutation $name rejection to contain: $reason" >&2
        cat "$output" >&2
        exit 1
    fi
}

assert_generated_accepted() {
    name=$1
    generated="$tmp_dir/$name.md"

    if ! sh "$verifier" "$generated" >"$output" 2>&1; then
        echo "Expected generated report $name to be accepted" >&2
        cat "$output" >&2
        exit 1
    fi
}

assert_checklist_contract() {
    if grep -Ei '\|[^|]*(notes?|free[- ]?text)[^|]*\|' "$checklist" >/dev/null; then
        echo "Expected M0.1 checklist to contain no unrestricted notes fields" >&2
        exit 1
    fi

    if ! grep -F '| Order | Mode | Attempt | Result | Admission | Callback | Manual target |' "$checklist" >/dev/null; then
        echo "Expected M0.1 checklist to use closed per-attempt evidence fields" >&2
        exit 1
    fi

    for prohibited_evidence in \
        'raw logs' \
        'screenshots' \
        'exact coordinates' \
        'key traces' \
        'profile names or screen content' \
        'package inventories' \
        'raw ADB output' \
        'raw protocol output'
    do
        if ! grep -Fi "$prohibited_evidence" "$checklist" >/dev/null; then
            echo "Expected M0.1 checklist to forbid $prohibited_evidence" >&2
            exit 1
        fi
    done
}

for fixture in \
    m0-1-proceed.md \
    m0-1-stop-exhausted.md \
    m0-1-stop-safety.md \
    m0-1-stop-calibration-safety.md
do
    assert_accepted "$fixture"
done

assert_rejected m0-1-mixed-artifact.md \
    "Commit and APK-SHA256 must identify the same M0.1 artifact"
assert_rejected m0-1-out-of-order.md \
    "probe attempts must follow BASELINE_80_MS, SYSTEM_TAP_TIMEOUT, CONTROLLER_HANDOFF order"
assert_rejected m0-1-third-attempt.md \
    "unknown field Baseline-80-MS-Attempt-3"
assert_rejected m0-1-failed-confirmation.md \
    "winning mode requires two COMPLETED_ACTIVATED attempts"
assert_rejected m0-1-incomplete-safety.md \
    "unresolved placeholder in No-ADB-Input"
assert_rejected m0-1-missing-identity.md \
    "missing field Original-M0-APK-SHA256"
assert_rejected m0-1-mixed-winner.md \
    "later modes must be NOT_RUN after the winning mode"
assert_rejected m0-1-cumulative-investigation.md \
    "M0.1-Investigation-Count must be 1"

cp "$fixtures/m0-1-proceed.md" "$tmp_dir/unknown-field.md"
printf '%s\n' 'Unexpected-Field: value' >>"$tmp_dir/unknown-field.md"

cp "$fixtures/m0-1-proceed.md" "$tmp_dir/duplicate-field.md"
printf '%s\n' 'Gate: M0.1' >>"$tmp_dir/duplicate-field.md"

cp "$fixtures/m0-1-proceed.md" "$tmp_dir/colon-free-line.md"
printf '%s\n' 'Unexpected-Field = value' >>"$tmp_dir/colon-free-line.md"

cp "$fixtures/m0-1-proceed.md" "$tmp_dir/heading-line.md"
printf '%s\n' '# raw ADB output must not be accepted' >>"$tmp_dir/heading-line.md"

awk '
    /^Outcome:/ { print "Outcome = PROCEED_TO_M0_RETEST"; next }
    { print }
' "$fixtures/m0-1-proceed.md" >"$tmp_dir/malformed-field.md"

awk '
    /^Firmware:/ { print "Firmware:"; next }
    { print }
' "$fixtures/m0-1-proceed.md" >"$tmp_dir/empty-value.md"

awk '
    /^Firmware:/ { print "Firmware: PENDING"; next }
    { print }
' "$fixtures/m0-1-proceed.md" >"$tmp_dir/placeholder.md"

awk '
    /^Commit:/ { print "Commit: 6223C19E4900C8086BA549E9FA326FD41D156C11"; next }
    /^APK-SHA256:/ { print "APK-SHA256: F3CC262C74645800515DA01D28E6E175833B29761188A88C5A4E15AF8BD2535A"; next }
    { print }
' "$fixtures/m0-1-proceed.md" >"$tmp_dir/uppercase-original-identity.md"

awk '
    /^Outcome:/ { print "Outcome: STOP"; next }
    /^Baseline-80-MS-Attempt-1:/ { print "Baseline-80-MS-Attempt-1: COMPLETED_NOT_ACTIVATED"; next }
    /^Baseline-80-MS-Attempt-2:/ { print "Baseline-80-MS-Attempt-2: NOT_RUN"; next }
    /^Winning-Mode:/ { print "Winning-Mode: NONE"; next }
    /^Key-Leakage:/ { print "Key-Leakage: FAIL"; next }
    /^Stop-Reason:/ { print "Stop-Reason: KEY_LEAKAGE"; next }
    { print }
' "$fixtures/m0-1-proceed.md" >"$tmp_dir/key-leakage-stop.md"

assert_mutation_rejected unknown-field "unknown field Unexpected-Field"
assert_mutation_rejected duplicate-field "duplicate field Gate"
assert_mutation_rejected colon-free-line "unexpected colon-free line Unexpected-Field = value"
assert_mutation_rejected heading-line "unexpected heading/comment line # raw ADB output must not be accepted"
assert_mutation_rejected malformed-field "malformed field Outcome"
assert_mutation_rejected empty-value "empty value for Firmware"
assert_mutation_rejected placeholder "unresolved placeholder in Firmware"
assert_mutation_rejected uppercase-original-identity \
    "M0.1 artifact identity must differ from the immutable M0 artifact"
assert_generated_accepted key-leakage-stop
assert_checklist_contract

echo "M0.1 verifier regression test passed: 4 accepted fixtures, 8 rejected fixtures, 8 rejected parser mutations, closed checklist contract"
