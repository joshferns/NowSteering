# Phase 1 test 2 (~8 min: parked, then a short loop)

**At home first:** install the new NowSteering.apk. Open it: the box at the top must say **Setup complete**.
For any MISSING line, tap its numbered button. For *Notification access*: tap **1. Allow restricted settings** → ⋮
→ *Allow restricted settings* first, then **2. Notification access** → turn on **Now Steering (Maps + media)**
(not NS Probe). Then **Choose car** → *Honda HFT*. Open **Diagnostics** → **Clear**.

## Parked: does the car update with the phone locked? (one action per step)
1. Start the car. Note: does "Now Steering is on" appear on the phone by itself?
2. Diagnostics: tick **Seconds on idle clock**. Note: are the seconds ticking on the car?
3. Lock the phone (power button). Watch the car for 20 s. Note: seconds ticking / frozen?
4. Unlock. Diagnostics: choose **metadata + playback-position bump**.
5. Lock the phone. Watch 20 s. Note: ticking / frozen?
6. Unlock. Choose **fake track change**.
7. Lock the phone. Watch 20 s. Note: ticking / frozen? Any flicker or odd display?
8. Unlock. Choose the style that kept ticking (if several did, use **metadata only** first, then **bump**). Untick **Seconds on idle clock**.

## Nav check
9. Start Google Maps navigation to a place ~2 km away. Note: car shows `• <distance>` / road / `ETA …`? 📷
10. Lock the phone and drive there (glance only). Note: does the distance count down? Do road changes show?
11. Arrive. Note: does the car go back to the clock screen?
12. Diagnostics → **Share file**, send it to me with your notes.

**Notes template**
```
1: …  2: …  3 (metadata only): …  5 (bump): …  7 (track change): …  8 chose: …
9: …  10: …  11: …
odd: …
```
