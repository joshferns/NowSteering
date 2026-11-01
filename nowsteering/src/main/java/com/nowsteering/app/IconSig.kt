package com.nowsteering.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon

/**
 * Google Maps puts the maneuver only in the notification's large icon. We shrink it to a
 * 24x24 on/off mask, compare it with icons labelled from real drives ([KnownIcons]), and log
 * every new mask once as ASCII art so it can be labelled.
 */
object IconSig {
    const val N = 24

    /** Returns the mask as N*N booleans, or null if the icon can't be drawn. */
    fun mask(ctx: Context, icon: Icon?): BooleanArray? {
        if (icon == null) return null
        return try {
            val d = icon.loadDrawable(ctx) ?: return null
            val bmp = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, N, N)
            d.draw(Canvas(bmp))
            val px = IntArray(N * N)
            bmp.getPixels(px, 0, N, 0, 0, N, N)
            val opaque = px.count { (it ushr 24) > 128 }
            // Usually a light arrow on a transparent background; if the icon is mostly opaque, use brightness instead.
            if (opaque > N * N * 85 / 100) {
                BooleanArray(N * N) { lum(px[it]) > 128 }
            } else {
                BooleanArray(N * N) { (px[it] ushr 24) > 128 }
            }
        } catch (e: Exception) {
            NsLog.log("NAV", "icon decode failed: ${e.javaClass.simpleName} ${e.message}")
            null
        }
    }

    private fun lum(c: Int): Int {
        val r = (c shr 16) and 0xff; val g = (c shr 8) and 0xff; val b = c and 0xff
        return (r * 3 + g * 6 + b) / 10
    }

    fun hex(m: BooleanArray): String {
        val sb = StringBuilder()
        for (i in m.indices step 4) {
            var v = 0
            for (j in 0 until 4) if (m[i + j]) v = v or (8 shr j)
            sb.append("0123456789abcdef"[v])
        }
        return sb.toString()
    }

    fun fromHex(h: String) = BooleanArray(N * N) { i -> ("0123456789abcdef".indexOf(h[i / 4]) and (8 shr (i % 4))) != 0 }

    fun art(m: BooleanArray) = (0 until N).joinToString("\n") { r ->
        (0 until N).joinToString("") { c -> if (m[r * N + c]) "#" else "." }
    }

    fun distance(a: BooleanArray, b: BooleanArray) = a.indices.count { a[it] != b[it] }

    /** Best labelled match within ~6% of pixels, else null. */
    fun match(m: BooleanArray): Pair<String, Int>? =
        KnownIcons.list.map { (glyph, h) -> glyph to distance(m, fromHex(h)) }
            .filter { it.second <= N * N * 6 / 100 }
            .minByOrNull { it.second }
}

/** Labelled Maps maneuver icons (glyph to 24x24 mask hex), from logged drives (drive of 2026-11-01). */
object KnownIcons {
    val list: List<Pair<String, String>> = listOf(
        // #1 straight arrow over a dashed line: "head towards" at the start of a route
        "↑" to "000000000000000000000000001800003c00007e0000ff0001db800399c0011880001800001800001800001800000000001800001800000000001800001800000000000000000000",
        // #2 up from bottom right, then a horizontal bar ending in a left arrowhead: turn left
        "←" to "00000000000000000000800001c00003c0000780000f00001fffe03ffff01ffff80f001c07801c03c00c01c00c00800c00000c00000c00000c00000c00000c000000000000000000",
        // #3 mirror of #2: turn right
        "→" to "0000000000000000000001000003800003c00001e00000f007fff80ffffc1ffff83800f03801e03003c0300380300100300000300000300000300000300000000000000000000000",
        // #4 up the left side, over the top, back down to a lower-right arrowhead: sharp right (no ↘ on the car)
        "→" to "0000000000000000000000000380000fc0001fe0001c70001c3800181c18180e181807181803981801f81800f81800f8180ff8180ff8180000180000180000000000000000000000",
        // #5 mirror of #4: sharp left
        "←" to "0000000000000000000000000001c00003f00007f8000e38001c3818381818701818e01819c0181f80181f00181f00181ff0181ff018000018000018000018000000000000000000",
        // #7 round map pin: destination (saved place "Home")
        "●" to "000000000000007e0001ff8003c3c00700e00600600e00700c18300c3c300c3c300c18300c00300600600600600300c00381c001c38000e700007e00003c00001800000000000000",
        // #6 map pin with a flag: destination
        "●" to "0000000001e00007f8000e1c000c0c0018060018c60018c6001806000c0c000c0c0e6e1c0e66181ff3301ff3f01e79e03e7cc03ffc003ffc007e7e007e7e00000000000000000000",
    )
}
