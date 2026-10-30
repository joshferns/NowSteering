package com.nowsteering.app

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Tiny programmatic-UI helpers shared by the two screens. */
abstract class BaseActivity : Activity() {
    protected lateinit var root: LinearLayout

    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    protected fun page() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(48))
        }
        setContentView(ScrollView(this).apply { addView(root, MATCH_PARENT, WRAP_CONTENT) })
    }

    protected fun section(title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(4))
        })
    }

    protected fun mono(size: Float = 12f) = TextView(this).apply {
        textSize = size
        typeface = Typeface.MONOSPACE
    }.also { root.addView(it) }

    protected fun big(color: Int) = TextView(this).apply {
        textSize = 26f
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setBackgroundColor(color)
    }.also { root.addView(it) }

    protected fun row(vararg views: View) {
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
        })
    }

    protected fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    protected fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
