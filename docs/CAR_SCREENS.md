# Car screens

Every screen the app can put on the Honda. The car shows three text lines (plus its own track number at the top and the phone name at the bottom):

| Row | Android field | Icon on the car | Limit we use | What the car shows |
|---|---|---|---|---|
| **Line 1** (big) | title | none | 15 | 17 characters |
| **Line 2** | album | disc | 18 (20 for time / song lines) | 20 |
| **Line 3** | artist | person | 18 (20 for the song line) | 20 |

The row order (title, album, artist from top to bottom) was confirmed by the 11 Nov glyph test; older screens (clock, notices, Arrived) are placed by the same mapping.
TODO: add a photo of each screen to confirm every row.

In the mock-ups, `[ ]` marks a line that is empty. Fonts are proportional, so lengths are in characters, not columns.

## 0. Presets (0.6.0): which screens you get

The sections below describe the screens of the **Classic** preset (exactly the 0.5.0 screens). Since 0.6.0 the default is **Driver**. A preset changes the faces, not the priority order, which is the same for all: Arrived, Rerouting, near a turn (enter 300 m, leave 340 m), navigating, music (Keep on screen), idle. Double Next cycles Driver → Trip → Car (only when Torque is installed) → Classic and shows the name on line 1 for 2 s. Line 1 in Driver, Trip and Car may use the car's full 17 characters; lines 2 and 3 use 20.

A part of a line that needs a missing value is dropped (no dashes); a value older than its limit counts as missing. Mock-ups below are the preview in the app (sample values: 42 km/h, 17:03, a song with 2:31 left, a turn in 2.4 km, Torque 84 °C, 14.2 V, 14.6 km/L, fuel Rs 100/L).

### Driver (default)

**1  17:03 42 km/h**

```
    L2  Sat 14 Nov
    L3  Now Steering
```

**Music playing (Keep on screen)**

```
    L1  42 km/h 17:03
    L2  ▶ Falling - Trevor
    L3  2:31 left ██▒▒▒▒
```

**Navigating, music playing**

```
    L1  ↗ 2.4 km 42 km/h
    L2  ▶ Falling - Trevor
    L3  2:31 left ██▒▒▒▒
```

**Navigating, no music**

```
    L1  ↗ 2.4 km 42 km/h
    L2  23 min ▒▒▒▒▒▒
    L3  Outer Ring Rd
```

**Last 300 m before a turn**

```
    L1  ↗ 250 m
    L2  ↗ ██▒▒▒▒▒▒▒▒
    L3  42 km/h ▶ 2:31
```

**Rerouting**

```
    L1  • Rerouting...
    L2  [ ]
    L3  [ ]
```

**Arrived**

```
    L1  ● Arrived
    L2  18:05
    L3  Hosur Rd
```

**Coolant warning on idle**

```
    L1  ▲ HOT 107°C
    L2  Sat 14 Nov
    L3  Now Steering
```

Line 1 far from a turn tries `↗ 2.4 km 42 km/h`, then drops ` km/h`, then the speed (`← 12.5 km 120`), so it always fits 17.

### Trip

Only the lines that differ from Driver:

```
Navigating (far)
    L1  ↗ 2.4 km
    L2  23 min ▒▒▒▒▒▒
    L3  Rs 125 trip · 48 now      (or "Rs ~125 trip · ~48 now" when that fits 20, or "Rs ~125 trip", or the road if no fuel price is set)
```

### Car

Idle line 2 and music line 3 show Torque's numbers (km/L is dropped first, then voltage; the date or time-left bar when Torque has nothing fresh):

```
Idle                              Music playing
    L1  17:03 42 km/h                 L1  42 km/h 17:03
    L2  84°C 14.2V 14.6km/L           L2  ▶ Falling - Trevor
    L3  Now Steering                  L3  84°C 14.2V 14.6km/L
```

### Warnings (all presets)

