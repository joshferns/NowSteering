# 0.6.0 parked test (car on, engine off or on, not driving)

Install 0.6.0 over 0.5.0. One action per step; "Expect" is what you should see. Note any step that differs.

**A. Preset picker and previews**
1. Open Now Steering. Expect: header 0.6.0; a **Screen preset** list with Driver selected (Car only if Torque Pro is installed).
2. Read the preview under the list. Expect: for each situation three numbered lines; none longer than the car's widths (17 / 20 / 20).
3. Tap **Trip**. Expect: the preview changes within a second; the navigating lines show `Rs 125 trip · 48 now` marked "sample price Rs 100/L".
4. Tap **Car** (if listed). Expect: idle line 2 shows `84°C 14.2V 14.6km/L`.
5. Tap **Classic**. Expect: idle shows `Now Steering` on line 2 and the date on line 3, as in 0.5.0.
6. Tap **Driver**. Expect: idle `17:03 42 km/h` / `Sat 14 Nov` / `Now Steering` (sample values).
7. Look at the car. Expect: the car shows the real Driver idle screen: clock and speed on line 1, the date on line 2, `Now Steering` on line 3.

**B. Keep on screen default**
8. Check the tick box *Keep Now Steering on screen*. Expect: whatever you had saved before (an existing install keeps it).

**C. Fuel price**
9. In **Trip cost**, type `105` as the fuel price and `14` as typical km/L. Tap **Save cost settings**. Expect: "Saved", the Trip preview line 3 no longer says "sample price".
10. Clear the fuel price field. Tap **Save cost settings**. Select **Trip**. Expect: the Trip preview's navigating line 3 shows `Outer Ring Rd` (no cost line without a price).
11. Type `105` again, save, select **Driver**.

**D. Double Next**
12. Press Next twice quickly on the wheel (nothing playing). Expect: line 1 shows `Trip` for 2 s, then the screen of the Trip preset. Repeat: `Car` (if Torque installed) then `Classic`, then `Driver`.

**E. Test warnings**
13. Diagnostics → **Test HOT warning**. Expect: the car shows `▲ HOT 107°C` on line 1, `Test only`, `Back to live clears`.
14. Tap **Back to live**.
15. Tap **Test LOW warning**. Expect: `▲ LOW 12.0V` on line 1.
16. Tap **Back to live**.
17. Read **Engine warnings and trip (read-only)**. Expect: the thresholds (105 / 102 °C, 12.2 / 12.6 V, 500 RPM, 10 s on / 50 s off), "warnings active: none", and a trip line.

**F. Power saving notice (optional)**
18. Switch Power saving ON in Quick settings. Expect: the car shows `Power saving on` / `nav less smooth` / `turn it off`, top to bottom. Switch it OFF again.

**G. Play-button experiment**
19. Diagnostics → **Start Play experiment (10 s)**. Expect: status "RUNNING" for 10 s.
20. Within those 10 s, press Play (or the play / pause button) on the head unit. Expect: nothing breaks.
21. Wait for the status to return to "not running". Diagnostics → Event log. Expect: `[EXPERIMENT] ... over: PLAY key arrived=true/false`.

**H. Report**
22. Diagnostics → **Share key lines** and send it. Expect: a file that includes every `[SCREEN]` line, `[WARN]` and `[EXPERIMENT]` lines.
