#!/bin/sh

if [ "$#" -ne 1 ]; then
    echo "usage: $0 <report>" >&2
    exit 2
fi

report=$1

if [ ! -f "$report" ] || [ ! -r "$report" ]; then
    echo "report is not a readable file: $report" >&2
    exit 1
fi

LC_ALL=C awk '
function trim(text) {
    sub(/^[[:space:]]+/, "", text)
    sub(/[[:space:]]+$/, "", text)
    return text
}

function reject(message) {
    print "invalid M0.1 report: " message
    invalid = 1
}

function reject_order() {
    if (!order_rejected) {
        reject("probe attempts must follow BASELINE_80_MS, SYSTEM_TAP_TIMEOUT, CONTROLLER_HANDOFF order")
        order_rejected = 1
    }
}

function is_attempt(value) {
    return value == "NOT_RUN" ||
        value == "REJECTED" ||
        value == "CANCELLED" ||
        value == "TIMEOUT" ||
        value == "HANDOFF_FRAME_TIMEOUT" ||
        value == "COMPLETED_NOT_ACTIVATED" ||
        value == "COMPLETED_ACTIVATED"
}

function is_non_safety_failure(value) {
    return value == "REJECTED" ||
        value == "CANCELLED" ||
        value == "TIMEOUT" ||
        value == "COMPLETED_NOT_ACTIVATED"
}

function mode_failed(mode_number, first, second) {
    first = values[attempt_one[mode_number]]
    second = values[attempt_two[mode_number]]
    return (is_non_safety_failure(first) && second == "NOT_RUN") ||
        (first == "COMPLETED_ACTIVATED" && is_non_safety_failure(second))
}

function mode_not_run(mode_number) {
    return values[attempt_one[mode_number]] == "NOT_RUN" &&
        values[attempt_two[mode_number]] == "NOT_RUN"
}

function calibration_not_run() {
    return values[calibration[1]] == "NOT_RUN" &&
        values[calibration[2]] == "NOT_RUN" &&
        values[calibration[3]] == "NOT_RUN" &&
        values["Calibration-Classification"] == "NOT_RUN"
}

function safety_attestations_pass() {
    return values["Key-Leakage"] == "PASS" &&
        values["Controller-Lifecycle"] == "PASS" &&
        values["No-ADB-Input"] == "PASS" &&
        values["Evidence-Integrity"] == "PASS"
}

function safety_sequence_valid(mode_number, last_mode, first, second) {
    last_mode = 0
    for (mode_number = 1; mode_number <= 3; mode_number++) {
        if (!mode_not_run(mode_number)) {
            last_mode = mode_number
        }
    }
    if (last_mode == 0) {
        return 0
    }

    for (mode_number = 1; mode_number < last_mode; mode_number++) {
        if (!mode_failed(mode_number)) {
            return 0
        }
    }

    first = values[attempt_one[last_mode]]
    second = values[attempt_two[last_mode]]
    return first != "NOT_RUN" &&
        (second == "NOT_RUN" || first == "COMPLETED_ACTIVATED")
}

function safety_calibration_sequence_valid(mode_number, calibration_number, seen_not_run, value) {
    for (mode_number = 1; mode_number <= 3; mode_number++) {
        if (!mode_failed(mode_number)) {
            return 0
        }
    }
    if (values["Calibration-Classification"] != "NOT_RUN") {
        return 0
    }

    seen_not_run = 0
    for (calibration_number = 1; calibration_number <= 3; calibration_number++) {
        value = values[calibration[calibration_number]]
        if (value == "NOT_RUN") {
            seen_not_run = 1
        } else if (seen_not_run) {
            return 0
        }
    }
    return 1
}

