# Now Steering

Turns a car's basic Bluetooth stereo screen into a small glanceable dashboard. Built for a 2014 Honda City (stock head unit, "Honda HFT")
and a Samsung Galaxy S20 FE (Android 13). It shows Google Maps turn prompts, a clock with GPS speed, and the song that is playing,
using the "Now Playing" text the stereo already displays. The steering-wheel Next / Previous and the head-unit Pause keep controlling the music app.

It never plays music, never talks to the internet (the app has no internet permission), and reuses the apps you already have:
Google Maps for navigation, YouTube / YouTube Music for audio.

**Status:** personal-use debug builds, version 0.3.4. Phase 1 (navigation) and Phase 2a (glance layout) are done; Phase 2b (car data from Torque Pro) is in progress.

## Download

Newest build: https://github.com/joshferns/NowSteering/releases/latest/download/NowSteering.apk

Every build is signed with the same key, so a new version installs over the old one and keeps your settings and log.
Each release is named by version number (`v0.3.4`); see [CHANGELOG.md](CHANGELOG.md).

## Install (sideload)

1. Open the download link in Chrome on the phone and download `NowSteering.apk`.
2. Tap the downloaded file. If Android asks, allow "Install unknown apps" for Chrome.
3. If Play Protect warns about an unknown app, choose **Install anyway**.
4. Open **Now Steering** and follow "Permissions" below until the top box says **Setup complete**.
5. Tap **Choose car** and pick the car's Bluetooth name (for example *Honda HFT*). Leave *Start automatically when the car connects* ticked.

From then on the app starts when the car connects and stops when it disconnects.

## Permissions

The main screen has numbered buttons 1 to 5; do them in order. The top box lists anything still missing.

| Step | What | Why | How to grant (Samsung One UI 5.1) |
|---|---|---|---|
| 1 | **Allow restricted settings** | Android blocks Notification access for apps installed from a file until you allow this | Button 1 opens *App info*. Tap **⋮** (top right) → **Allow restricted settings** → confirm. The ⋮ menu only appears after you have tried step 2 once. |
| 2 | **Notification access** (special access) | Reads Google Maps' navigation notification, and lists media sessions so the song can be shown and Next / Previous / Pause forwarded | Button 2 → switch on **Now Steering (Maps + media)** → Allow. Without it there is no navigation and the wheel buttons do nothing. |
| 3 | **Notifications**, **Nearby devices** (Bluetooth), **Location** | The "Now Steering is on" notification; seeing paired devices and noticing the car connect; GPS speed | Button 3 shows the Android prompts. Allow all. For Location choose **While using the app** first. |
| 4 | **Location: Allow all the time** | So speed works when the car starts the app in the background | Button 4 → **Allow all the time** (Settings → Apps → Now Steering → Permissions → Location). |
| 5 | **Battery: unrestricted** | Keeps Samsung from killing the app | Button 5 → **Allow**. Optional: Settings → Battery → Background usage limits → *Never sleeping apps* → add Now Steering. |

Other permissions are declared but need no prompt: foreground service (media playback, location), keep-CPU-awake while the car is connected.
The app does **not** ask for microphone, phone, contacts, storage or internet.

### Power saving must be OFF while driving

Samsung **Power saving** switches GPS off when the screen is off, so speed and the distance countdown stop updating on a locked phone.
Turn it off in Quick settings (or Settings → Battery → Power saving) before you drive. If it is on, the car shows
`Power saving on / turn it off / nav less smooth` for 6 s when the app starts, when navigation starts and when you switch it on.
TODO: confirm the exact Samsung menu path for Power saving on this phone.

## Daily use

1. Get in the car. The phone connects; *Now Steering is on* appears.
2. Start navigation in Google Maps as usual (the phone can stay locked). The car shows the turn arrow, distance, time left, the song and a bar.
3. Steering **Next** = next song, **Previous** = previous song (first press restarts it), head-unit **Pause** = pause / resume.
   **Double-tap Next** switches between the navigation screen and the clock.
4. Pull down the notification shade and tap **Show on car** if another app (for example the regular YouTube app) has taken the car screen.
   Or tick **Keep Now Steering on screen** to take it back automatically.

What each screen looks like: [docs/CAR_SCREENS.md](docs/CAR_SCREENS.md). What each phone setting does: [docs/PHONE_UI.md](docs/PHONE_UI.md).

## Known car behaviour (not app bugs)

- Using the microphone while connected (voice dictation in another app) makes the Honda show its call screen, which can look stuck.
- The car's own clock runs about 1 minute fast; the app's time comes from the phone.
- Glyphs the car cannot draw (for example ♪) show as dots. See [docs/CAR_PROFILE_HONDA_CITY_2014.md](docs/CAR_PROFILE_HONDA_CITY_2014.md).

## Documentation

| | |
|---|---|
| [CHANGELOG.md](CHANGELOG.md) | one entry per release |
| [docs/HISTORY.md](docs/HISTORY.md) | timeline of phases 0, 1 and 2a, with what each car test found |
| [docs/DESIGN.md](docs/DESIGN.md) | how it works: architecture and every rule |
| [docs/CAR_SCREENS.md](docs/CAR_SCREENS.md) | every car screen as a mock-up, when it appears, what the buttons do |
| [docs/PHONE_UI.md](docs/PHONE_UI.md) | main screen, Diagnostics, the notification; every setting and its default |
| [docs/DECISIONS.md](docs/DECISIONS.md) | decision log with the reasons |
| [docs/CAR_PROFILE_HONDA_CITY_2014.md](docs/CAR_PROFILE_HONDA_CITY_2014.md) | measured head-unit facts |

Phase reports and test scripts (historical, kept as records): `docs/PHASE0_REPORT.md`, `docs/IN_CAR_TEST_SCRIPT.md`, `docs/PHASE1_*.md`, `docs/PHASE2A_*.md`.
`docs/SESSION2_TEST_SCRIPT.md` was never run (the probe's session 2 was dropped, see DECISIONS).

## Build

JDK 17+, Android SDK 35: `./gradlew :nowsteering:assembleDebug :nowsteering:testDebugUnitTest`.
The one-line `VERSION` file is the release version: bump it for every release (the tag is `v<VERSION>`). Pushing to the working branch runs
`.github/workflows/apks.yml`, which runs the unit tests, builds `NowSteering.apk` and the old probe `NowSteering-probe.apk`, and publishes them as the latest release.
Source: `nowsteering/` (the app) and `app/` (the Phase 0 probe, retired).
