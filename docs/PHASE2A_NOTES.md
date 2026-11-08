# Phase 2a: decisions (v0.3.0)

Which line is which on the car, top to bottom: **line 1** = big line (title field), **line 2** = disc icon (album field),
**line 3** = person icon (artist field). The `[SCREEN]` log lines now list them in that order (before, artist came before album).

## Navigating
| | Far from a turn | Last ~300 m |
|---|---|---|
| Line 1 | `← 750 m` (unchanged) | unchanged |
| Line 2 | `12 min ███▒▒▒`: Maps' own time left + 6-cell trip bar | turn bar `← █████▒▒▒▒▒` |
| Line 3 | the song: `Falling - Trevor` (else the road) | Maps' next-turn text (road) |

- **Clock arrival time: a setting, off by default** (main screen, "Arrival time on line 2"). When on, it *replaces* the trip bar:
  `12 min · 19:42` (`1 hr 5 min 7:42pm` for long 12-hour cases). A bar and a clock together would need a 3-cell bar, which is too coarse to read.
  The parked "Line 2 text" check shows whether the longest case (19 characters) fits.
- **Song line:** `Title - Artist`, cut at a word to 20 characters (a hard cut only if the word boundary would lose 40% or more).
  Only a PLAYING or BUFFERING app counts; browsers ("Stories • Instagram") are ignored; a title that is empty after dropping
  unsupported characters (Hindi, emoji) falls back to the road; held for 4 s between songs so it does not flick to the road and back.
- **Song prefix:** none until ♪ is seen working on the car. Diagnostics → "Song on the nav screen" chooses none / • / ♪.
  Fallback is • or nothing, as briefed. ♪ is never sent to the car unless you choose it.
- **Near-turn switch** has hysteresis: enters at 300 m, leaves only above 340 m or on a new instruction, so a wobbling distance can't flip it.
- **Song progress bar on the nav screen: not built** (optional; no line left for it). It is on the music-mirror screen below.

## Not navigating
- **Default (Keep off): unchanged.** Idle clock; a playing app's own song info stays on the car.
- **(b) "Show on car"** button in the "Now Steering is on" notification: re-claims the car at once.
- **(c) "Keep Now Steering on screen"** (main screen, off by default). Another app starting to play (or changing song) is re-claimed after 1.5 s,
  at most once per 2 s and 4 per 30 s. The screen while it plays:
  ```
  17:03  32 km/h
  ▶ <title>
  2:31 left ███▒▒▒        (the artist instead, for live streams)
  ```
  Pause or nothing playing: back to the normal idle screen.
- **(a) Triple-tap Next = re-claim:** built. It only works when the button reaches us (see 5d). The log decides whether it stays.

## 5d: did the double-tapped Next reach us? No: it went straight to the music/video app.
Checked against Android 13's own source (not guessed):
1. `MediaSessionStack`: *"We send the media button events to the lastly played app."* The session that gets Next / Previous / Pause is the one
   whose app most recently **started an audio player** that is still playing.
2. The Bluetooth stack (`MediaPlayerList`) sets the car's "active player" from that same session. So the car's screen and the wheel buttons
   always follow the same app.
3. Our silent audio started first; the moment YouTube starts a video, it is the "lastly played app". Both taps of a double-tap went to YouTube, which skipped twice.
   Our re-grab (restart our silent audio) is what puts us back in front: that is why buttons worked during navigation, where we re-grab at every instruction.
4. In your 7 Nov log the only button event is one lone `KEYCODE_MEDIA_PAUSE UP` at 19:57:22.322, 23 ms after YouTube Music itself
   paused (19:57:22.299). Its DOWN evidently went to YouTube Music (it paused at that moment); only the UP reached us, once YouTube Music stopped and our silent audio was the
   newest player again. The 5 Nov presses are not in the pasted log, so this is the source plus that one event, not a measured double-tap.

Consequences: the double-tap detection (600 ms) is fine, nothing to fix. **(a) cannot work while the other app plays**, which is exactly when it
would be needed; it works after "Show on car" or with Keep on (we hold the buttons then). Proposal: drop (a) if the YouTube drive confirms it; (b) and (c) stay.

## Also changed
- **Version numbers:** `VERSION` file (now 0.3.0) names the release (`v0.3.0`), the APK and the log header. Bump it for every release.
- **Dead reckoning (found in your 7 Nov log):** after 90 s with no Maps update the distance jumped back up to Maps' old value
  (16:50:44, bar went backwards). It now holds the last shown value.
- **Diagnostics:** one-tap "Car test screens", the test limits are the car's measured 17 / 20 / 20 with ♪ ♫ let through, and
  **Share key lines** exports only the lines needed for the YouTube drive.
