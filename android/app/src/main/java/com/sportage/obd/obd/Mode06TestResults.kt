package com.sportage.obd.obd

/**
 * One SAE J1979 Service 06 (on-board monitoring test result) record.
 *
 * Layout on the wire: `46 <MID> <TID> <UAS> <value hi> <value lo> <min hi> <min lo> <max hi> <max lo>`.
 * Every numeric field is kept exactly as the ECU sent it; [value], [min] and [max] apply the
 * UAS scaling only, and the raw integers stay available through the display helpers.
 */
data class Mode06TestResult(
    val mid: Int,
    val monitorName: String,
    val tid: Int,
    val testName: String,
    val uas: Int,
    val unitLabel: String,
    val scaling: Float,
    val rawValue: Int,
    val rawMin: Int,
    val rawMax: Int,
    val hasLimits: Boolean
) {
    val value: Float get() = rawValue * scaling
    val min: Float get() = rawMin * scaling
    val max: Float get() = rawMax * scaling

    /** True/false only when the ECU supplied usable limits, null otherwise. */
    val passed: Boolean?
        get() = if (hasLimits && rawMin <= rawMax) rawValue in rawMin..rawMax else null

    val unitSuffix: String get() = if (unitLabel.isEmpty()) "" else " $unitLabel"

    val displayValue: String get() = format(value)

    val displayRange: String
        get() = if (hasLimits) "${format(min)} – ${format(max)}$unitSuffix" else "no limits reported"

    private fun format(number: Float): String =
        if (scaling >= 1f) number.toInt().toString()
        else String.format(java.util.Locale.US, "%.2f", number)
}

/** Decoded Mode 06 result set for a single MID, plus the MIDs the ECU advertises. */
data class Mode06TestReport(
    val supportedMids: List<Int> = emptyList(),
    val tests: List<Mode06TestResult> = emptyList(),
    val raw: String = ""
) {
    val failures: List<Mode06TestResult> get() = tests.filter { it.passed == false }
    val passes: List<Mode06TestResult> get() = tests.filter { it.passed == true }
    val unknowns: List<Mode06TestResult> get() = tests.filter { it.passed == null }

    val monitoredMids: List<Pair<Int, String>>
        get() = tests.distinctBy { it.mid }.map { it.mid to it.monitorName }

    /** Sort by monitor id (ASC) and test id. */
    fun sortedByMonitor(): List<Mode06TestResult> = tests.sortedWith(compareBy({ it.mid }, { it.tid }))

    /** Failures first, then unknown, then passes; each group ordered by monitor/test id. */
    fun sortedByFailure(): List<Mode06TestResult> = tests.sortedWith(
        compareBy<Mode06TestResult>({ when (it.passed) { false -> 0; null -> 1; true -> 2 } }, { it.mid }, { it.tid })
    )
}

/**
 * SAE J1979 Service 06 (on-board monitoring test) decoder.
 *
 * The decoder is deliberately conservative:
 *  - it only interprets the documented `MID / TID / UAS / value / min / max` record shape,
 *  - unreadable remainders are kept in [Mode06TestReport.raw] instead of being guessed,
 *  - pass/fail is reported only when the ECU supplied non-zero, ordered limits.
 *
 * The UAS "unit" nibble is mapped for the handful of unit ids below; anything else is shown as a
 * raw count with the numeric unit id, never as a fabricated physical unit.
 */
object Mode06TestParser {

    /** Commands used to enumerate supported MIDs: `06 00`, `06 20`, `06 40`, ... */
    fun supportCommands(): List<String> = (0x00..0xE0 step 0x20).map { "06" + hex2(it) }

    /** MIDs advertised by a `46 <base> <4 bitmap bytes>` response. */
    fun decodeSupportedMids(raw: String): List<Int> {
        val hex = IsoTp.assemble(raw).uppercase()
        val mids = mutableListOf<Int>()
        var search = 0
        while (true) {
            val index = hex.indexOf("46", search)
            if (index < 0 || hex.length < index + 12) break
            search = index + 2
            val base = hex.substring(index + 2, index + 4).toIntOrNull(16) ?: continue
            val bitmap = hex.substring(index + 4, index + 12)
            val bits = bitmap.chunked(2).mapNotNull { it.toIntOrNull(16) }
            if (bits.size != 4) continue
            bits.forEachIndexed { byteIndex, byte ->
                for (bit in 7 downTo 0) {
                    if ((byte shr bit) and 0x01 == 1) {
                        val mid = base + 1 + byteIndex * 8 + (7 - bit)
                        if (mid in 1..0xFF && mid !in mids) mids.add(mid)
                    }
                }
            }
        }
        return mids
    }

    /** Decodes the test records of one MID (`46 <MID> ...`). */
    fun decodeTests(raw: String, mid: Int): Mode06TestReport {
        val hex = IsoTp.assemble(raw).uppercase()
        val marker = "46" + hex2(mid)
        val tests = mutableListOf<Mode06TestResult>()
        var search = 0
        while (true) {
            val index = hex.indexOf(marker, search)
            if (index < 0) break
            search = index + marker.length
            var cursor = index + marker.length
            // Records are 8 bytes: TID, UAS, value(2), min(2), max(2).
            while (cursor + 16 <= hex.length) {
                val record = hex.substring(cursor, cursor + 16)
                val bytes = record.chunked(2).mapNotNull { it.toIntOrNull(16) }
                // A following MID block starts with 46 <mid>; stop before reading across it.
                if (bytes.size < 8 || hex.startsWith("46", cursor)) break
                val tid = bytes[0]
                val uas = bytes[1]
                val value = (bytes[2] shl 8) or bytes[3]
                val min = (bytes[4] shl 8) or bytes[5]
                val max = (bytes[6] shl 8) or bytes[7]
                val usableLimits = usableLimits(min, max)
                tests.add(
                    Mode06TestResult(
                        mid = mid,
                        monitorName = monitorName(mid),
                        tid = tid,
                        testName = testName(mid, tid),
                        uas = uas,
                        unitLabel = unitLabel(uas),
                        scaling = scaling(uas),
                        rawValue = value,
                        rawMin = min,
                        rawMax = max,
                        hasLimits = usableLimits
                    )
                )
                cursor += 16
            }
        }
        return Mode06TestReport(tests = tests, raw = raw)
    }

