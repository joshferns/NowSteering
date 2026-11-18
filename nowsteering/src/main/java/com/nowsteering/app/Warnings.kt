package com.nowsteering.app

import java.util.Locale
import kotlin.math.roundToInt

enum class WarnKind(val word: String) { HOT("HOT"), LOW("LOW") }

/** One active warning: [value] is °C for HOT and volts for LOW. */
data class Warn(val kind: WarnKind, val value: Double) {
    /** What the car shows: "▲ HOT 107°C", "▲ LOW 12.0V". */
    fun text(): String = when (kind) {
        WarnKind.HOT -> "▲ HOT ${value.roundToInt()}°C"
        WarnKind.LOW -> "▲ LOW ${String.format(Locale.US, "%.1f", value)}V"
    }
}

/** Where a face lets a warning replace a line: the top line on idle / music faces, line 2 on navigation faces, nowhere near a turn. */
enum class WarnSlot { LINE1, LINE2, NONE }

/**
 * Decides which warnings are active. Fed once a second with the latest readings; a null reading means the value is
 * missing or stale (or, for voltage, that the engine is not known to be running), which clears the warning at once.
 */
class WarningMonitor(private val th: WarningThresholds = CarProfile.current.warnings, private val log: (String) -> Unit = {}) {
    private class State { var armedAt: Long? = null; var clearAt: Long? = null; var active = false; var since = 0L }

    private val hot = State()
    private val low = State()

    /** Active warnings in the order they became active. */
    fun update(nowMs: Long, coolantC: Double?, voltage: Double?, rpm: Double?): List<Warn> {
        step(hot, WarnKind.HOT, nowMs, coolantC,
            raise = { it > th.coolantHighC }, hold = th.coolantHoldMs, clear = { it < th.coolantClearC }, clearHold = th.coolantClearMs)
        val engineOn = rpm != null && rpm > th.rpmMin
        step(low, WarnKind.LOW, nowMs, if (engineOn) voltage else null,
            raise = { it < th.voltageLowV }, hold = th.voltageHoldMs, clear = { it > th.voltageClearV }, clearHold = th.voltageClearMs)
        val out = ArrayList<Warn>()
        for ((st, kind, v) in listOf(Triple(hot, WarnKind.HOT, coolantC), Triple(low, WarnKind.LOW, voltage)).sortedBy { it.first.since }) {
            if (st.active && v != null) out += Warn(kind, v)
        }
        return out
    }

    private fun step(s: State, kind: WarnKind, now: Long, v: Double?, raise: (Double) -> Boolean, hold: Long, clear: (Double) -> Boolean, clearHold: Long) {
        if (v == null) {
            if (s.active) log("[WARN] ${kind.word} cleared: reading missing or stale")
            s.active = false; s.armedAt = null; s.clearAt = null
            return
        }
        if (!s.active) {
            if (raise(v)) {
                val a = s.armedAt ?: now.also { s.armedAt = it }
                if (now - a >= hold) { s.active = true; s.since = now; s.clearAt = null; log("[WARN] ${kind.word} active: ${Warn(kind, v).text()}") }
            } else s.armedAt = null
        } else {
            if (clear(v)) {
                val c = s.clearAt ?: now.also { s.clearAt = it }
                if (now - c >= clearHold) { s.active = false; s.armedAt = null; s.clearAt = null; log("[WARN] ${kind.word} cleared: ${Warn(kind, v).text()}") }
            } else s.clearAt = null
        }
    }
}

/**
 * Decides when an active warning is on the car: [showMs] on, [hideMs] off while it lasts; at once when it first appears
 * or gets worse by [WarningThresholds.worsenC] / [WarningThresholds.worsenV]; both active alternate. While the face has
 * no slot for it (the last 300 m before a turn, Arrived, a notice) it is held: its on-time stops, and it is shown right after.
 */
class WarningPresenter(private val th: WarningThresholds = CarProfile.current.warnings, private val log: (String) -> Unit = {}) {
    private var onKind: WarnKind? = null
    private var onLeftMs = 0L
    private var queue = ArrayDeque<WarnKind>()
    private var offUntil = 0L
    private val baseline = HashMap<WarnKind, Double>()
    private val known = HashSet<WarnKind>()
    private var lastAt = 0L

    /** The warning on the car now (with its current value), or null. */
    fun update(nowMs: Long, active: List<Warn>, canShow: Boolean): Warn? {
        val dt = if (lastAt == 0L) 0L else (nowMs - lastAt).coerceIn(0, 5_000)
        lastAt = nowMs
        val byKind = active.associateBy { it.kind }
        if (byKind.isEmpty()) {
            if (onKind != null) log("[WARN] hidden: no warning active")
            reset(); return null
        }
        known.retainAll(byKind.keys); baseline.keys.retainAll(byKind.keys)
        queue.removeAll { it !in byKind }
        if (onKind != null && onKind !in byKind) onKind = null

        // Due at once: a warning that is new, or worse than when it was last shown.
        for ((k, w) in byKind) {
            if (k !in known) { known += k; if (k !in queue && k != onKind) queue.addLast(k) }
            val b = baseline[k]
            if (b != null) {
                val worse = when (k) { WarnKind.HOT -> w.value >= b + th.worsenC; WarnKind.LOW -> w.value <= b - th.worsenV }
                if (worse) { baseline[k] = w.value; if (k !in queue && k != onKind) queue.addFirst(k); log("[WARN] ${w.text()} got worse: showing again at once") }
            }
        }
        val offOver = onKind == null && queue.isEmpty() && nowMs >= offUntil && offUntil != 0L
        if (offOver) queue.addAll(byKind.keys.sortedBy { active.indexOfFirst { a -> a.kind == it } })

        if (!canShow) return null          // held: nothing moves, the on-time is kept
        if (onKind == null && queue.isNotEmpty()) startNext(nowMs, byKind)
        else if (onKind != null) {
            onLeftMs -= dt
            if (onLeftMs <= 0) {
                onKind = null
                if (queue.isNotEmpty()) startNext(nowMs, byKind) else { offUntil = nowMs + th.hideMs; log("[WARN] hidden for ${th.hideMs / 1000} s") }
            }
        }
        // A worse reading interrupts the off period or another warning's turn.
        return onKind?.let { byKind[it] }
    }

    private fun startNext(now: Long, byKind: Map<WarnKind, Warn>) {
        val k = queue.removeFirst()
        onKind = k; onLeftMs = th.showMs; offUntil = 0L
        byKind[k]?.let { baseline[k] = it.value; log("[WARN] showing ${it.text()}") }
    }

    private fun reset() { onKind = null; queue.clear(); offUntil = 0L; baseline.clear(); known.clear() }
}

object Warnings {
    /** Put the warning on the line the face's slot allows. */
    fun apply(s: Screen, slot: WarnSlot, w: Warn): Screen = when (slot) {
        WarnSlot.LINE1 -> s.copy(title = w.text(), titleMax = CarProfile.current.measuredTitle)
        WarnSlot.LINE2 -> s.copy(album = w.text(), albumMax = CarProfile.current.measuredLine2)
        WarnSlot.NONE -> s
    }
}
