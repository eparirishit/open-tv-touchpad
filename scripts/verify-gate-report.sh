#!/bin/sh

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <report> M0" >&2
    exit 2
fi

report=$1
gate=$2

case $gate in
    M0) ;;
    *)
        echo "unsupported gate: $gate" >&2
        exit 1
        ;;
esac

if [ ! -f "$report" ] || [ ! -r "$report" ]; then
    echo "report is not a readable file: $report" >&2
    exit 1
fi

LC_ALL=C awk -v expected_gate="$gate" '
function trim(text) {
    sub(/^[[:space:]]+/, "", text)
    sub(/[[:space:]]+$/, "", text)
    return text
}

function reject(message) {
    print "invalid " expected_gate " report: " message
    invalid = 1
}

BEGIN {
    required[1] = "Gate"
    required[2] = "Decision"
    required[3] = "Commit"
    required[4] = "APK-SHA256"
    required[5] = "Projector"
    required[6] = "Android-TV"
    required[7] = "Firmware"
    required[8] = "Netflix-Version"
    required[9] = "Overlay"
    required[10] = "Tap"
    required[11] = "Vertical-Scroll"
    required[12] = "Horizontal-Scroll"
    required[13] = "Drag"
    required[14] = "Cancel"
    required[15] = "Playback-1080p"
    required[16] = "Investigation-Count"
    required[17] = "Investigation-Details"

    for (field_number = 1; field_number <= 17; field_number++) {
        known[required[field_number]] = 1
    }

    observations[1] = "Overlay"
    observations[2] = "Tap"
    observations[3] = "Vertical-Scroll"
    observations[4] = "Horizontal-Scroll"
    observations[5] = "Drag"
    observations[6] = "Cancel"
    observations[7] = "Playback-1080p"
}

{
    sub(/\r$/, "")

    if ($0 ~ /^[[:space:]]*$/ || $0 ~ /^[[:space:]]*#/) {
        next
    }

    separator = index($0, ":")
    if (separator == 0) {
        malformed_name = $0
        sub(/^[[:space:]]*/, "", malformed_name)
        sub(/[[:space:]=].*$/, "", malformed_name)
        if (malformed_name in known) {
            reject("malformed field " malformed_name)
        }
        next
    }

    name = trim(substr($0, 1, separator - 1))
    value = trim(substr($0, separator + 1))

    if (name !~ /^[A-Za-z][A-Za-z0-9-]*$/) {
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
        upper_value ~ /(^|[^A-Z])(TBD|TODO|FIXME|PLACEHOLDER|REPLACE|CHANGEME|UNKNOWN)([^A-Z]|$)/) {
        reject("unresolved placeholder in " name)
        next
    }

    values[name] = value
}

END {
    for (field_number = 1; field_number <= 17; field_number++) {
        name = required[field_number]
        if (!(name in seen)) {
            reject("missing field " name)
        }
    }

    if (("Gate" in values) && values["Gate"] != expected_gate) {
        reject("Gate must be " expected_gate)
    }

    if (("Decision" in values) && values["Decision"] != "PASS") {
        reject("Decision must be PASS")
    }

    if (("Projector" in values) && values["Projector"] != "XGIMI Horizon XK03H") {
        reject("Projector must be XGIMI Horizon XK03H")
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

    for (observation_number = 1; observation_number <= 7; observation_number++) {
        name = observations[observation_number]
        if ((name in values) && values[name] != "PASS") {
            reject(name " must be PASS")
        }
    }

    if ("Investigation-Count" in values) {
        investigation_count = values["Investigation-Count"]
        if (investigation_count !~ /^[0-9]+$/) {
            reject("Investigation-Count must be 0 or 1")
        } else if ((investigation_count + 0) > 1) {
            reject("Investigation-Count must not exceed 1")
        } else if ((investigation_count + 0) == 0 && values["Investigation-Details"] != "NONE") {
            reject("Investigation-Details must be NONE when Investigation-Count is 0")
        } else if ((investigation_count + 0) == 1 && values["Investigation-Details"] == "NONE") {
            reject("Investigation-Details must document the compatibility investigation")
        }
    }

    if (invalid) {
        exit 1
    }

    print expected_gate " report valid: PASS"
}
' < "$report"
