# In-car test script (Phase 0, ~17 min, parked)

**Before you go (at home, ~3 min):** install the latest APK and do the one-time
setup in the README. Check the status line shows `listener=true battExempt=true`.
Make sure YouTube Music has something queued. In the probe app tap **Clear** on
the log.

**In the car:** engine on or ACC, phone on a mount or in hand, car screen on
Bluetooth audio. Take a photo of the car screen whenever it says 📷.
Between steps, type the step number in the *Add note* box (e.g. `step 4`) and
tap **Add note** — this lines the log up with your photos.

---

### Setup and basics (2 min)
1. Connect the phone to the car over Bluetooth. Open NS Probe.
   Note: does the log show `[BT] ACL connected '<car name>'` and `A2DP ... CONNECTED`?
2. Section 1: leave **Request audio focus** ticked. Tap **START session**.
   Look at the car: does it show `Now Steering` / `Phase 0 probe` / `NS`? 📷
   Note which line on the car shows which field (title, artist, album) and anything else shown (track number, time).

### Width (2 min)
3. In **Title**, **Artist** and **Album**, pick preset **Ruler 62** in each, tap **Send ALL three fields**.
   Watch for 10 s: is text cut off, or does it scroll? 📷 (if it scrolls, take a short video).
   Note per field the **last character visible** (e.g. `F` → 16 chars; `0`=1, `9`=10, `A`=11, `Z`=36, `a`=37).
4. Same with preset **Ruler 120** in all three → **Send ALL**. 📷
   Note the last number you see (`...20|` means 20 characters).

### Character set (3 min) — one photo per round
5. Title = **Unicode arrows**, Artist = **ASCII arrows**, Album = **Symbols** → **Send ALL**. 📷
6. Title = **Box drawing**, Artist = **Blocks**, Album = **Latin-1 / Win-1252** → **Send ALL**. 📷
7. Title = **Devanagari (Hindi)**, Artist = **Emoji**, Album = **ASCII punctuation** → **Send ALL**. 📷
   Each item starts with a letter (A, B, C…) so you can note e.g. "arrows: A–D ok, E–J boxes".

### Refresh (4 min)
8. Section 2: select **a) metadata only**. Section 4: seconds `5`, **Start auto**.
   Watch the car title for 30 s. Note: does `#n` advance? Every time, or only sometimes? How long between phone and car? Then **Stop auto**.
9. Select **b) fake track change**. **Start auto** (5 s), watch 30 s, note the same. **Stop auto**.
10. Select **c) pause → update → play**. **Start auto** (5 s), watch 30 s, note the same. **Stop auto**.
11. Pick the best mode from 8–10, set seconds to `1`, **Start auto**, watch 15 s: does the car keep up? **Stop auto**.

### Buttons and gestures (4 min)
Session still running. The **green box** shows the last raw key (`NEXT DOWN r0`, `NEXT UP r0`…);
the **blue box** shows the recognised gesture (`NEXT SINGLE`, `NEXT DOUBLE`, `NEXT LONG HOLD`…).
For each step: do the gesture, wait 2 s, note what the **blue box** says (and "nothing" if neither box changed).
Add a note with the step number before each one (e.g. `12c`) so the log lines up.

12. Steering wheel **Next**:
    a) single tap · b) double tap · c) triple tap (fast, like a double-click) ·
    d) hold 2 s, then release · e) hold 5 s, then release (watch if the car itself starts fast-forwarding)
13. Steering wheel **Previous**: same five, a) to e).
14. Head unit **play/pause** button: a) tap once (pause) · b) tap again (play) · c) hold 2 s.
15. Head unit's own **next / previous (seek/track)** buttons: a) tap each once · b) hold each 2 s.
16. Steering **SOURCE**, **Volume +**, **Volume −**: one press each. These probably won't reach the app; just note if either box changes.
17. Only if double or triple showed up as `SINGLE` in 12–13: in section 4b set *tap window ms* to `700`, **Apply timings**, and redo 12b and 12c.

### Coexistence with YouTube Music (3 min)
18. Tap **STOP session**. Open YouTube Music and play a song. Note: car shows the YTM song? 📷
19. Back in NS Probe: **untick** Request audio focus; tick **Forward next/prev/play/pause**; select mode **b**.
    Tap **START session**. Note: is YTM music **still audible**? What does the car show — `Now Steering` or the YTM song? 📷
    Look at *Active sessions* in section 5 and note which app is listed first.
20. Press steering **Next**, then **Previous**, then the head-unit **play/pause** twice. Note: did YouTube Music skip / pause / resume?
21. Tick **Mirror target's title/artist/album**. Note: does the car now show the YTM song through our session? 📷
    In YouTube Music on the phone, skip to the next song. Does the car follow? Wait for the new song to play 20 s: does the car still show our session?
    If the car switched to YTM's own display, tap **Re-grab** in section 1 and note if it comes back.
22. Variation: tap **STOP session**, **tick** Request audio focus, YTM still playing, tap **START session**. Note: does YTM pause? (Expected yes.)

### Wrap up (1 min)
23. Add note `end`. Tap **STOP session**. Turn the car off (or disconnect Bluetooth) and wait 10 s so the disconnect is logged.
24. Tap **Share text** (or **Share file** if the text is huge) and send it to yourself. Paste it back to me with your notes and photos.

**Notes template** (copy into your reply):
```
2: fields shown on car: line1=… line2=… line3=…  other info: …
3: last char title=… artist=… album=…  scroll? …
4: title=… artist=… album=…
5-7: ok / box / blank per letter: …
8 a): …   9 b): …   10 c): …   11 1s: …
12 next  a=… b=… c=… d=… e=…  (car fast-forwarded on e? …)
13 prev  a=… b=… c=… d=… e=…
14 play/pause  a=… b=… c=…   15 HU next/prev  a=… b=…   16 source/vol: …   17 (700ms) b=… c=…
18: …  19: audible? … car shows … first session …
20: …  21: …  22: …
anything odd: …
```
