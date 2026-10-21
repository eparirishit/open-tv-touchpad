# M0 Hardware Gate Report

This report records an M0 hardware gate failure. The PASS-only verifier is
expected to reject it. Checks after the required tap failure were not run, and
M1 must not begin.

Gate: M0
Decision: FAIL
Commit: 6223c19e4900c8086ba549e9fa326fd41d156c11
APK-SHA256: f3cc262c74645800515da01d28e6e175833b29761188a88c5a4e15af8bd2535a
Projector: XGIMI Horizon XK03H
Android-TV: 11
Firmware: RT.3.24.327 release-keys
Netflix-Version: 8.112.1 build 6 50665
Tested-Application: com.netflix.mediaclient
Overlay: NOT-RUN-AFTER-TAP-FAIL
Tap: FAIL
Vertical-Scroll: NOT-RUN-AFTER-TAP-FAIL
Horizontal-Scroll: NOT-RUN-AFTER-TAP-FAIL
Drag: NOT-RUN-AFTER-TAP-FAIL
Cancel: NOT-RUN-AFTER-TAP-FAIL
Playback-1080p: NOT-RUN-AFTER-TAP-FAIL
Investigation-Count: 1
Investigation-Details: ACCESSIBILITY_SETTINGS resolved to com.google.android.tv.frameworkpackagestubs/.Stubs$SettingsStub and showed no compatible app; debug-gate navigation now falls back to general TV Settings with regression and release-isolation coverage; replacement install and manual settings-path validation passed