Coolant above 105 °C for 5 s: `▲ HOT 107°C`. Voltage below 12.2 V for 10 s with the engine running: `▲ LOW 12.0V`. It replaces line 1 on idle and music screens and line 2 on navigation screens; it is held in the last 300 m before a turn and shown right after. 10 s on, 50 s off while it lasts; at once if worse by 3 °C / 0.3 V; both active alternate.

```
Idle with a warning                   Navigating with a warning
    L1  ▲ HOT 107°C                       L1  ↗ 2.4 km 42 km/h
    L2  Sat 14 Nov                        L2  ▲ HOT 107°C
    L3  Now Steering                      L3  Outer Ring Rd
```

## 1. Idle: clock and speed

Shown when not navigating (and Keep on screen is off, or nothing is playing).

```
L1  17:03 32 km/h          clock + GPS speed ("--" without a recent fix)
L2  Now Steering
L3  Thu 12 Nov
```
(The code writes two spaces after the clock; the engine collapses them.) With *Seconds on idle clock* (Diagnostics): `17:03:21 32km/h`.

## 2. Keep-on mirror (setting "Keep Now Steering on screen")

Shown instead of 1 while another app is **playing** (PLAYING or BUFFERING). Back to 1 when paused or nothing plays.

```
L1  17:03 32 km/h
L2  ▶ Falling
L3  2:31 left ███▒▒▒       or the artist for live streams (no length)
```
Browser tabs are never shown as a song. A title that is unreadable once unsupported characters are dropped is not shown. A missing title is held for 4 s between songs.

## 3. Navigation, far from the turn (more than 300 m)

```
L1  ← 750 m                arrow + distance to the turn (counts down, see DESIGN)
L2  12 min ███▒▒▒          Maps' time left + 6-cell trip bar
L3  ▶ Falling - Trevor     the playing song, cut at a word to 20; else the road: 13th Main Rd
```
- Arrow: `↑ ← → ↖ ↗` from Maps' icon; `●` when the destination is ahead; `•` for an icon not yet labelled.
- Setting *Arrival time on line 2*: L2 becomes `12 min · 19:42` (no bar).
- Setting *Trip bar* off: L2 is the time only. Song prefix (Diagnostics): `▶` (default), none, `•` or `♪` (♪ draws as a dot).

## 4. Navigation, near the turn (last 300 m)

Enters at 300 m, leaves above 340 m or on a new instruction.

```
L1  ← 230 m
L2  ← ██▒▒▒▒▒▒▒▒           arrow + 10-cell bar that fills as the turn approaches
L3  13th Main Rd           Maps' road text (the song is hidden here)
```
With the *Turn bar* setting off, L2 stays as in screen 3 but L3 still shows the road.

## 5. Rerouting

```
L1  • Rerouting...
L2  [ ]
L3  [ ]
```
Until Maps sends the next real instruction.

## 6. Arrived

```
L1  ● Arrived
L2  11:12                  clock time of arrival
L3  Work                   destination name
```
Appears when Maps shows its destination marker with ≤ 30 m left, or Maps ends navigation with ≤ 150 m left.
Stays until: the car stops and then exceeds 10 km/h, or the phone's location moves 300 m (works without GPS), or a new trip starts, or double Next, or 20 min, or the car disconnects.

## 7. Notices (timed overlays, highest priority)

(0.6.0: the Power saving notice now reads `Power saving on` / `nav less smooth` / `turn it off`, top to bottom; the table below is the 0.5.0 order. A 2 s notice shows the preset name on line 1 when you double-tap Next.)

| Screen | L1 | L2 | L3 | For | When |
|---|---|---|---|---|---|
| Power saving | `Power saving on` | `turn it off` | `nav less smooth` | 6 s | service start, navigation start, Power saving switched on |
| Navigation ended early | `Nav ended` | [ ] | [ ] | 4 s | Maps ends with more than 150 m left |
| No music app | `No music app` | [ ] | `Start music first` | 3 s | Next / Previous / Pause pressed with nothing to forward to |
| No notification access | `No music link` | `in Now Steering app` | `Grant notif access` | 3 s | same, but Notification access is off |

