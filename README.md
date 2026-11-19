# Open TV Touchpad

Open TV controls an Android TV projector from a phone. The Android pilot pairs
directly over Wi-Fi for TV buttons and uses Bluetooth for the pointer, clicks,
and scrolling needed by mobile Netflix. The pilot has been tested on a OnePlus
Nord and XGIMI Horizon. See the [current status](docs/progress.md#current-status)
and [mobile roadmap](BACKLOG.md#proposed-mobile-roadmap).

## Android phone pilot

The phone app lives in `experiments/hid_probe`. Use JDK 17 and Android SDK 36
to run the pilot's opt-in Gradle tasks.

```sh
./gradlew -PhidProbe=true :experiments:hid_probe:testDebugUnitTest :experiments:hid_probe:assembleDebug
```

See the [pilot runbook](experiments/hid_probe/README.md#build-and-local-checks)
and [installation evidence](docs/gates/nord-setup-wizard-report.md).
Updates on an enrolled phone require the existing signing key.

## Browser hub and Mac experiment

The browser hub in `hub/` and `web/` implements local HTTPS enrollment and
the remote UI. Projector input remains disabled pending the
[protected transport](docs/gates/h1a-report.md) and
[real-browser trust](docs/gates/h1b-report.md) checks.
See [browser hub setup](docs/setup-hub.md).

The [Mac remote experiment](experiments/hub_remote/README.md) verified
Netflix pointer and TV button behavior using a separate test setup.
Its [hardware report](docs/gates/h0-report.md) records the transport
and failure cases still needing verification.

## Original projector-side test

The `android/app` module contains the M0/M0.1 accessibility tests that run
on Android TV. Its release build excludes the test controller.

On the XGIMI Horizon XK03H, the controller moved the cursor and failed to
activate a Netflix profile tile. This route is blocked before M1.
See the [M0 hardware report](docs/gates/m0-report.md).

### Build the hardware test

The reproducible baseline requires JDK 17 and Android SDK 36. With `JAVA_HOME`,
`ANDROID_HOME`, and `GRADLE_USER_HOME` configured, run:

```sh
./gradlew :android:app:testDebugUnitTest :android:app:assembleDebug
```

The debug APK is written to
`android/app/build/outputs/apk/debug/app-debug.apk`.

That build command does not perform hardware verification. The completed
certified-device result is recorded in `docs/gates/m0-report.md` and
`docs/gates/m0-checklist.md`.

### Run the M0 controller

Open the launcher activity, choose **Open accessibility settings and assign
shortcut**, enable the gesture-only service, and assign the system
accessibility shortcut. While the service is enabled, its focusable,
non-touchable D-pad controller remains available over the application under
test; the separate cursor overlay remains non-focusable and non-touchable.
Use the system shortcut to disable the service and remove both overlays.
