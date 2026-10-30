# Now Steering — Phase 0 probe

Throwaway debug app that answers five questions about a car's Bluetooth stereo
(character set, field width, refresh behaviour, steering-wheel buttons, and
coexistence with YouTube Music). No navigation features. No polish.

**Download (always the newest build):**
https://github.com/joshferns/NowSteering/releases/latest/download/NowSteering-probe.apk

Every push that touches the app builds a debug APK in GitHub Actions
(`.github/workflows/probe-apk.yml`) and publishes it as a new release
`probe-<n>`, marked latest. All builds use the committed `app/debug.keystore`,
so a new build installs over the old one without uninstalling (the log survives).

## One-time phone setup (Samsung S20 FE, Android 13)
1. Open the link above in Chrome, download, tap the file, allow
   "Install unknown apps" for Chrome if asked. Play Protect may warn: "Install anyway".
2. Open **NS Probe**, allow notifications and "Nearby devices" (Bluetooth names).
3. **Restricted settings (Android 13 sideload rule):** tap *App info (restricted
   settings)* → ⋮ (top right) → *Allow restricted settings* → confirm.
   Without this the next step is greyed out.
4. Tap *Notif listener access* → enable **NS Probe session watcher**.
5. Tap *Battery: don't optimise* → Allow. Optionally also Settings → Battery →
   Background usage limits → *Never sleeping apps* → add NS Probe.

The status lines at the top should then read `listener=true battExempt=true notif=true`.

## What's in the app
| Section | What it does |
|---|---|
| Flash (top, green) | Last button / transport command, large text, with time |
| 1. Media session | Foreground service with a `MediaSession` and a looping near-silent `AudioTrack` (±1 LSB, inaudible). Start / Stop / Re-grab. "Request audio focus" off = YouTube Music keeps playing |
| 2. Refresh mode | a) metadata only, b) fake track change (new media ID + duration), c) pause → update → play |
| 3. Text tester | Title / Artist / Album: preset strings (rulers, arrows, box, blocks, symbols, Devanagari, emoji…) or custom text; send each field or all |
| 4. Auto counter | Title = `#n HH:mm:ss` every N seconds using the selected refresh mode |
| 5. Coexistence | Live list of all media sessions in Android's priority order; mirror target (YouTube Music) text into our session; forward next/prev/play/pause to it |
| 6. Event log | Everything above + Bluetooth connect/disconnect, A2DP state, audio focus, active audio players, permissions. Stored in a file; Share text / Share file / Copy / Clear; *Add note* writes your own marker line |

Code: `app/src/main/java/com/nowsteering/probe/` — `ProbeService` (session,
audio, buttons, refresh modes), `SessionWatcher` (other apps' sessions),
`ProbeApp` (Bluetooth/audio logging, log header), `MainActivity` (UI),
`ProbeLog` (persistent log).

## Building locally
`./gradlew assembleDebug` (JDK 17+, Android SDK 35). Output:
`app/build/outputs/apk/debug/app-debug.apk`.

The in-car checklist is in [docs/IN_CAR_TEST_SCRIPT.md](docs/IN_CAR_TEST_SCRIPT.md).