## 8. Test screens (Diagnostics only)

Shown until *Back to live*; limits 17 / 20 / 20; experimental glyphs let through.

| Button | L1 | L2 | L3 |
|---|---|---|---|
| ♪ glyph | `♪ ♫ • ▶ ♪` | `♪ Falling` | `♪ Falling - Trevor` |
| Glyph candidates | `♩ ♬ ♭ ♯ ♠` | `♣ ♥ ♦ ☼ ♀` | `♂ « » ○ ◊` |
| Song widths | `Song 20 chars` | `Falling - Trevor Dan` | `MWMWMWMWMWMWMWMWMWMW` |
| Line 2 text | `Line 2 text` | `1 hr 59 min 11:59pm` | `12 min · 19:42` |
| Bars | `Bars` | `12 min ███▒▒▒` | `1:02:31 left ██▒▒▒▒` |
| Mirror preview | `17:03 32 km/h` | `▶ Falling` | `2:31 left ███▒▒▒` |

## Priority: which screen wins

```mermaid
flowchart TD
    A{Call in progress?} -- yes --> Z[Nothing sent: stepped aside]
    A -- no --> B{Notice active?}
    B -- yes --> N[7. Notice]
    B -- no --> C{Test screen on?}
    C -- yes --> T[8. Test screen]
    C -- no --> D{Arrived?}
    D -- yes --> R[6. Arrived]
    D -- no --> E{Navigating and not switched to the clock?}
    E -- yes --> F{Rerouting?}
    F -- yes --> G[5. Rerouting]
    F -- no --> H{300 m or less to the turn?}
    H -- yes --> I[4. Near the turn]
    H -- no --> J[3. Far from the turn]
    E -- no --> K{Keep on and an app is playing?}
    K -- yes --> L[2. Mirror]
    K -- no --> M[1. Idle clock]
```

## How the screens change

```mermaid
stateDiagram-v2
    [*] --> Idle: car connects, service starts
    Idle --> Mirror: Keep on + an app is playing
    Mirror --> Idle: paused / nothing playing
    Idle --> NavFar: Maps navigation starts
    NavFar --> NavNear: 300 m or less to the turn
    NavNear --> NavFar: next instruction (turn done)
    NavFar --> Rerouting: Maps "Rerouting…"
    NavNear --> Rerouting
    Rerouting --> NavFar: next real instruction
    NavFar --> Arrived: destination marker at 30 m or less
    NavNear --> Arrived
    NavFar --> Idle: Maps ends early (Nav ended, 4 s)
    NavFar --> NavAsClock: double Next
    NavAsClock --> NavFar: double Next
    Arrived --> Idle: moves off / 300 m away / double Next / new trip / 20 min
    Idle --> [*]: car disconnects (service stops)
    note right of Idle
        Any state: a call stops all text
        and returns 3 s after it ends.
    end note
```
*NavAsClock* = navigating, but the idle screen is shown on request.

## What the buttons do in each state

Buttons reach the app only while it holds the car (during navigation, after *Show on car*, with *Keep on screen*, or when the other app is paused).
Otherwise they go straight to the music app and do whatever that app does.

| State | Next ×1 | Next ×2 | Next ×3+ | Previous | Head-unit Pause |
|---|---|---|---|---|---|
| Idle / Mirror | next song (forwarded) | nothing (logged "staying on idle") | ignored | previous song (forwarded) | pause / resume the playing app |
| Nav (all) | next song | switch to the clock screen | ignored | previous song | pause / resume |
| Nav as clock | next song | back to the nav screen | ignored | previous song | pause / resume |
| Arrived | next song | clears Arrived (clock) | ignored | previous song | pause / resume |
| Test screen | next song | ignored | ignored | previous song | pause / resume |

Forwarded Next / Previous are dropped while the music app is loading, in ERROR, or within 2 s of a track change (see DESIGN). The first Previous restarts the current song and the second goes back one (that is the music app's behaviour).
Long presses never reach the app: the car beeps at about 2 s and keeps them.
