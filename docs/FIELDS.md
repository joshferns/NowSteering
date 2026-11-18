# Fields (v2)

Faces and rules read **fields**: named values with a unit and an age. The names are short, dotted and lower case, and they are meant to become a public contract (shared faces will use them), so treat a rename as a breaking change.

- A field is **missing** when its source has nothing, when the value is blank, when it has reached its **staleness limit** (age ≥ limit), or when the car profile says never to show it. A missing field never shows as a dash or an old number: its part of the line is dropped (see [DESIGN.md](DESIGN.md#v2-model)). The one exception is a face that asks for a fallback on purpose, e.g. `{speed|--}`, which the Current preset uses to keep today's `--` for a missing speed.
- Sources: **phone** (clock, GPS, battery), **Maps** (Google Maps notification), **music** (the music app's media session), **Torque** (Torque Pro plugin). Any source can be absent; the app keeps working.
- Fields are read only when a face or rule asks for them, once per screen.
- Since 0.6.0 the Car preset shows `car.coolant`, `car.voltage` and `car.trip_kmpl`, the Trip preset shows `trip.cost` and `trip.cost_total`, and the warning layer reads `car.coolant`, `car.voltage` and `car.rpm`. `phone.battery` can be read but no face shows it yet.

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
| `song.artist` | music | | none | Playing artist, cleaned for the car; `Unknown`, `<unknown>` and empty count as missing |
| `song.text` | music | | none | `Title - Artist` as the faces show it: artist dropped when empty, `Unknown`, `<unknown>` or already in the title; missing when unreadable |
| `song.time_left` | music | m:ss | none | Time left in the song; missing for live streams or unknown length |
| `song.progress` | music | 0..1 | none | How far through the song; missing for live streams |
| `song.playing` | music | | none | `true` while a music app is playing (a gap under 4 s between songs is bridged) |
| `car.coolant` | Torque | °C | 10 s | Engine coolant temperature, PID `05` |
| `car.voltage` | Torque | V | 10 s | Control module voltage, PID `42` (not the adapter's `Volts(Ad)`) |
| `car.rpm` | Torque | rpm | 10 s | Engine speed, PID `0c` |
| `car.trip_kmpl` | Torque | km/l | 10 s | Trip average fuel economy: Torque PID `ff1206`, "Trip average KPL" (the one Home Assistant calls Trip Avg km/L; **not** `KPL(avg)`, a long-term average). Confirmed from the 12 Nov probe log |
| `car.fuel_used` | Torque | l | 10 s | Fuel used this trip: Torque PID `ff1271`, "Fuel used (trip)". Confirmed from the 12 Nov probe log |
| `car.kmpl_longterm` | Torque | km/l | 10 s | Long-term average km/L: Torque PID `ff5202` |
| `trip.distance` | phone | km | none | Kilometres driven since the car's Bluetooth connected (GPS), 1 decimal |
| `trip.kmpl` | Torque / user | km/l | none | The km/L used for the cost maths (see Trip cost below) |
| `trip.cost` | Torque | Rs | none | Fuel cost so far this trip, whole rupees |
| `trip.cost_to_go` | Maps + Torque | Rs | none | Cost of the distance still to go: `nav.distance_left` ÷ km/L × price |
| `trip.cost_total` | Maps + Torque | Rs | none | `trip.cost` + `trip.cost_to_go` |
| `trip.cost_per_10km` | Torque / user | Rs | none | What 10 km costs: 10 ÷ km/L × price |
| `car.<pid>` | Torque | PID's own | 10 s | Any Torque PID by id, e.g. `car.0b`, `car.0f`. Ids are matched, never short names (two PIDs are both called "Intake") |

## Never shown

The car profile lists fields no face may show; they always read as missing. For the 2014 Honda City: `car.fuel_level`, `car.fuel_remaining` (the ECU reported 2.75 % with 72 km to empty) and distance to empty (PID `ff126a`, `car.ff126a`, unreliable on this car). Other PIDs seen in the probe: `ff125c` fuel cost (trip).

## Torque rules

- A `car.*` field is missing unless Torque is bound and reports the adapter connected, the PID is in Torque's list and has an update time.
- Values older than 10 s are stale. (The Diagnostics Torque section uses the same limit.)

## Trip cost (0.6.0)

A **trip** runs from the car's Bluetooth connect to its disconnect (also when the service is started by hand, from that moment). Distance is integrated from GPS speed; fuel is Torque's own trip counter (PID `ff1271`), so if Torque's trip was reset at a different moment, the fuel figure follows Torque. The trip lives in memory: the app restarting starts a new one.

Settings: **fuel price** (Rs per litre; empty = every cost field is missing) and **typical km/L** (optional).

| Field | Formula |
|---|---|
| `trip.kmpl` | `car.trip_kmpl` once the trip is 2 km or longer; before that, or if it is missing, `car.kmpl_longterm`; without Torque, the typical km/L. Readings outside 1 to 100 km/L are ignored |
| `trip.cost` | `car.fuel_used` × price. With no fuel reading but a typical km/L: trip distance ÷ typical km/L × price |
| `trip.cost_to_go` | `nav.distance_left` ÷ `trip.kmpl` × price (missing when not navigating) |
| `trip.cost_total` | `trip.cost` + `trip.cost_to_go` |
| `trip.cost_per_10km` | 10 ÷ `trip.kmpl` × price |

The car shows whole rupees as `Rs`, with a `~` in front where it fits (`Rs ~125 trip · ~48 now`, then `Rs 125 trip · 48 now`, then `Rs ~125 trip`). The phone may show `₹`. A `[TRIP]` line goes to the log every 60 s and at disconnect: distance, fuel used, trip and long-term km/L, the km/L used and where it came from, and the cost, so they can be checked against Torque and Home Assistant.
