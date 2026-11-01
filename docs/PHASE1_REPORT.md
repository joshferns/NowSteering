# Phase 1 report: nav mirror MVP (after tests 1–3, 2026-11-01)

## Works
- **Nav mirror with the phone locked.** Real arrows were correct at every turn, the distance counted down smoothly, ETA and distance left were right.
- **Display engine.** 15/18/18-character limits, word-boundary cuts, glyph fallback, at most 1 update/s, only on change. No blank or over-long screens.
- **Wheel buttons.** Next / Previous / head-unit Pause reach YouTube Music. Double Next switches nav ↔ clock.
- **Call safeguard.** A real call worked normally; we stepped aside at ringing and came back 3 s after.
- **Auto-start / stop** with the car (Honda HFT) via Bluetooth.
- **Arrival.** Clean switch ~5 s after arriving (Maps ends navigation itself).

## The three lines
| Line | Nav screen | Clock screen | Arrived screen |
|---|---|---|---|
| Title (big) | next maneuver's arrow + distance to it, e.g. `→ 230 m` (same as Google Maps) | `09:42  35 km/h` | `● Arrived` |
| Artist (person icon) | what Maps names for the next maneuver: the road you turn onto (`20th Main Rd`), `Turn right` when Maps has no road name, the destination on the last leg (`Home (Samvriddhi…)`), `to X` before the first instruction | date | destination name |
| Album (disc icon) | `ETA 09:45 600m`: arrival time + distance left | `Now Steering` | arrival time |

The road line changing during a trip is Maps changing its next-maneuver text (e.g. 09:43:16 → `20th Main Rd` 10 m before that turn).

## Locked-phone updates: Maps or dead reckoning?
Both, mostly Maps. In test 3, with Power saving off, Maps updated every 1–3 s even locked (09:43:55–09:46:02).
Dead reckoning ran a fraction of a second ahead of Maps and filled three real gaps of 4–8 s (450 → 300 m, 09:44:50–09:45:11).
57 corrections: median error 2 m, 55 within 5 m, worst 21 m. In test 2 Maps gaps were ~20–60 s; power saving wasn't logged then, so the cause is unproven.

## Power saving
Battery-optimisation exemption and Power saving are separate Android mechanisms. To my knowledge, exempting Google Maps from battery optimisation does not lift Power saving's GPS limits, so it would not be a substitute.
Whether Samsung's Power saving limits GPS at all is untested; the app now logs it. So "Power saving OFF" is no longer a required checklist item, just a note. Test 4 settles it in 2 minutes.

## Flaky / proposed fixes (not built)
1. **YouTube Music takes the car screen back on each song change** (seen after a forwarded Next). Proposal: re-claim the screen ~1.5 s after each forwarded skip and on music track changes. Waits on session 2.
2. **Distance can tick up by 10–20 m** when Maps corrects a slightly-ahead estimate (09:45:13 280 → 09:45:15 290). Proposal: hold the shown value unless Maps is more than 30 m higher.
3. **Maps' maneuver icons:** 7 labelled so far. Slight turns, U-turns and roundabouts are still unseen and would show `•` until labelled from a log.

## Changes in build 7
- Arrived screen stays until the car moves (> 10 km/h), a new trip starts, double Next, or the car disconnects.
- "Starting navigation…" no longer counts as a real instruction (it wrongly hid the first "towards" line at 09:42:27).
- New destination pin icon labelled (●).
- Power saving: advisory note, plus logging of its GPS mode and of Google Maps' battery-optimisation status.
