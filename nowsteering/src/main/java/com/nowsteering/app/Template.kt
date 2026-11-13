package com.nowsteering.app

/**
 * Line templates (internal syntax for now; the shareable format comes later).
 *
 *   {speed}                  a field
 *   {speed|--}               a field with a fallback text, used when it is missing
 *   {bar song.progress 6}    a progress bar 6 cells wide from a 0..1 field
 *   "{speed} km/h  {clock}"  two or more spaces split a line into parts
 *
 * A part that uses a missing field is dropped whole (no dashes, no orphan "km/h"); the remaining parts are joined.
 * Cutting to the line width at a word happens when the screen is sent ([Glyphs.sanitize]).
 */
object Template {
    private sealed interface Item {
        data class Lit(val s: String) : Item
        data class Ref(val name: String, val fallback: String?) : Item
        data class Bar(val name: String, val cells: Int) : Item
    }

    private val cache = HashMap<String, List<List<Item>>>()
    private val gap = Regex(" {2,}")
    private val refRe = Regex("""^([A-Za-z0-9._]+)(?:\|(.*))?$""")

    @Synchronized
    private fun parse(template: String): List<List<Item>> = cache.getOrPut(template) {
        val items = ArrayList<Item>()
        var i = 0
        val lit = StringBuilder()
        fun flush() { if (lit.isNotEmpty()) { items.add(Item.Lit(lit.toString())); lit.clear() } }
        while (i < template.length) {
            val c = template[i]
            val end = if (c == '{') template.indexOf('}', i) else -1
            if (end < 0) { lit.append(c); i++; continue }
            val body = template.substring(i + 1, end).trim()
            val item: Item? = when {
                body.startsWith("bar ") -> body.removePrefix("bar ").trim().split(' ').filter { it.isNotEmpty() }.let { p ->
                    val cells = p.getOrNull(1)?.toIntOrNull()
                    if (p.size == 2 && cells != null && cells > 0) Item.Bar(p[0], cells) else null
                }
                refRe.matches(body) -> refRe.find(body)!!.let { m -> Item.Ref(m.groupValues[1], m.groups[2]?.value) }
                else -> null
            }
            if (item == null) { lit.append(template, i, end + 1) } else { flush(); items.add(item) }
            i = end + 1
        }
        flush()
        // Split into parts at runs of 2+ spaces inside literals.
        val parts = ArrayList<List<Item>>()
        var cur = ArrayList<Item>()
        for (it in items) {
            if (it is Item.Lit && gap.containsMatchIn(it.s)) {
                val pieces = it.s.split(gap)
                pieces.forEachIndexed { n, piece ->
                    if (piece.isNotEmpty()) cur.add(Item.Lit(piece))
                    if (n < pieces.size - 1) { parts.add(cur); cur = ArrayList() }
                }
            } else cur.add(it)
        }
        parts.add(cur)
        parts
    }

    /** The rendered line, or "" when every part was dropped. */
    fun render(template: String, fields: Fields): String {
        val kept = ArrayList<String>()
        for (part in parse(template)) {
            val sb = StringBuilder()
            var missing = false
            var hasField = false
            for (item in part) {
                when (item) {
                    is Item.Lit -> sb.append(item.s)
                    is Item.Ref -> {
                        hasField = true
                        val v = fields.text(item.name) ?: item.fallback
                        if (v == null) missing = true else sb.append(v)
                    }
                    is Item.Bar -> {
                        hasField = true
                        val f = fields.number(item.name)
                        if (f == null) missing = true else sb.append(Bars.bar(f, item.cells))
                    }
                }
            }
            if (missing) continue
            val s = sb.toString()
            if (s.isBlank() && !hasField) continue
            kept.add(s)
        }
        return kept.joinToString("  ")
    }

    /** Field names a template reads (for tests and docs). */
    fun fieldsUsed(template: String): Set<String> =
        parse(template).flatten().mapNotNull { (it as? Item.Ref)?.name ?: (it as? Item.Bar)?.name }.toSet()
}

/**
 * One way to write a line. The first alternative that renders non-empty wins, so a line can fall back
 * ("time + bar", then "time", then "bar"). [max] is the width of the line when this alternative is used.
 * [fitsIn] makes it count only if its text is that short (raw length, before cleaning).
 */
data class Alt(val template: String, val max: Int? = null, val fitsIn: Int? = null)

/** A line of a face: its alternatives in order of preference. */
data class Line(val alts: List<Alt>) {
    constructor(template: String, max: Int? = null) : this(listOf(Alt(template, max)))

    /** The text and the width it is cut to. */
    fun render(fields: Fields, defaultMax: Int): Pair<String, Int> {
        for (a in alts) {
            val t = Template.render(a.template, fields)
            if (t.isEmpty()) continue
            if (a.fitsIn != null && t.length > a.fitsIn) continue
            return t to (a.max ?: defaultMax)
        }
        return "" to (alts.lastOrNull()?.max ?: defaultMax)
    }

    companion object {
        val EMPTY = Line(listOf(Alt("")))
    }
}

/** Three lines for the car, top to bottom: title (big line), line 2 (disc icon), line 3 (person icon). */
data class Face(
    val id: String,
    val line1: Line,
    val line2: Line,
    val line3: Line,
    /** Extra glyphs the car is allowed to try on this face. */
    val extra: String = "",
    val profile: CarProfile = CarProfile.current,
) {
    fun render(fields: Fields): Screen {
        val (t, tMax) = line1.render(fields, profile.safeTitle)
        val (l2, l2Max) = line2.render(fields, profile.safeLine)
        val (l3, l3Max) = line3.render(fields, profile.safeLine)
        return Screen(t, artist = l3, album = l2, titleMax = tMax, artistMax = l3Max, albumMax = l2Max, extra = extra)
    }

    companion object {
        /** A face made of fixed text only (messages). */
        fun literal(id: String, line1: String, line2: String = "", line3: String = "") =
            Face(id, Line(line1), Line(line2), Line(line3))
    }
}
