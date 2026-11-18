# 0.6.0 drive test

Phone settings: Driver preset, Keep Now Steering on screen ON, Power saving OFF. Torque Pro running and connected (for the cost line and the Car preset). A fuel price entered. Music playing on YouTube Music.

**Leg A: Driver with music and navigation**
1. Connect to the car with the engine on. Expect: Driver idle: clock and speed on line 1.
2. Start music. Expect: line 1 `speed km/h  clock`, line 2 `▶ title - artist`, line 3 `time left` and a bar.
3. Start Google Maps navigation to somewhere with a turn in 2 km or more. Expect: line 1 arrow, distance and speed, line 2 the song, line 3 time left and a bar.
4. Drive faster than 100 km/h on a road where the next turn is more than 10 km away, if you can. Expect: line 1 never longer than 17 characters; the speed stays visible, losing ` km/h` first.
5. Keep driving toward the turn. At about 300 m, expect: line 1 arrow and distance only, line 2 the arrow and a turn bar, line 3 `speed km/h ▶ time left`. The song and the bar are gone from lines 2 and 3.
6. Take the turn. Expect: the far layout returns about 340 m later or on the next instruction, with the song and bar again.
7. Stop the music. Expect: line 2 shows the trip time and bar, line 3 the road.

**Leg B: Trip preset**
8. Double-tap Next. Expect: `Trip` on line 1 for 2 s.
9. Look while navigating. Expect: line 1 arrow and distance, line 2 time left and a trip bar, line 3 `Rs ~125 trip · ~48 now` style (the numbers are yours); if a number is long it becomes `Rs ~1247 trip`.
10. Check the numbers: Torque's trip fuel used x price is the `now` figure.
11. Double-tap Next. Expect: `Car` (if Torque installed): with no navigation, idle line 2 shows coolant, voltage and km/L.
12. Double-tap Next. Expect: `Classic`. Double-tap again: `Driver`.

**Leg C: arrival and end of trip**
13. Arrive. Expect: `● Arrived`, the time, the road. Double Next clears it (cycling does not start until Arrived is cleared).
14. Park and turn the car off. Expect the log to have a `[TRIP] trip ended (car disconnected)` line.

**Report**
15. Share key lines. Include: the `[TRIP]` lines (every 60 s and the last one), the distance and fuel Torque / Home Assistant shows for the same trip, and any screen that looked wrong.