BEGIN {
    required[1] = "Gate"
    required[2] = "Outcome"
    required[3] = "Commit"
    required[4] = "APK-SHA256"
    required[5] = "Projector"
    required[6] = "Android-TV"
    required[7] = "Firmware"
    required[8] = "Netflix-Version"
    required[9] = "Tested-Application"
    required[10] = "Original-M0-Commit"
    required[11] = "Original-M0-APK-SHA256"
    required[12] = "Original-M0-Decision"
    required[13] = "Original-M0-Investigation-Count"
    required[14] = "M0.1-Investigation-Count"
    required[15] = "Baseline-80-MS-Attempt-1"
    required[16] = "Baseline-80-MS-Attempt-2"
    required[17] = "System-Tap-Timeout-Attempt-1"
    required[18] = "System-Tap-Timeout-Attempt-2"
    required[19] = "Controller-Handoff-Attempt-1"
    required[20] = "Controller-Handoff-Attempt-2"
    required[21] = "Calibration-Baseline-80-MS"
    required[22] = "Calibration-System-Tap-Timeout"
    required[23] = "Calibration-Controller-Handoff"
    required[24] = "Calibration-Classification"
    required[25] = "Winning-Mode"
    required[26] = "Key-Leakage"
    required[27] = "Controller-Lifecycle"
    required[28] = "No-ADB-Input"
    required[29] = "Evidence-Integrity"
    required[30] = "Stop-Reason"

    for (field_number = 1; field_number <= 30; field_number++) {
        known[required[field_number]] = 1
    }

    mode[1] = "BASELINE_80_MS"
    mode[2] = "SYSTEM_TAP_TIMEOUT"
    mode[3] = "CONTROLLER_HANDOFF"
    attempt_one[1] = "Baseline-80-MS-Attempt-1"
    attempt_two[1] = "Baseline-80-MS-Attempt-2"
    attempt_one[2] = "System-Tap-Timeout-Attempt-1"
    attempt_two[2] = "System-Tap-Timeout-Attempt-2"
    attempt_one[3] = "Controller-Handoff-Attempt-1"
    attempt_two[3] = "Controller-Handoff-Attempt-2"
    calibration[1] = "Calibration-Baseline-80-MS"
    calibration[2] = "Calibration-System-Tap-Timeout"
    calibration[3] = "Calibration-Controller-Handoff"
}

{
    sub(/\r$/, "")

    if ($0 ~ /^[[:space:]]*$/) {
        next
    }

    if ($0 ~ /^[[:space:]]*#/) {
        reject("unexpected heading/comment line " trim($0))
        next
    }

    separator = index($0, ":")
    if (separator == 0) {
        malformed_line = trim($0)
        malformed_name = $0
        sub(/^[[:space:]]*/, "", malformed_name)
        sub(/[[:space:]=].*$/, "", malformed_name)
        if (malformed_name in known) {
            reject("malformed field " malformed_name)
        } else {
            reject("unexpected colon-free line " malformed_line)
        }
        next
    }

    name = trim(substr($0, 1, separator - 1))
    value = trim(substr($0, separator + 1))

    if (name !~ /^[A-Za-z][A-Za-z0-9.-]*$/) {
        reject("malformed field name " name)
        next
    }

    if (!(name in known)) {
        reject("unknown field " name)
        next
    }

    if (seen[name]++) {
        reject("duplicate field " name)
        next
    }

    if (value == "") {
        reject("empty value for " name)
        next
    }

    upper_value = toupper(value)
    if (value ~ /[<>]/ || value ~ /\{\{|\}\}/ ||
        upper_value ~ /(^|[^A-Z])(TBD|TODO|FIXME|PENDING|PLACEHOLDER|REPLACE|CHANGEME|UNKNOWN|TEST-FIXTURE-ONLY)([^A-Z]|$)/) {
        reject("unresolved placeholder in " name)
        next
    }

    values[name] = value
}

