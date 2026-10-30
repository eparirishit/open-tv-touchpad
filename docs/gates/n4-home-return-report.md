# Nord Home-return investigation — 2026-10-29

Status: reported Home-return failure reproduced and resolved in the controlled
retest by the owner-approved per-app setting. Broader daily-use readiness remains
NOT READY.

## Exact candidate and scope

Source `6abbc23c53c64d40e6618c562ac3442455e23b8e`, version21 /
0.21-daily-use-candidate. The installed APK was streamed and hashed without
modifying it: `da6f7bb68cbb16a96769f6f273b70bb9ad284ccf35c894169d11f1e61a0d84a5`.
Nord AC2001, Android API31, build AC2001_11_F.24. No APK, application source,
identity, pairing, transport bounds or input-admission behavior changed.

## Observed failure and mechanism

The owner reported disconnect after roughly one minute on Nord Home. Both owner
text and read-only UI inspection showed CONTROL_EXCHANGE failure, both channels
stopped, native teardown completed. The prior trace had expired.

A fresh owner-operated trial established TV/pointer control, then left the phone
on Home, screen awake/unlocked. Four samples at0/15/35/65seconds showed the
started foreground service still present, UID state FOREGROUND_SERVICE and Data
Saver Off. This alone did not prove the process could execute.

The system log supplies the missing evidence: OnePlus Hans froze Open TV at
21:24:24.781, about30seconds after its21:23:54.755 Home transition, in LcdOn
scene. It unfroze the app at21:25:54.857 as the owner returned. The contiguous
app trace shows PAUSE at46702ms and STOP at166785ms, then selected disconnect,
unregister and TEARDOWN_COMPLETE at166895ms. All times are local EDT,2026-10-29.
The app did not survive this return with working controls; the owner confirmed
the same CONTROL_EXCHANGE error. A registered foreground service was therefore
insufficient to prevent this OEM freeze. Suspension prevents timely TV protocol
maintenance; on return the channel is expired. No timeout extension or synthetic
cleanup proof is justified by this evidence.

## Approved narrow remedy

Read-only App info → Battery usage showed Allow foreground activity On,
Allow background activity Off, and Allow auto launch Off. After the owner’s
explicit approval, only Allow background activity was enabled. The other two
settings were verified unchanged. This is reversible through the same page.
No global exemption, wake lock, app auto-launch, boot receiver or reconnect loop
was introduced. Background execution may increase battery consumption.

## Controlled retest

The same installed APK was used. Owner confirmed a fresh Connect, working TV
button and pointer before Home. The second awake/unlocked Home interval preserved
the started foreground service at0/15/35/65seconds. System logs span Home at
21:33:53.553 to return at21:35:48.033 (114.480seconds) with no Hans freeze event
recorded in this interval. The owner confirmed pointer, TV button and scroll
worked after return **without Connect**, then Stop and physical remote worked.

An initial post-answer sample still showed the connection active, so Stop was
checked explicitly rather than inferred. The owner then confirmed stopped/native
teardown completed. The final trace records STOP342301ms, selected disconnected
342443ms, UNREGISTERED342444ms and TEARDOWN_COMPLETE342453ms; final service state
is foreground false, started false, foreground count0. A bound read-only service
may remain while the UI is visible; it is not a running control session.

Fixed-label pre-opt-in trace SHA256:
`d9d5c5ba364d55d3ef8e4a83f5242c44af2dd59accf5885ec4369e8e0db50252`.
Fixed-label post-opt-in trace SHA256:
`fefcb84fb5f9ba77d6c89a43251694eb0c0332b47bf05cb528a859b0dd51f369`.
Raw device logs/identifiers remain local; only fixed observations/digests are
reported. No software tests were rerun because executable source/APK is unchanged.
This closes the reported short Home-return failure on this Nord with the approved
setting. It does not close the broader gates below.

## Limits

This short diagnostic uses an authorized Wi-Fi debugging connection and an awake
screen. It cannot prove locked-phone, deep-sleep,30-minute retention/expiry,
network interruption, unplugged battery/thermal or endurance behavior. Historical
missing Bluetooth callbacks remain recorded. All existing native-completion,
visibility, enrollment and TLS/pairing requirements remain unchanged.
