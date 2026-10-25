# Open TV Touchpad

## Unified remote proof (H0)

The user approved a separate local-hub experiment on 2026-10-24: one Mac
window combines Android TV remote buttons with scrcpy UHID mouse input. **H0 PASS
is scoped to the local Mac functional proof.** The user has confirmed
visible pointer movement, UHID profile/title selection, both scroll axes, dragging,
Stop/reconnect and playback/pause/resume. The earlier broad controls PASS is preserved,
but its Netflix shortcut claim was contradicted by a later logged launch failure.
The corrected shortcut now passed a separate live retest: Netflix opens and both
channels stay connected. Next is new hub trust/PWA design and its acceptance gate,
not network implementation. Abrupt link/power loss and active-drag interruption
remain untested production reliability limitations; PWA and Windows are not certified.
The earlier
accessibility gates remain failed/stopped. This experiment is not the PWA or
a released remote app. See [the proof runbook](experiments/hub_remote/README.md)
and [its hardware evidence](docs/gates/h0-report.md).

## Original Android gate

Open TV Touchpad is an Android TV hardware-gate project. This repository
contains the M0/M0.1 Android gate baseline and the isolated H0 Mac experiment.
The Android release contains no product networking, web or input behavior.

The certified XGIMI Horizon XK03H run recorded an M0 `FAIL`: cursor movement
worked, but accessibility-dispatched taps did not activate a Netflix profile
tile. The approved architecture therefore stops before M1. See
`docs/progress.md` and `docs/gates/m0-report.md`.

## Build

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

## M0 Controller

Open the launcher activity, choose **Open accessibility settings and assign
shortcut**, enable the gesture-only service, and assign the system
accessibility shortcut. While the service is enabled, its focusable,
non-touchable D-pad controller remains available over the application under
test; the separate cursor overlay remains non-focusable and non-touchable.
Use the system shortcut to disable the service and remove both overlays.
