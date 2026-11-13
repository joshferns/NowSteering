# v2 foundation (0.5.0): regression drive

Goal: everything looks and behaves **exactly like 0.4.0**. Nothing is new. If anything differs, note the step and Share key lines.

Before you start: install 0.5.0 over 0.4.0 (https://github.com/joshferns/NowSteering/releases/latest/download/NowSteering.apk). Settings as you normally have them (Keep on screen OFF).

1. Open Now Steering, check the header says 0.5.0. Connect to the car.
2. Look at the car screen, nothing playing. Expect: line 1 clock and speed (`17:03 0 km/h`), line 2 `Now Steering`, line 3 the date.
3. Start YouTube Music playing. Expect: the car screen stays as step 2 (Keep on screen is off).
4. In Now Steering turn on **Keep Now Steering on screen**. Expect: line 1 clock + speed, line 2 `▶` and the song title, line 3 time left and a bar (artist if it is a live stream).
5. Press Next on the wheel once. Expect: the song changes and the car screen shows the new song.
6. Turn **Keep on screen** back OFF. Start regular YouTube playing. Tap **Show on car** in the notification. Expect: the clock screen on the car.
7. Start Google Maps navigation to somewhere ~5 km away. Expect: line 1 arrow and distance, line 2 time left and a trip bar, line 3 the song (or the road if nothing is playing).
8. Drive toward the first turn. At about 300 m: line 2 changes to the arrow and a turn bar, line 3 shows the road. After the turn it goes back to time left and the song.
9. Press Next twice quickly. Expect: the clock screen; twice again returns to navigation.
10. Drive off the route. Expect: `• Rerouting...` until Maps gives a new instruction.
11. Arrive. Expect: `● Arrived`, the time on line 2, the road on line 3, until you drive away.
12. If convenient: make or receive a call. Expect: the car screen steps aside during the call and returns about 3 s after.
13. Diagnostics: check the Torque section still works (bound, OBD connected, values). Then **Share key lines**.

Report: any step that did not match "Expect", and the key lines file.
