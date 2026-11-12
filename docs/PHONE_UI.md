# Phone UI

Screenshots are placeholders for you to fill in: put images in `docs/images/` and replace the `TODO` lines.

## Main screen ("Now Steering")

![Main screen](images/main-screen.png) <!-- TODO screenshot -->

Top to bottom:

| Section | Contents | Notes |
|---|---|---|
| Status box (green / red) | `Setup complete`, or `SETUP INCOMPLETE: n missing` with one line per item | Items: Notification access, Car chosen, Battery not optimised, Power saving OFF, GPS all the time, Notifications + Bluetooth. A last `INFO` line shows whether Google Maps is battery-optimised (information only). |
| Status lines | Service ON / OFF (or "stepped aside: call"), Navigating yes / no, what the car shows now, permission summary | Refreshes every second. |
| **Car** | **Choose car** button, "Car: name address", tick box *Start automatically when the car connects* | Needs the Nearby devices permission first. |
| Settings (tick boxes) | see table below | |
| **Start now** / **Stop** | Manual start and stop | Normally unnecessary: the car's connection starts and stops it. |
| **Permissions (one-time)** | buttons 1 to 5 | See README. |
| **Diagnostics** | opens the Diagnostics screen | |

### Settings on the main screen

| Setting | Default | Effect |
|---|---|---|
| Start automatically when the car connects | on | Start and stop the service with the chosen car's Bluetooth connection. |
| Turn bar (last 300 m before a turn) | on | Line 2 becomes an arrow + 10-cell bar in the last 300 m. |
| Trip bar (after the ETA) | on | Line 2 far from a turn shows time left + a 6-cell trip bar. Off: time left only. |
| Arrival time on line 2 (replaces the trip bar) | off | Line 2 far from a turn reads `12 min · 19:42` instead. |
| Keep Now Steering on screen (take the car back when another app plays) | off | Re-claims the car when another app starts or changes; shows the mirror screen when not navigating. |

## Diagnostics

![Diagnostics](images/diagnostics.png) <!-- TODO screenshot -->

| Section | Contents |
|---|---|
| Blue box + status | The last button gesture (for example `NEXT single`, with the time) and the service mode. |
| Maps parse | What was read from the current Maps notification: arrow and where it came from, distance, road, ETA, distance left, icon id. |
| Text tester | Three text fields, top to bottom *Title (big line, 17)*, *Album (disc icon, 20)*, *Artist (person icon, 20)*, each with a full-width **preset** dropdown below it. **Show on car** sends them (test limits, experimental glyphs let through), **Back to live**, **Re-grab** (restart the silent audio now). |
| Car test screens (one tap each) | ♪ glyph, Glyph candidates, Song widths, Line 2 text, Bars, Mirror preview, and **Back to live**. |
| Song on the nav screen | Radio list: **▶ (play, default)**, none, •, ♪ (shows as a dot on this car). |
| Car refresh (phone-locked test) | Tick *Seconds on idle clock* (default off); radio list of refresh styles: **metadata only (default)**, metadata + playback-position bump, fake track change. |
| Buttons | *Next tap window ms* (default 600) and **Apply**. |
| Active media sessions | Every app with a media session, in Android's priority order, with state and title; `(US)` is us, `(TARGET)` is where buttons are forwarded. |
| Event log | A note field with **Add note**; **Share key lines** (file), **Share text**, **Share file**, **Copy**, **Clear**; the last 200 log lines. |

Share buttons: *Share key lines* and *Share file* send a `.txt` file; *Share text* sends text up to 60 000 characters and a file above that; *Copy* copies the last 60 000 characters.
If sharing fails a dialog says why.

### Torque section (0.4.0)

Diagnostics, between "Active media sessions" and "Buttons". Lines: Torque installed, Plugin service bound, Torque version (plugin API number) and full-permission flag, OBD connected, optional Status line (not installed / waiting for Torque / not connected to the car), then one line per PID: `AE name: value unit` (A = active in Torque, E = ECU supports it). Button **Refresh PID list**. TODO: screenshot `docs/images/diagnostics-torque.png`.

## Notification ("Now Steering is on")

![Notification](images/notification.png) <!-- TODO screenshot -->

- Title *Now Steering is on*, text *Showing nav / clock on the car screen*; ongoing, low importance (no sound).
- Tap: opens the app.
- Action **Show on car**: re-claims the car screen and buttons at once (use it when another app, for example the regular YouTube app, has taken the screen).
