# Phase 2b probe: test script (v0.4.0)

Parked, in the car, one action per step. Before you start: Torque Pro is installed and paired to the adapter as usual; Now Steering 0.4.0 is installed (open the app once). Torque's phone notification access etc. is not needed.

1. Engine off. Open Now Steering → Diagnostics → scroll to **Torque**. Read: *Torque installed*, *Plugin service bound*, *OBD connected*. Expected: installed yes; OBD connected no/unknown.
2. Start the engine.
3. Open Torque Pro and wait until it says it is connected to the adapter.
4. Go back to Now Steering → Diagnostics → **Torque**. Expected: bound yes, a Torque version, OBD connected yes, a list of PIDs.
5. Wait 10 seconds. Check that values change (coolant, voltage, RPM) and none says "stale".
6. Tap **Refresh PID list** once. Check the list is still there.
7. Check Home Assistant is still getting updates from Torque (same dashboard as always).
8. Optional: switch Torque to the background (home button) for 30 s, return to Diagnostics. Values should keep updating.
9. Diagnostics → **Share key lines** and send it.

Also report: Torque version shown, whether the name of each PID looks right (coolant, battery voltage, trip average), and any step that did not match "Expected".
