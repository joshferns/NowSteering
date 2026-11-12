# Car profile: 2014 Honda City, stock head unit

Measured facts about the head unit, with the test and date they came from. Phone: Samsung Galaxy S20 FE 5G (SM-G781B), Android 13, One UI 5.1.
The head unit appears over Bluetooth as **Honda HFT**. It shows Bluetooth audio track info (title / artist / album) and no album art.

## Layout

Top to bottom: track number at the top (for example `02`), the **big line = title**, then **disc icon = album**, then **person icon = artist**, and the phone name at the bottom. Proportional font.
The row order was confirmed by the glyph test (11 Nov).

## Widths and updates

| Fact | Value | Source |
|---|---|---|
| Title (big line) | 17 characters | Phase 0, 30 Oct (digit + letter ruler) |
| Album and artist | 20 characters | Phase 0 |
| Behaviour past the limit | cut off, **no scrolling** | Phase 0 |
| Very long text | about 120 characters is rejected and the screen shows "No title / No name"; 62 characters is just cut | Phase 0 |
| `Falling - Trevor Dan` (20 mixed characters) | fits on a 20-character line | parked test, 11 Nov |
| Wide letters on line 3 | about 16 (8 M + 8 W) | parked test, 11 Nov |
| `1 hr 59 min 11:59pm` (19), `12 min · 19:42`, `12 min ███▒▒▒` | all fit | parked test, 11 Nov |
| Ten-cell bar, and `1:02:31 left ██▒▒▒▒` | all blocks visible; 18 full blocks fill the line to the right edge (blocks merge) | Phase 0 and 11 Nov |
| Refresh | an in-place metadata-only change lands every time at 5 s and 1 s intervals, with the phone unlocked or locked (wake lock) | Phase 0, Phase 1 |
| Track-change tricks | not needed | Phase 0 |

## Glyphs

| Draws correctly | Draws as a dot or blank |
|---|---|
| ASCII; Latin-1 (`° ± ² ½ © « » · é`); `← → ↑ ↓ ↖ ↗`; `• ● ▲ ▶ ▼`; `─ │ ┌ ┐ └ ┘`; `█ ▓ ▒ ▄`; `♀ ♂ ○` | `░ ▀ ⇐`; Devanagari (Hindi); emoji; `♪ ♫ ♩ ♬ ♭ ♯ ♠ ♣ ♥ ♦ ☼ ◊` |

Notes: the car replaces a glyph it cannot draw with a dot (♪ shows as a small dot), so a dot means "not supported". Not yet checked: `↘ ↙ ◀ ★ ✓ × ⇒ ➜ ⬆`.
Sources: Phase 0 (30 Oct) and the glyph test (11 Nov).

## Buttons and gestures

| Fact | Detail |
|---|---|
| Steering **Next** / **Previous** | arrive as `KEYCODE_MEDIA_NEXT` (87) / `KEYCODE_MEDIA_PREVIOUS` (88), an instant DOWN + UP per press, `eventTime=0` |
| Taps | single, double and triple taps are detectable; gaps between taps measured 250 to 550 ms, so a **600 ms** window is used |
| Holds | **never reach the phone**; the car beeps at about 2 s; no fast-forward or rewind events |
| Head-unit **Pause** | arrives as `KEYCODE_MEDIA_PAUSE` (127) and **never** as PLAY, even when paused: treat it as a toggle |
| SOURCE, volume + / − | do not reach the app |
| Who gets the buttons | the app that most recently started playing audio (see DESIGN); with YouTube playing, presses go straight to YouTube (11 Nov) |

## Other behaviour

- **Car clock is about 1 minute fast**; the app's time comes from the phone.
- **Microphone use** (voice dictation in another app) while connected makes the car show its call screen, which can look stuck. Not caused by Now Steering.
- A real phone call works normally; after it ends the car showed YouTube briefly, then Now Steering returned in about 5 s (1 Nov).
- Maps' arrow is not shown by the car; we draw it with `← → ↑ ↖ ↗`.
- Bluetooth: A2DP audio + AVRCP track info. TODO: AVRCP version (assumed old).
- TODO: what the car shows for the track number we send (we always send track 1 of an hour-long track).
