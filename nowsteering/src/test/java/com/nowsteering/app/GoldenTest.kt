package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * Golden tests: the v2 pipeline must produce exactly what v0.4.0 produced. Two kinds of replay:
 *  1. Every Maps notification and every song title/artist seen in the recorded phase 1 / 2a / 2b logs (resources/golden),
 *     combined with every setting and with random-but-seeded speed, distance-to-turn, trip, song-length and arrival states,
 *     run through [LegacyScreens] (the v0.4.0 code) and the new pipeline side by side. The text actually sent to the car
 *     (after the display engine's cleaning) must match on every frame. Both pipelines get the same sequence of
 *     frames, so their turn-zone hysteresis is compared too.
 *  2. Every idle screen in the recorded logs ("[SCREEN] sent '08:41 27 km/h' / 'Sat 31 Oct' / 'Now Steering'") is rebuilt
 *     from the clock, date and speed on that line and must come out identical.
 * Raw (not yet cleaned) differences are counted and printed in the test output.
 */
class GoldenTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream("golden/$name")!!.bufferedReader().readLines()

    private val mapsRaw: List<NavText> = resource("maps_raw.tsv").filter { it.isNotBlank() }.map { l ->
        val p = l.split('\t')
        NavTextParser.parse(p.getOrNull(0), p.getOrNull(1), p.getOrNull(2))
    }

    private val songs: List<Pair<String, String>> = (resource("songs.tsv").filter { it.isNotBlank() }.map { l ->
        val p = l.split('\t'); p[0] to p.getOrElse(1) { "" }
    } + listOf(
        "Falling" to "Trevor Daniel", "Falling" to "", "Falling - Trevor Daniel" to "Trevor Daniel", "A" to "B",
        "🚗🙂" to "Trevor", "नमस्ते" to "", "Tum Hi Ho (From \"Aashiqui 2\")" to "Arijit Singh",
        "WWWWWWWWWWWWWWWWWWWWWW" to "MMMMMMMMMMMMMMMMMMMMMMMM", "  Padded  " to "  Artist  ", "Ünïcödé Tïtle" to "Ånd Àrtist",
    ))

    private val glyphs = listOf("←", "→", "↑", "↓", "↖", "↗", "•", "●", "U", "O", "")


    private fun settingsFor(n: Int, r: Random) = Settings(
        keepOnScreen = r.nextBoolean(), idleSeconds = r.nextInt(5) == 0, arrivalTime = r.nextBoolean(),
        tripBar = r.nextBoolean(), turnBar = r.nextInt(4) != 0, songPrefix = SongPrefix.entries[n % SongPrefix.entries.size])

    @Test fun replayRecordedMapsAndSongsThroughOldAndNew() {
        assertTrue("recorded Maps notifications loaded", mapsRaw.size > 300)
        val r = Random(20261113)
        val legacy = LegacyScreens()
        val zone = TurnZone()
        var frames = 0
        var rawDifferences = 0
        var toTurn: Double? = 800.0
        repeat(40_000) { n ->
            // A walk for the distance to the turn that crosses 300 / 340 m both ways, with jumps and gaps.
            toTurn = when (r.nextInt(12)) {
                0 -> null
                1 -> r.nextDouble() * 2000
                2 -> 300.0 + r.nextInt(60) - 20
                else -> toTurn?.let { (it - r.nextInt(25) + (if (r.nextInt(8) == 0) r.nextInt(60) else 0)).coerceAtLeast(0.0) } ?: 600.0
            }
            val text = mapsRaw[r.nextInt(mapsRaw.size)]
            val glyph = text.textGlyph ?: glyphs[r.nextInt(glyphs.size)]
            val navOn = r.nextInt(5) != 0
            val nav = if (navOn) NavState.Info(text, glyph, "test", null) else null
            val song = if (r.nextInt(3) == 0) null else songs[r.nextInt(songs.size)].let { (t, a) ->
                val dur = when (r.nextInt(6)) { 0 -> 0L; 1 -> -1L; 2 -> 90_000_000L; else -> 120_000L + r.nextInt(300_000) }
                val pos = if (r.nextInt(6) == 0) null else if (r.nextInt(8) == 0) -5L else r.nextInt(400_000).toLong()
                SessionWatcher.Song("com.test", t, a, dur, pos)
            }
            val arrived = if (r.nextInt(15) == 0) Arrived(text.road, "19:${10 + r.nextInt(50)}") else null
            val cal = Calendar.getInstance().apply { timeInMillis = 1_793_000_000_000L + r.nextInt(86_400) * 1000L * 5 }
            val settings = settingsFor(n, r)
            val speedKmh = if (r.nextInt(4) == 0) null else r.nextInt(130)
            val speedAge = if (speedKmh == null) null else listOf(0L, 900L, 4999L, 5000L, 5001L, 20_000L)[r.nextInt(6)]
            val trip = if (r.nextInt(4) == 0) null else r.nextInt(30_000).toDouble() + 1
            val inputsOld = LiveInputs(settings, cal.time, speedKmh, speedAge, arrived, nav, { toTurn }, trip, { song })
            val inputsNew = LiveInputs(settings, cal.time, speedKmh, speedAge, arrived, nav, { toTurn }, trip, { song })
            val mode = if (r.nextInt(10) == 0) "IDLE_WHILE_NAV" else "AUTO"
            val old = legacy.screen(inputsOld, mode)
            val ni = if (mode == "AUTO") inputsNew else LiveInputs(settings, cal.time, speedKmh, speedAge, arrived, null, { toTurn }, trip, { song })
            val new = CurrentPreset.build(settings).resolve(LiveContext(ni, zone))
            if (old != new) {
                rawDifferences++
                // The only allowed raw difference is stray spaces that cleaning removes anyway.
                fun squash(x: String) = x.replace(Regex(" +"), " ").trim()
                assertEquals(listOf(old.title, old.album, old.artist).map(::squash), listOf(new.title, new.album, new.artist).map(::squash))
            }
            assertEquals("frame $n settings=$settings nav=$text glyph=$glyph toTurn=$toTurn song=$song arrived=$arrived mode=$mode",
                old.sanitized().toString(), new.sanitized().toString())
            assertEquals("max widths frame $n", old.sanitized().let { listOf(it.titleMax, it.albumMax, it.artistMax) },
                new.sanitized().let { listOf(it.titleMax, it.albumMax, it.artistMax) })
            frames++
        }
        println("GOLDEN replay: $frames frames from ${mapsRaw.size} recorded Maps notifications and ${songs.size} songs; " +
            "sent text identical on all; raw (pre-cleaning) differences: $rawDifferences, all stray spaces (empty arrow glyph)")
    }

    @Test fun recordedIdleScreensComeOutIdentical() {
        val line = Regex("""\[SCREEN] sent '(\d\d:\d\d) (--|\d+) km/h' / '([A-Za-z]{3} \d{1,2} [A-Za-z]{3})' / 'Now Steering'""")
        val lines = resource("idle_screens.txt").filter { it.isNotBlank() }
        assertTrue("recorded idle screens loaded", lines.size > 300)
        val dateIn = SimpleDateFormat("EEE d MMM yyyy HH:mm", Locale.US)
        var checked = 0
        for (l in lines) {
            val m = line.find(l) ?: continue
            val (clock, speed, date) = m.destructured
            val d: Date = dateIn.parse("$date 2026 $clock")!!
            val settings = Settings(false, false, false, true, true, SongPrefix.ARROW)
            val inp = LiveInputs(settings, d, speed.toIntOrNull(), if (speed == "--") null else 0L, null, null, { null }, null, { null })
            val s = CurrentPreset.build(settings).resolve(LiveContext(inp, TurnZone())).sanitized()
            // Logs before the row-order fix print (title / artist / album); artist is car line 3, album is line 2.
            assertEquals(l, "'$clock $speed km/h' / '$date' / 'Now Steering'", "'${s.title}' / '${s.artist}' / '${s.album}'")
            checked++
        }
        println("GOLDEN recorded idle screens rebuilt identically: $checked")
        assertTrue(checked > 300)
    }
}