    /** Decodes several MID responses into one report (support bitmap folded in). */
    fun decode(responses: Map<Int, String>): Mode06TestReport {
        val supported = mutableListOf<Int>()
        val tests = mutableListOf<Mode06TestResult>()
        val raws = mutableListOf<String>()
        for ((mid, raw) in responses) {
            raws.add(raw)
            if (mid % 0x20 == 0) {
                supported.addAll(decodeSupportedMids(raw))
            } else {
                tests.addAll(decodeTests(raw, mid).tests)
            }
        }
        return Mode06TestReport(
            supportedMids = supported.distinct().sorted(),
            tests = tests.sortedWith(compareBy({ it.mid }, { it.tid })),
            raw = raws.joinToString("\n")
        )
    }

    /**
     * SAE J1979 unit ids (high nibble of the UAS byte). Only ids the app can state with
     * confidence are mapped; every other id falls back to an empty unit and raw counts.
     */
    private fun unitLabel(uas: Int): String = when (uas shr 4) {
        0x0 -> ""
        0x1 -> "%"
        0x2 -> "°C"
        0x3 -> "kPa"
        0x4 -> "rpm"
        0x5 -> "km/h"
        0x6 -> "V"
        0x7 -> "ms"
        else -> ""
    }

    /** Low nibble of the UAS byte: power-of-ten divisor applied to the raw counts. */
    private fun scaling(uas: Int): Float {
        val exponent = uas and 0x0F
        var scale = 1f
        repeat(exponent.coerceAtMost(6)) { scale /= 10f }
        return scale
    }

    private fun usableLimits(min: Int, max: Int): Boolean =
        max != 0 && min <= max && !(min == 0xFFFF && max == 0xFFFF) && !(min == 0 && max == 0xFFFF)

    fun hex2(value: Int): String = value.toString(16).uppercase().padStart(2, '0')

    /**
     * Monitor names keyed by MID. Values follow the SAE J1979 monitor ids most ECUs use;
     * unknown ids are labelled with the numeric MID so the value is never misnamed.
     */
    fun monitorName(mid: Int): String = when (mid) {
        0x01 -> "Misfire Monitor — General"
        in 0x02..0x0A -> "Misfire Monitor — Cylinder ${mid - 1}"
        0x0B -> "Misfire Monitor — Random"
        0x21 -> "Catalyst Monitor — Bank 1"
        0x22 -> "Catalyst Monitor — Bank 2"
        0x31 -> "O2 Sensor Monitor — Bank 1 Sensor 1"
        0x32 -> "O2 Sensor Monitor — Bank 1 Sensor 2"
        0x33 -> "O2 Sensor Monitor — Bank 2 Sensor 1"
        0x34 -> "O2 Sensor Monitor — Bank 2 Sensor 2"
        0x35 -> "O2 Sensor Heater Monitor — Bank 1 Sensor 1"
        0x36 -> "O2 Sensor Heater Monitor — Bank 1 Sensor 2"
        0x37 -> "O2 Sensor Heater Monitor — Bank 2 Sensor 1"
        0x38 -> "O2 Sensor Heater Monitor — Bank 2 Sensor 2"
        0x3B -> "EVAP Monitor — Leak Detection"
        0x3C -> "EVAP Monitor — Purge Flow"
        0x3D -> "EVAP Monitor — Vent Valve"
        0x41 -> "EGR Monitor"
        0x42 -> "EGR Monitor — VVT"
        0x51 -> "Secondary Air Monitor"
        0x61 -> "Thermostat Monitor"
        0xA1 -> "NMHC Catalyst Monitor"
        0xA2 -> "NOx / SCR Catalyst Monitor"
        0xA3 -> "Boost Pressure Monitor"
        0xA4 -> "Exhaust Gas Sensor Monitor"
        0xA5 -> "PM Filter Monitor"
        else -> "Monitor MID ${hex2(mid)}"
    }

    /** Test names for the common (MID, TID) pairs; the fallback keeps the numeric ids. */
    fun testName(mid: Int, tid: Int): String {
        val specific = when (mid) {
            0x01, 0x0B -> when (tid) {
                0x0B -> "Misfire count — current cycle"
                0x0C -> "Misfire count — previous cycle"
                else -> null
            }
            0x21, 0x22 -> when (tid) {
                0x86 -> "Switch time — rich to lean"
                0x87 -> "Switch time — lean to rich"
                else -> null
            }
            0x3B, 0x3C, 0x3D -> when (tid) {
                0x0B -> "Vapor pressure"
                0x0C -> "Purge flow"
                else -> null
            }
            else -> null
        }
        return specific ?: "Test 0x${hex2(tid)}"
    }
}
