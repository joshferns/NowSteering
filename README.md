# Now Steering

Shows Google Maps turn-by-turn prompts, a clock and GPS speed on a car's stock Bluetooth stereo
screen (2014 Honda City), using the "Now Playing" text fields. Steering-wheel Next / Previous and the
head-unit Pause button control the music app. Personal use; debug builds only.

**Download (newest builds):**
- App: https://github.com/joshferns/NowSteering/releases/latest/download/NowSteering.apk
- Phase 0 probe (for session 2): https://github.com/joshferns/NowSteering/releases/latest/download/NowSteering-probe.apk

The two are separate apps (`com.nowsteering.app` and `com.nowsteering.probe`) and can be installed side by side.
Every build is signed with the committed `app/debug.keystore`, so updates install over the old version.
GitHub Actions (`.github/workflows/apks.yml`) runs the unit tests, builds both, and publishes them as the latest release.

## One-time setup (Now Steering, Android 13)
In the app, tap the permission buttons in order:
1. **Allow restricted settings** → ⋮ (top right) → *Allow restricted settings* (needed for sideloaded apps).
2. **Notification access** → enable *Now Steering (Maps + media)*.
3. **Notifications, Bluetooth, GPS** → allow all.
4. **GPS: allow all the time** → choose *Allow all the time* (speed works when started from the car).
5. **Battery: don't optimise** → Allow (also lets the car connection start the app).

Then **Choose car** → pick the car (e.g. "Honda HFT") and leave *Start automatically* ticked.

## What it does
- **Car screen.** Nav: `↖ 300 m` / road / `ETA 19:42 6.1km`. Idle: `19:42  48 km/h` / date / `Now Steering`.
  The display engine (`DisplayEngine`, `Glyphs`) cuts text to 15/18/18 characters, swaps unsupported characters
  for ASCII, and sends only changed text, at most once per second.
- **Maps.** `NavListener` reads Google Maps' navigation notification: distance (title), road (text),
  time · distance · ETA (sub-text). Maps puts the turn arrow only in the notification icon, so each new icon is
  logged once as ASCII art; labelled icons go in `KnownIcons` (empty until real drives are logged). Until an
  icon is labelled, the arrow shows `•`.
- **Buttons.** Next: single = next song, double = switch nav/idle screen (600 ms window, adjustable in Diagnostics).
  Previous = previous song. Pause (head unit) = pause/resume toggle. Forwarded to the music app that is playing.
- **Music.** Never takes audio focus, so music keeps playing. While navigating, re-claims the car screen on each
  new instruction and ~1.5 s after a song change or skip; when not navigating, the music app's song info shows.
- **Progress bars** (settings, both on): turn bar `█████▒▒▒▒▒` replaces the ETA line in the last 300 m before a turn;
  trip bar `ETA 09:45 ███▒▒` shows overall trip progress.
- **Arrival.** `● Arrived` as soon as Maps shows its destination marker within 30 m. It stays until the car
  stops and moves off again (> 10 km/h), moves 300 m away, a new trip starts, double Next, 20 min pass, or the car disconnects.
- **Rerouting.** Maps' "Rerouting..." shows as `• Rerouting...` until the new instruction arrives; Maps' blank / no-distance
  in-between notifications keep the previous instruction on screen.
- **Power saving must be off** (it blocks our GPS with the screen off). When it's on, the car shows
  "Power saving on / nav less smooth" at start, at each new trip and when it's switched on.
- **Calls.** On any call (ringing, in call, VoIP; via AudioManager mode) the app stops its audio and
  deactivates its session at once, and comes back 3 s after the call ends.
- **Auto-start.** Starts when the chosen car connects over Bluetooth, stops when it disconnects.
- **Diagnostics.** Gesture readout, what the car is being sent, Maps parse, active media sessions, text tester,
  event log with Share / Copy / Clear / Add note.

Code: `nowsteering/src/main/java/com/nowsteering/app/` (app), `app/` (probe).
Build locally: `./gradlew :nowsteering:assembleDebug :nowsteering:testDebugUnitTest`.

Test scripts: [Phase 1 in-car test](docs/PHASE1_TEST_SCRIPT.md) · [Phase 0 session 2](docs/SESSION2_TEST_SCRIPT.md).

## Known car behaviour (not app bugs)
- Using the microphone while connected (e.g. voice dictation in another app) makes the Honda show its
  call screen, which can look stuck. It isn't caused by Now Steering.
- Each sideloaded app needs *Allow restricted settings* before its notification access can be turned on.
  The main screen's checklist shows anything missing.
