# Phase 0 report — session 1 (2026-10-30, Honda City "Honda HFT", S20 FE / Android 13)

**Q1 Character set — High confidence.**
OK: ASCII, Latin-1, ← → ↑ ↓ ↖ ↗, ° • ● ▲ ▶ ▼, ─ │ ┌ ┐ └ ┘, █ ▓ ▒ ▄.
Fail (dot/blank): ░ ▀ ⇐, Devanagari, emoji. Not yet checked: ↘ ↙ ◀ ★ ✓ × ⇒ ➜ ⬆.
Font is proportional, so box-drawing will not line up as a grid.
→ Use plain ASCII plus the confirmed arrows and symbols. No Hindi, no emoji.

**Q2 Width — High confidence (measured with a digits+letters ruler).**
Title 17 chars, artist 20, album 20. No scrolling (cut off). A 120-char string is
rejected entirely ("No title / No name"); 62 chars is cut off, not rejected.
The car also shows the track number ("02") top-left: a possible 2-digit extra field.
→ Budget ~15 title / ~18 artist+album to allow for wide letters (W, M); truncate on the phone.

**Q3 Refresh — High confidence.**
A metadata-only update (same track, no fake track change) landed every time at 5 s and 1 s intervals.
→ The real app can update text in place at up to 1 Hz with no track-change tricks.

**Q4 Buttons — High confidence.**
- Steering Next / Previous reach the app as KEYCODE_MEDIA_NEXT (87) / PREVIOUS (88).
  Each press arrives as DOWN+UP within ~10–75 ms, whatever the actual press length.
- Holds never reach the app (the car beeps and keeps them). No fast-forward or rewind events.
- Single, double and triple taps all detected. Measured gaps between taps were 250–550 ms, so a 400 ms
  window split some triples. → Use a ~600 ms tap window.
- The head-unit play/pause button always sends KEYCODE_MEDIA_PAUSE (127), never PLAY, even after
  our session went PAUSED (most likely because our silent audio keeps the Bluetooth stream active).
  → Treat PAUSE as a third button/toggle and never depend on a PLAY event.
- SOURCE and volume: nothing reached the app (as expected).
→ Input model: Next, Prev, Pause × single/double/triple tap. No long-press. Each single tap reacts
  ~600 ms late because we wait to rule out a double.

**Q5 Music coexistence — PARTIAL. Medium confidence on what is proven.**
- Proven (one trial, 19:10:56): with audio focus *not* requested, our session went to the top while
  YouTube Music was still playing. The car showed our text and YTM stayed audible.
- Proven: we can read YTM's title / artist / album live.
- Big open risk: when YTM changed song on its own (19:11:19), Android moved YTM back to the top.
  Most likely the car then showed YTM's text again (screen not observed). The real app would then
  need an automatic re-grab after each YTM song change.
- Not tested yet: forwarding steering Next/Prev to YTM, Re-grab, the audio quality impact ("slightly
  muddled", not verified A/B).
- Incident: after a phone call (19:09:42–19:10:01; Android shows the call ended cleanly) the car stayed
  stuck on an active-call display and the steering controls stopped working, even after a car restart.
  Cause unknown. The probe never touches the call (HFP) side, but its silent audio was playing
  during the call. Session 2 repeats the call with the probe off, then on.

**Probe fixes made for session 2:** (1) START now warns if the session is already running. In session 1
a START at 19:09:23 did nothing, so the "no audio focus" setting only took effect at 19:10:42.
(2) The car's key events have no timestamps (eventTime=0), so the "no KeyEvent" labels in the log
were wrong. Timing is now measured on arrival. The session-1 conclusions above use the log's own
clock and are unaffected.

**What it means for the real app:** the text dashboard is viable: 17/20 chars, a safe glyph set,
1 Hz in-place updates, and 3 buttons with tap counting. Whether it can run alongside YouTube Music
depends on session 2: does our text survive YTM song changes (or can an automatic re-grab keep it),
does forwarding work, and is the stuck-call issue linked to the probe.
