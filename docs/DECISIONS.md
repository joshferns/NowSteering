# Decision log

Newest last within each group. Dates are when the decision was taken (local). "Why" is the evidence at the time.

## How the car is driven

**D1. Play near-silent audio in a MediaSession, request no audio focus** (30 Oct)
- *Why:* the car shows the session Android treats as the active player, so we must be a playing player. With audio focus YouTube Music pauses; without it, music keeps playing and only our text is on the car.
- *Alternatives:* no audio at all (Android does not rank a silent session as playing); taking focus (stops the music, unacceptable); a notification-only approach (the stereo never sees it).

**D2. Update text metadata-only, in place, at most once per second, only when it changed** (30 Oct)
- *Why:* Phase 0 showed the Honda refreshes every metadata change at 1 s without a fake track change.
- *Alternatives:* fake track change per update, pause / play around each update (kept as Diagnostics experiments, not needed).

**D3. Send only glyphs the car draws** (30 Oct)
- *Why:* a 120-character string blanked the screen and unsupported glyphs show as dots or blanks. Confirmed set in CAR_PROFILE; everything else falls back to ASCII.

**D4. Take the car back by restarting our silent audio ("re-grab")** (used since 30 Oct; the reason was verified in Android's source on 8 Nov)
- *Why:* Android 13's source shows media buttons and the Bluetooth stack's active player both follow the app that most recently started playing audio.
- *Alternatives:* toggling playback state (does not change who last started a player).

## Navigation

**D5. Parse Google Maps' notification instead of integrating with Maps** (30 Oct)
- *Why:* reuse the existing app; Maps has no public turn-by-turn API. Same technique as open-source watch integrations.
- *Alternatives:* Waze / OsmAnd (out of scope), our own routing (no).

**D6. Read the turn arrow from the notification icon, labelled from real drives** (1 Nov)
- *Why:* Maps gives the arrow only as a picture. Each new icon is logged once as ASCII art, labelled by hand, matched by pixel difference. Unknown icons show `•` rather than a guess ("never wrong").

**D7. Dead reckoning between Maps updates, but never show less than Maps** (1 to 2 Nov)
- *Why:* with the phone locked Maps updates only about every 20 s. First version ignored upward corrections and ran 16 to 25 m ahead (showed `0 m` at 20 m); now Maps' value is shown while Maps updates, GPS counts only in gaps, a repeated higher value pauses us, and displayed numbers round up. After 90 s without Maps we hold the last value (7 Nov log).

**D8. Arrived is action-based, not timed** (1 Nov)
- *Why:* a 10 s screen was easy to miss. It stays until the car moves off, a new trip starts, double Next or disconnect, with a GPS-free fallback (phone location 300 m away) because Power saving blocks GPS.

**D9. Show `Rerouting...` instead of holding the old instruction during a reroute** (2 Nov, PM)
- *Why:* holding an old turn during a real reroute can be wrong. Blank / no-distance notifications are still held, because they are Maps' in-between states.

**D10. Arrival clock replaces the trip bar on line 2 (setting, off by default)** (8 Nov)
- *Why:* both do not fit with a readable bar (3 cells would be needed). *Alternatives:* alternate every few seconds (extra updates, flicker); always show the clock (not asked for).

**D11. Song line on the nav screen, far from the turn only; road near the turn** (8 Nov, PM)
- *Why:* far from a turn the road name changes rarely; near a turn it is the useful text. Browser media sessions are not songs.

## Phone behaviour

**D12. Power saving: warn on the car and require it OFF, no workaround** (1 to 2 Nov)
- *Why:* Samsung Power saving turns GPS off with the screen off. Exempting Maps or Now Steering from *battery optimisation* is a different setting and does not lift it (and Maps' "Unrestricted" was read as optimised anyway). A warning on the car is honest; silently degrading is not.
- *Alternatives:* a Modes and Routines automation that turns Power saving off when the car connects (parked for before any Play Store work).

**D13. Call safeguard: step aside completely, come back 3 s after the call ends** (30 Oct, PM)
- *Why:* the probe once left the car on a call display. Detected through `AudioManager.mode`, no phone-state permission.

**D14. Releases named by version number, not build number** (8 Nov, PM)
- *Why:* easier to talk about. A one-line `VERSION` file drives the app, the APK and the release tag.

## Music and buttons

**D15. Re-claim after song changes only while navigating, or with "Keep on screen"** (1 Nov, PM)
- *Why:* when not navigating the music app's own info is wanted; the regular YouTube app's video titles made that annoying, so "Keep on screen" and "Show on car" give a way back without changing the default.

**D16. Triple-tap Next (re-claim) built, then removed** (8 Nov built, 11 Nov removed)
- *Why removed:* buttons follow the last-started player, so with YouTube playing the presses never reach us, exactly when the gesture would be needed (11 Nov log: no `[KEY]` line for a single Next).

**D17. Drop, do not delay, Next / Previous pressed while the app is loading or in ERROR** (11 Nov)
- *Why:* forwarding a skip into a loading regular-YouTube player made it show "An error occurred" (08:47 and 08:49 on 11 Nov). A missed press is harmless; a delayed skip is a wrong action. Also: nothing forwarded to an app in ERROR, and a lone UP never acts.

**D18. ▶ as the song prefix, no music glyph** (11 Nov, PM)
- *Why:* the car draws ♪ ♫ ♩ ♬ ♭ ♯ ♠ ♣ ♥ ♦ ☼ ◊ as dots (glyph test, 11 Nov). ▶ is proven. A saved • or ♪ was switched to ▶ once on upgrade.

## Tooling and process

**D19. Sign every debug build with one committed key** (30 Oct)
- *Why:* builds install over each other without uninstalling and keep the log. Acceptable for a personal debug app; not for a store release.

**D20. Probe session 2 dropped; open music questions tested inside Now Steering** (1 Nov, PM)
- *Why:* the real app answers them directly.

**D21. Share key lines as a file** (12 Nov)
- *Why:* a text extra over about 100 KB overflows Android's share call and fails silently; a file works at any size. Sharing failures now show a dialog.

**D22. Phase 2b principle: never talk to the OBD adapter; only read values from Torque Pro** (12 Nov, PM)
- *Why:* Torque owns the ELM327 connection and already uploads to the PM's Home Assistant. Reading its values is read-only; show nothing rather than a stale number.

**D23. Torque probe binds without auto-create and only reads** (12 Nov)
- *Why:* Now Steering must never start Torque or change how it talks to the adapter; it attaches when Torque is already running and only calls read methods.

**D24. Redesign as a customisable, Home Assistant-style system; C.2 paused** (13 Nov, PM)
- *Why:* car data is one of many things people may want on three lines. Fields + faces + rules + a car profile let screens become data (and later shareable files) instead of code. Phase 1 changes nothing visible so the structure can be proven first.

**D25. Prove "no change" with a golden test against the old code** (13 Nov)
- *Why:* the v0.4.0 screen code stays in the tests (`LegacyScreens`) and is run side by side with the new pipeline. This caught a swapped idle row during the rebuild. Remove it with the old `Layouts` helpers once v2 has run in the car for a while.

**D26. Fields are read lazily; missing parts are dropped, never dashed** (13 Nov, PM)
- *Why:* laziness keeps the song hold and turn-zone hysteresis identical to 0.4.0; dropping a part (with its unit) avoids "-- km/h" style noise. The Current preset keeps `--` for a missing speed on purpose, via `{speed|--}`.

**D27. Keep the Power saving notice's line order until Phase 2; keep `LegacyScreens` for about a week of drives** (13 Nov, PM)
- *Why:* Phase 1 must stay identical to 0.4.0; the notice's reversed rows (`turn it off` on line 2) get flipped in Phase 2. The old-code reference and golden test stay until v2 has had about a week in the car.
