# Open TV Touchpad

Open TV Touchpad is an Android TV hardware-gate project. This repository is
currently limited to the M0 gate baseline and does not contain networking,
pairing, certificate, web, storage, analytics, or product input behavior.

## Build

The reproducible baseline requires JDK 17 and Android SDK 36. With `JAVA_HOME`,
`ANDROID_HOME`, and `GRADLE_USER_HOME` configured, run:

```sh
./gradlew :android:app:testDebugUnitTest :android:app:assembleDebug
```

The debug APK is written to
`android/app/build/outputs/apk/debug/app-debug.apk`.

Hardware and instrumentation verification are outside this baseline task.

## M0 Controller

Open the launcher activity, choose **Open accessibility settings and assign
shortcut**, enable the gesture-only service, and assign the system
accessibility shortcut. While the service is enabled, its focusable,
non-touchable D-pad controller remains available over the application under
test; the separate cursor overlay remains non-focusable and non-touchable.
Use the system shortcut to disable the service and remove both overlays.
