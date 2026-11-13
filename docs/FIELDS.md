# Fields (v2)

Faces and rules read **fields**: named values with a unit and an age. The names are short, dotted and lower case, and they are meant to become a public contract (shared faces will use them), so treat a rename as a breaking change.

- A field is **missing** when its source has nothing, when the value is blank, when it has reached its **staleness limit** (age ≥ limit), or when the car profile says never to show it. A missing field never shows as a dash or an old number: its part of the line is dropped (see [DESIGN.md](DESIGN.md#v2-model)). The one exception is a face that asks for a fallback on purpose, e.g. `{speed|--}`, which the Current preset uses to keep today's `--` for a missing speed.
- Sources: **phone** (clock, GPS, battery), **Maps** (Google Maps notification), **music** (the music app's media session), **Torque** (Torque Pro plugin). Any source can be absent; the app keeps working.
- Fields are read only when a face or rule asks for them, once per screen.
- Phase 1: only the phone, Maps and music fields are on screen (the Current preset). Torque and `phone.battery` can be read but no face shows them.

| Field | Source | Unit | Staleness limit | Meaning |
|---|---|---|---|---|
| `clock` | phone | HH:mm | none | Local time |
| `clock.seconds` | phone | HH:mm:ss | none | Local time with seconds (Diagnostics "Seconds on idle clock") |
| `date` | phone | EEE d MMM | none | Local date, e.g. `Sat 7 Nov` |
| `speed` | phone | km/h | 5 s | GPS speed, whole km/h, 0 below 2 km/h |
| `phone.battery` | phone | % | 2 min | Phone battery level |
| `nav.active` | Maps | | none | `true` while Maps is navigating and the instruction may be shown |
| `nav.state` | Maps | | none | `navigating`, `rerouting` or `arrived` |
| `turn.arrow` | Maps | | none | Maneuver glyph for the next turn |
| `turn.distance` | Maps | m / km | none | Distance to the next turn: Maps' value, counted down with GPS speed when Maps is quiet, rounded up to Maps' steps |
| `turn.road` | Maps | | none | Road of the next turn |
| `turn.progress` | Maps | 0..1 | none | Progress over the last 300 m before the turn (0 at 300 m or more, 1 at the turn) |
| `nav.time_left` | Maps | | none | Maps' own trip time left, e.g. `7 min`; when Maps gives no time, the distance left without its space (`2.6km`) |
| `nav.distance_left` | Maps | m / km | none | Maps' distance left on the trip |
| `nav.eta` | Maps | | none | Maps' arrival time, as Maps writes it |
| `nav.progress` | Maps | 0..1 | none | Trip progress: 1 − distance left ÷ the longest distance left seen this trip |
| `arrived.road` | Maps | | none | Road shown on the Arrived screen |
| `arrived.time` | phone | HH:mm | none | Clock time when the Arrived screen started |
| `song.title` | music | | none | Playing title; missing when fewer than 2 letters or digits are readable (e.g. Devanagari or emoji only) |
| `song.artist` | music | | none | Playing artist, cleaned for the car |
| `song.text` | music | | none | `Title - Artist` as the nav screen shows it: artist dropped when empty or already in the title; missing when unreadable |
| `song.time_left` | music | m:ss | none | Time left in the song; missing for live streams or unknown length |
| `song.progress` | music | 0..1 | none | How far through the song; missing for live streams |
| `song.playing` | music | | none | `true` while a music app is playing (a gap under 4 s between songs is bridged) |
| `car.coolant` | Torque | °C | 10 s | Engine coolant temperature, PID `05` |
| `car.voltage` | Torque | V | 10 s | Control module voltage, PID `42` (not the adapter's `Volts(Ad)`) |
| `car.rpm` | Torque | rpm | 10 s | Engine speed, PID `0c` |
| `car.trip_kmpl` | Torque | km/l | 10 s | Trip average fuel economy (Torque "Trip KPL", the one Home Assistant calls Trip Avg km/L; **not** `KPL(avg)`, a long-term average). TODO: confirm the PID id `ff1206` from a probe log |
| `car.fuel_used` | Torque | l | 10 s | Fuel used this trip. TODO: PID id not yet mapped; reachable as `car.<pid>` once known |
| `car.<pid>` | Torque | PID's own | 10 s | Any Torque PID by id, e.g. `car.0b`, `car.0f`. Ids are matched, never short names (two PIDs are both called "Intake") |

## Never shown

The car profile lists fields no face may show; they always read as missing. For the 2014 Honda City: `car.fuel_level`, `car.fuel_remaining` (the ECU reported 2.75 % with 72 km to empty).

## Torque rules

- A `car.*` field is missing unless Torque is bound and reports the adapter connected, the PID is in Torque's list and has an update time.
- Values older than 10 s are stale. (The Diagnostics Torque section uses the same limit.)
