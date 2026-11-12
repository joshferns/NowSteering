package com.nowsteering.app

/** One Torque PID as the plugin API describes it. [id] is the string to pass back to getPIDValuesAsDouble. */
data class TorquePid(
    val id: String,
    val name: String,
    val shortName: String,
    val unit: String,
    val active: Boolean,
    val ecuSupported: Boolean,
) {
    companion object {
        /**
         * getPIDInformation returns "longName,shortName,unit,max,min,scale" per PID. Names can contain commas,
         * so the last four fields are taken from the end, then unit, then shortName, and the rest is the long name.
         */
        fun parseInfo(id: String, csv: String?, active: Boolean, ecu: Boolean): TorquePid {
            val parts = (csv ?: "").split(',')
            if (parts.size < 7) {
                val name = parts.getOrNull(0)?.trim().orEmpty().ifEmpty { id }
                return TorquePid(id, name, parts.getOrNull(1)?.trim().orEmpty(), parts.getOrNull(2)?.trim().orEmpty(), active, ecu)
            }
            val unit = parts[parts.size - 4].trim()
            val short = parts[parts.size - 5].trim()
            val name = parts.subList(0, parts.size - 5).joinToString(",").trim()
            return TorquePid(id, name.ifEmpty { id }, short, unit, active, ecu)
        }

        /** Torque gives -1 or 0 for a PID it has never read, and a time in ms otherwise. */
        const val STALE_MS = 10_000L

        fun valueText(value: Double?, updatedMs: Long?, nowMs: Long): String {
            if (value == null || updatedMs == null || updatedMs <= 0L) return "—"
            if (value.isNaN() || value.isInfinite()) return "—"
            if (nowMs - updatedMs > STALE_MS) return "— (stale)"
            val v = if (Math.abs(value) >= 100) String.format("%.0f", value) else String.format("%.2f", value).trimEnd('0').trimEnd('.')
            return v
        }
    }
}
