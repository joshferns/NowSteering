# Changelog

One entry per release, newest first. Dates are release dates (UTC). Every release updates this file and any doc it affects.
The story behind each release, with what the car tests found, is in [docs/HISTORY.md](docs/HISTORY.md).

## 0.6.0 (2026-11-18) — presets, trip cost, Torque on screen, warnings
- **Presets**: Driver (new default), Trip, Car (only when Torque is installed), Classic (exactly 0.5.0). Picker on the main screen with a preview of every situation at the car's widths. Double Next cycles them and shows the name for 2 s (it still clears Arrived first); the nav / clock toggle is gone. See [docs/CAR_SCREENS.md](docs/CAR_SCREENS.md).
- **Driver**: speed always visible (17-character line 1 with fallbacks), music on two lines that it gives up near a turn (turn bar, speed and the song's time left). Missing or stale values are dropped, never shown as `--`.
- **Trip cost**: settings for fuel price and typical km/L; `trip.cost`, `trip.cost_to_go`, `trip.cost_total`, `trip.cost_per_10km`; Trip preset line 3 `Rs ~125 trip · ~48 now`. `[TRIP]` log line every 60 s and at disconnect. Fuel used = Torque PID `ff1271`, long-term km/L = `ff5202`. See [docs/FIELDS.md](docs/FIELDS.md#trip-cost-060).
- **Warnings** (all presets; setting *Engine warnings from Torque*, on): `▲ HOT 107°C` (coolant > 105 °C for 5 s, clears < 102 °C for 5 s) and `▲ LOW 12.0V` (voltage < 12.2 V for 10 s with RPM > 500, clears > 12.6 V for 5 s). Line 1 on idle / music, line 2 on navigation, held near a turn; 10 s on / 50 s off. Thresholds in the car profile, read-only in Diagnostics, with **Test HOT warning** and **Test LOW warning** buttons.
- **Keep Now Steering on screen** is on for new installs (existing installs keep their value).
- Fixed / small: **Share key lines** now includes every `[SCREEN]` line plus `[TRIP]`, `[WARN]`, `[EXPERIMENT]`; an artist `Unknown`, `<unknown>` or empty is treated as missing (no `- artist`); the Power saving notice reads top to bottom (`nav less smooth` then `turn it off`); `car.distance_to_empty` and `car.ff126a` never shown.
- Diagnostics: **Play-button experiment** (reports PAUSED for 10 s, logs any PLAY key). Default behaviour unchanged.
- Tests: 148 (was 89), including presets that fit 17 / 20 / 20 with long values, cost maths, warning thresholds, hysteresis and placement. Classic is still golden-tested against the 0.4.0 code.

## 0.5.0 (2026-11-13) — v2 foundation, no visible change
- Internal rebuild around the v2 model: sources → **fields** → **rules** → **faces** (line templates) → **warning layer** → display engine, with a **car profile** for the 2014 Honda City. Today's screens are the first preset, "Current", expressed as data. See [docs/DESIGN.md](docs/DESIGN.md#v2-model) and [docs/FIELDS.md](docs/FIELDS.md).
- Nothing changes for the user: every car screen, setting and default, dead reckoning, claim rules, buttons, call safeguard, logging and Diagnostics (incl. Torque) behave as in 0.4.0. Keep on screen is still OFF by default.
- Torque fields (`car.coolant`, `car.voltage`, `car.rpm`, `car.trip_kmpl`, any `car.<pid>`) and `phone.battery` can be read; no face shows them yet.
- Tests: 89 (was 45), including a golden replay of 40 000 frames and all 754 recorded idle screens against the v0.4.0 code.

## 0.4.0 (2026-11-12) — Phase 2b probe
- Added: **Torque** section in Diagnostics (read-only). Shows: Torque installed (Pro / Lite / no), plugin service bound, Torque plugin API version, OBD connected, and the list of PIDs Torque has (active or ECU-supported) with live value and unit, refreshed every second. A value that Torque has not updated for 10 s shows "— (stale)"; a PID it never read shows "—".
- Added: `[TORQUE]` log lines (bind/unbind, OBD connect/disconnect, PID list once per connection, values every 5 s), and **Share key lines** now includes them.
- The car screen is unchanged. Torque is never started, written to, locked or sent commands by Now Steering.

## 0.3.4 (2026-11-12)
- Fixed: **Share key lines** did nothing once the log was large (about 750 KB). It now always sends a file; **Share text** switches to a file above 60 000 characters; **Copy** keeps the last 60 000; any sharing failure shows a dialog and is logged.

## 0.3.3 (2026-11-11)
- Fixed: Diagnostics layout. Dropdowns use the normal closed-view layout and sit full width under their field; the song prefix is a radio list so the chosen value is always visible.

## 0.3.2 (2026-11-11)
- Added: **▶** as a song prefix, and it is the default (a saved • or ♪ is switched to ▶ once).
- Added: "Glyph candidates" test screen (15 note-like and symbol glyphs).

## 0.3.1 (2026-11-11)
- Fixed: Next / Previous are **dropped** instead of forwarded while the music app is loading, within 2 s of a track change, or in ERROR (forwarding into a loading regular-YouTube player made it show "An error occurred").
- Removed: triple-tap Next (buttons follow the last-started player, so the gesture could not work when needed).

## 0.3.0 (2026-11-08)
- Releases are now named by version (`VERSION` file).
- Nav screen far from a turn: line 2 = Maps' time left + 6-cell trip bar, line 3 = the playing song (else the road); last 300 m: turn bar + road.
- Added: setting **Arrival time on line 2**; notification button **Show on car**; setting **Keep Now Steering on screen** (mirror of the playing title with a time-left bar); re-claim after any other app starts playing (when navigating or Keep on); one-tap car test screens; **Share key lines**.
- Fixed: after 90 s without a Maps update the distance jumped back to Maps' old value; it now holds.

## 0.2.21 (build-11, 2026-11-02)
- Displayed distances round up to Maps' steps, so the car never reads below the estimate.

## 0.2.20 (build-10, 2026-11-02)
- `• Rerouting...` shown during a real reroute; blank / no-distance notifications are still held.

## 0.2.19 (build-9, 2026-11-02)
- Dead reckoning rewritten: follows Maps while it updates, counts only in gaps, pauses when Maps stays higher.
- Hold the previous screen over blank / no-distance notifications; start-up debounce.
- Arrived as soon as Maps shows the destination marker (≤ 30 m); Arrived cleared without GPS (phone location 300 m away, 20 min).
- Power saving warning on the car; Power saving required again on the checklist; Google Maps battery status shown.
- Turn bar starts with its arrow; re-grab sends the latest screen.

## 0.2.18 (build-8, 2026-11-01)
- Turn bar (last 300 m) and trip bar, each a setting; re-claim after song changes while navigating; ignore upward distance corrections under 30 m.

## 0.2.17 (build-7, 2026-11-01)
- Arrived stays until the car moves, a new trip, double Next or disconnect; "Starting navigation…" no longer counts as an instruction; destination pin icon labelled; Power saving only advisory.

## 0.2.16 (build-6, 2026-11-01)
- Ignore Maps' "towards X" variant once a real instruction shows; long names cut at a word.

## 0.2.15 (build-5, 2026-11-01)
- Six Maps arrow icons labelled; first dead reckoning; Arrived screen for 10 s; first name of "A / B" roads.

## 0.2.14 (build-4, 2026-10-31)
- Buttons refresh the session list before forwarding; the car shows why a press did nothing.

## 0.2.13 (build-3, 2026-10-31)
- Setup checklist on the main screen; wake lock while the car is connected; selectable refresh style; seconds on the idle clock; screen and A2DP logging.

## 0.2.12 (build-2, 2026-10-30)
- Version codes keep rising after the CI counter reset.

## 0.2.1 (build-1, 2026-10-30)
- First Now Steering app (`com.nowsteering.app`): display engine, Maps parser, idle clock with GPS speed, button forwarding, call safeguard, auto-start, Settings and Diagnostics.

## Probe (`com.nowsteering.probe`, retired)
- **0.1.3-probe (2026-10-30):** key timing fix for the car's `eventTime=0`; START warns if already running.
- **0.1.2-probe (2026-10-30):** DOWN / UP logged separately, live gesture readout with adjustable timings.
- **0.1.1-probe (2026-10-30):** first probe (silent-audio MediaSession, text tester, refresh modes, button and Bluetooth logging, event log with Share).
