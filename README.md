# Open TV Touchpad

Open TV Touchpad is an Android TV hardware-gate project. This repository is
currently limited to the M0 gate baseline and does not contain networking,
pairing, certificate, web, storage, analytics, or product input behavior.

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