END {
    for (field_number = 1; field_number <= 30; field_number++) {
        name = required[field_number]
        if (!(name in seen)) {
            reject("missing field " name)
        }
    }

    if (("Gate" in values) && values["Gate"] != "M0.1") {
        reject("Gate must be M0.1")
    }

    if (("Outcome" in values) &&
        values["Outcome"] != "PROCEED_TO_M0_RETEST" &&
        values["Outcome"] != "STOP") {
        reject("Outcome must be PROCEED_TO_M0_RETEST or STOP")
    }

    if ("Commit" in values) {
        commit = values["Commit"]
        if (commit !~ /^[[:xdigit:]]+$/ || length(commit) != 40) {
            reject("Commit must be exactly 40 hexadecimal characters")
        }
    }

    if ("APK-SHA256" in values) {
        apk_sha256 = values["APK-SHA256"]
        if (apk_sha256 !~ /^[[:xdigit:]]+$/ || length(apk_sha256) != 64) {
            reject("APK-SHA256 must be exactly 64 hexadecimal characters")
        }
    }

    if (("Projector" in values) && values["Projector"] != "XGIMI Horizon XK03H") {
        reject("Projector must be XGIMI Horizon XK03H")
    }

    if (("Tested-Application" in values) && values["Tested-Application"] != "com.netflix.mediaclient") {
        reject("Tested-Application must be com.netflix.mediaclient")
    }

    if (("Original-M0-Commit" in values) &&
        values["Original-M0-Commit"] != "6223c19e4900c8086ba549e9fa326fd41d156c11") {
        reject("Original-M0-Commit must preserve the immutable M0 commit")
    }

    if (("Original-M0-APK-SHA256" in values) &&
        values["Original-M0-APK-SHA256"] != "f3cc262c74645800515da01d28e6e175833b29761188a88c5a4e15af8bd2535a") {
        reject("Original-M0-APK-SHA256 must preserve the immutable M0 APK")
    }

    if (("Original-M0-Decision" in values) && values["Original-M0-Decision"] != "FAIL") {
        reject("Original-M0-Decision must remain FAIL")
    }

    if (("Original-M0-Investigation-Count" in values) &&
        values["Original-M0-Investigation-Count"] != "1") {
        reject("Original-M0-Investigation-Count must remain 1")
    }

    if (("M0.1-Investigation-Count" in values) &&
        values["M0.1-Investigation-Count"] != "1") {
        reject("M0.1-Investigation-Count must be 1")
    }

    if (("Commit" in values) && ("APK-SHA256" in values) &&
        ("Original-M0-Commit" in values) && ("Original-M0-APK-SHA256" in values)) {
        commit_is_original = tolower(values["Commit"]) == tolower(values["Original-M0-Commit"])
        apk_is_original = tolower(values["APK-SHA256"]) == tolower(values["Original-M0-APK-SHA256"])
        if (commit_is_original != apk_is_original) {
            reject("Commit and APK-SHA256 must identify the same M0.1 artifact")
        } else if (commit_is_original && apk_is_original) {
            reject("M0.1 artifact identity must differ from the immutable M0 artifact")
        }
    }

    for (mode_number = 1; mode_number <= 3; mode_number++) {
        first = values[attempt_one[mode_number]]
        second = values[attempt_two[mode_number]]

        if ((attempt_one[mode_number] in values) && !is_attempt(first)) {
            reject(attempt_one[mode_number] " has invalid attempt result " first)
        }
        if ((attempt_two[mode_number] in values) && !is_attempt(second)) {
            reject(attempt_two[mode_number] " has invalid attempt result " second)
        }
        if (second != "NOT_RUN" && first != "COMPLETED_ACTIVATED") {
            reject(attempt_two[mode_number] " may run only after COMPLETED_ACTIVATED")
        }
        if (first == "NOT_RUN" && second != "NOT_RUN") {
            reject_order()
        }
    }

    for (mode_number = 1; mode_number <= 2; mode_number++) {
        if (mode_not_run(mode_number) && !mode_not_run(mode_number + 1)) {
            reject_order()
        }
    }

    if (values["Baseline-80-MS-Attempt-1"] == "HANDOFF_FRAME_TIMEOUT" ||
        values["Baseline-80-MS-Attempt-2"] == "HANDOFF_FRAME_TIMEOUT" ||
        values["System-Tap-Timeout-Attempt-1"] == "HANDOFF_FRAME_TIMEOUT" ||
        values["System-Tap-Timeout-Attempt-2"] == "HANDOFF_FRAME_TIMEOUT") {
        reject("HANDOFF_FRAME_TIMEOUT is valid only for CONTROLLER_HANDOFF")
    }

    for (calibration_number = 1; calibration_number <= 3; calibration_number++) {
        calibration_value = values[calibration[calibration_number]]
        if ((calibration[calibration_number] in values) && !is_attempt(calibration_value)) {
            reject(calibration[calibration_number] " has invalid attempt result " calibration_value)
        }
    }

    if (values["Calibration-Baseline-80-MS"] == "HANDOFF_FRAME_TIMEOUT" ||
        values["Calibration-System-Tap-Timeout"] == "HANDOFF_FRAME_TIMEOUT") {
        reject("HANDOFF_FRAME_TIMEOUT is valid only for CONTROLLER_HANDOFF")
    }

    classification = values["Calibration-Classification"]
    if (("Calibration-Classification" in values) &&
        classification != "NOT_RUN" &&
        classification != "APPLICATION_SPECIFIC_COMPATIBILITY" &&
        classification != "PROJECTOR_OR_GESTURE_DISPATCH") {
        reject("Calibration-Classification has an invalid value")
    }

    winner = values["Winning-Mode"]
    winner_number = 0
    for (mode_number = 1; mode_number <= 3; mode_number++) {
        if (winner == mode[mode_number]) {
            winner_number = mode_number
        }
    }
    if (("Winning-Mode" in values) && winner != "NONE" && winner_number == 0) {
        reject("Winning-Mode must be NONE or a closed probe mode")
    }

    if (("Key-Leakage" in values) &&
        values["Key-Leakage"] != "PASS" && values["Key-Leakage"] != "FAIL") {
        reject("Key-Leakage must be PASS or FAIL")
    }
    if (("Controller-Lifecycle" in values) &&
        values["Controller-Lifecycle"] != "PASS" && values["Controller-Lifecycle"] != "FAIL") {
        reject("Controller-Lifecycle must be PASS or FAIL")
    }
    if (("No-ADB-Input" in values) && values["No-ADB-Input"] != "PASS") {
        reject("No-ADB-Input must be PASS")
    }
    if (("Evidence-Integrity" in values) && values["Evidence-Integrity"] != "PASS") {
        reject("Evidence-Integrity must be PASS")
    }

    outcome = values["Outcome"]
    stop_reason = values["Stop-Reason"]

    if (outcome == "PROCEED_TO_M0_RETEST") {
        if (winner_number == 0) {
            reject("PROCEED_TO_M0_RETEST requires one winning mode")
        } else {
            for (mode_number = 1; mode_number < winner_number; mode_number++) {
                if (!mode_failed(mode_number)) {
                    reject("prior modes must fail in certified order before the winning mode")
                }
            }

            if (values[attempt_one[winner_number]] != "COMPLETED_ACTIVATED" ||
                values[attempt_two[winner_number]] != "COMPLETED_ACTIVATED") {
                reject("winning mode requires two COMPLETED_ACTIVATED attempts")
            }

            for (mode_number = winner_number + 1; mode_number <= 3; mode_number++) {
                if (!mode_not_run(mode_number)) {
                    reject("later modes must be NOT_RUN after the winning mode")
                }
            }
        }

        if (!calibration_not_run()) {
            reject("calibration must be NOT_RUN after a winning mode")
        }
        if (!safety_attestations_pass()) {
            reject("PROCEED_TO_M0_RETEST requires all safety attestations to PASS")
        }
        if (stop_reason != "NONE") {
            reject("Stop-Reason must be NONE for PROCEED_TO_M0_RETEST")
        }
    } else if (outcome == "STOP") {
        if (winner != "NONE") {
            reject("Winning-Mode must be NONE for STOP")
        }

        exhausted = 1
        for (mode_number = 1; mode_number <= 3; mode_number++) {
            if (!mode_failed(mode_number)) {
                exhausted = 0
            }
        }

        if (stop_reason == "COMPATIBILITY_EXHAUSTED") {
            if (!exhausted) {
                reject("compatibility exhaustion requires every Netflix mode to fail")
            }

            calibration_has_activation = 0
            calibration_complete = 1
            for (calibration_number = 1; calibration_number <= 3; calibration_number++) {
                calibration_value = values[calibration[calibration_number]]
                if (calibration_value == "NOT_RUN" || calibration_value == "HANDOFF_FRAME_TIMEOUT") {
                    calibration_complete = 0
                }
                if (calibration_value == "COMPLETED_ACTIVATED") {
                    calibration_has_activation = 1
                }
            }
            if (!calibration_complete) {
                reject("compatibility exhaustion requires all three calibration attempts")
            } else if (calibration_has_activation &&
                classification != "APPLICATION_SPECIFIC_COMPATIBILITY") {
                reject("calibration activation requires APPLICATION_SPECIFIC_COMPATIBILITY")
            } else if (!calibration_has_activation &&
                classification != "PROJECTOR_OR_GESTURE_DISPATCH") {
                reject("calibration failure requires PROJECTOR_OR_GESTURE_DISPATCH")
            }

            if (!safety_attestations_pass()) {
                reject("compatibility exhaustion requires all safety attestations to PASS")
            }
        } else if (stop_reason == "HANDOFF_FRAME_TIMEOUT" ||
            stop_reason == "KEY_LEAKAGE" ||
            stop_reason == "CONTROLLER_LIFECYCLE") {
            if (calibration_not_run()) {
                if (!safety_sequence_valid()) {
                    reject("safety STOP must end at the first failed invariant")
                }
            } else if (!safety_calibration_sequence_valid()) {
                reject("calibration safety STOP must preserve only the completed ordered prefix")
            }
            if (values["No-ADB-Input"] != "PASS" || values["Evidence-Integrity"] != "PASS") {
                reject("safety STOP requires complete no-ADB-input and evidence-integrity attestations")
            }

            handoff_timeouts = 0
            for (mode_number = 1; mode_number <= 3; mode_number++) {
                if (values[attempt_one[mode_number]] == "HANDOFF_FRAME_TIMEOUT") {
                    handoff_timeouts++
                }
                if (values[attempt_two[mode_number]] == "HANDOFF_FRAME_TIMEOUT") {
                    handoff_timeouts++
                }
            }
            for (calibration_number = 1; calibration_number <= 3; calibration_number++) {
                if (values[calibration[calibration_number]] == "HANDOFF_FRAME_TIMEOUT") {
                    handoff_timeouts++
                }
            }

            if (stop_reason == "HANDOFF_FRAME_TIMEOUT") {
                if (handoff_timeouts != 1 ||
                    values["Key-Leakage"] != "PASS" ||
                    values["Controller-Lifecycle"] != "PASS") {
                    reject("HANDOFF_FRAME_TIMEOUT must be the single failed invariant")
                }
            } else if (stop_reason == "KEY_LEAKAGE") {
                if (values["Key-Leakage"] != "FAIL" ||
                    values["Controller-Lifecycle"] != "PASS" || handoff_timeouts != 0) {
                    reject("KEY_LEAKAGE must identify the single failed invariant")
                }
            } else if (values["Controller-Lifecycle"] != "FAIL" ||
                values["Key-Leakage"] != "PASS" || handoff_timeouts != 0) {
                reject("CONTROLLER_LIFECYCLE must identify the single failed invariant")
            }
        } else {
            reject("STOP requires a closed compatibility or safety Stop-Reason")
        }
    }

    if (invalid) {
        exit 1
    }

    print "M0.1 report valid: " outcome
}
' < "$report"
