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

/** Labelled Maps maneuver icons (glyph to 24x24 mask hex). Filled in from logged drives. */
object KnownIcons {
    val list: List<Pair<String, String>> = listOf(
    )
}
