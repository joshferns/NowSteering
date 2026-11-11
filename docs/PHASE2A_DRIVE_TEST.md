# Phase 2a drive test (v0.3.2, two short drives plus a short fix check)

Rules: the driver only glances. Never touch the phone while driving. No voice dictation. No photos while driving.
Write notes when you stop. Do the parked test first.

## Drive 1: navigating (new layout)
**Parked**
1. Start YouTube Music. Play a song.
2. Start Google Maps navigation to a place about 3 km away with at least 3 turns. Pick it from saved places.
3. Lock the phone.

**Driving, glance only** (note each when you stop)
4. Far from a turn, glance at the car. Note: line 2 `12 min` + bar? Line 3 the song?
5. Glance at about 300 m before a turn. Note: line 2 turned into a bar with the arrow? Line 3 the road?
6. Glance just after the turn. Note: back to time left and the song?
7. *(Removed in 0.3.1: triple-tap Next no longer exists.)*
8. At a safe moment, press steering **Next** once. Note: song changed? Nav screen back within about 3 s?

**Parked**
9. Note: did anything look wrong, stale or stuck?

## Drive 2: regular YouTube, not navigating (answers 5d and (a))
**Leg A, Keep off**
10. Parked: Now Steering → make sure **Keep Now Steering on screen** is unticked.
11. Parked: open YouTube. Start a **playlist** (so Next has a next).
12. Parked: Diagnostics → type `A start` → **Add note**.
13. Drive off.
14. Press steering **Next** once. Wait 10 s.
15. Press steering **Next** twice, fast. Wait 10 s. (Keep on: both presses reach us, so this switches nav/idle and skips nothing.)
16. *(Removed in 0.3.1.)*
17. Glance at the car. Note: YouTube's title or Now Steering?
18. Park. Diagnostics → type `A end: Next x1, x2, x3, 10 s apart` → **Add note**.

**Leg B, Keep on**
19. Parked: Now Steering → tick **Keep Now Steering on screen**.
20. Parked: Diagnostics → type `B start` → **Add note**.
21. Drive off. Repeat steps 14 to 17.
22. Park. Diagnostics → type `B end: Next x1, x2, x3, 10 s apart` → **Add note**.

**Send**
23. Diagnostics → **Share key lines**. Send it with your notes.

## Fix check, new in 0.3.1 (about 3 min, Keep off)
Why: with Keep off, the second press of a quick pair reached us while YouTube was loading, we forwarded a skip, and YouTube showed "An error occurred".
Now such a press is dropped.
24. Parked: Now Steering → make sure **Keep Now Steering on screen** is unticked.
25. Parked: open YouTube. Start a **playlist**.
26. Parked: Diagnostics → type `Fix check start` → **Add note**.
27. Drive off. Press steering **Next** twice, fast. Wait 10 s.
28. Repeat step 27 two more times.
29. Glance at the phone only when stopped. Note: did YouTube ever show "An error occurred"?
30. Park. Diagnostics → type `Fix check end` → **Add note**.
31. Diagnostics → **Share key lines**. Send it with your notes.

Expected: no error. `[FWD] next DROPPED …` lines are normal and correct (a press that arrived while YouTube was loading).

## Lines to paste back from the YouTube drive
**Share key lines** gives exactly these, nothing else:
- `[KEY]` and `[GESTURE]`: a button that reached us. No line = it went to YouTube.
- `[FWD]`: a press we forwarded to the player and what it did 1.5 s later, or `DROPPED` with the reason.
- `[CLAIM]`: another app took the car, and our re-claim (with "last button to us … ago").
- `[SESSION] re-grab` and `[SESSIONS]`: who is playing and which app is first.
- `[SCREEN]` lines with `▶`: the mirror screens we sent in Leg B.
- your `[NOTE]` lines, which mark the legs.

```
4: …  5: …  6: …  7: …  8: …  9: …
17A: …  17B: …  29: …
```
