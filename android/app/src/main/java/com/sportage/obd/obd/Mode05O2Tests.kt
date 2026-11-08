package com.sportage.obd.obd

/**
 * One Mode 05 (non-CAN oxygen sensor monitor) test result.
 *
 * Mode 05 was replaced by Mode 06 on CAN vehicles (the 2016+ Sportage QL is CAN), so it is
 * offered as a compatibility path for older or partial adapters. The TID groups follow
 * SAE J1979: `0x01–0x0F` output-voltage tests, `0x10–0x1F` constant-voltage tests,
 * `0x21–0x2F` output-voltage tests with limits, `0x30–0x3F` response tests,
 * `0x40–0x4F` pressure/other tests. Values outside those groups are shown as raw bytes.
 */
data class O2TestResult(
    val tid: Int,
    val label: String,
    val value: Float?,
    val trimPct: Float?,
    val unit: String,
    val rawBytes: String
) {
    val displayValue: String
        get() = value?.let {
            if (unit == "V") String.format(java.util.Locale.US, "%.3f V", it)
            else String.format(java.util.Locale.US, "%.1f%s", it, if (unit.isEmpty()) "" else " $unit")
        } ?: rawBytes

    val displayTrim: String?
        get() = trimPct?.let { String.format(java.util.Locale.US, "%+.1f %%", it) }
}

data class O2TestReport(
    val supportedTids: List<Int> = emptyList(),
    val results: List<O2TestResult> = emptyList(),
    val raw: String = ""
)

/**
 * SAE J1979 Service 05 decoder.
 *
 * Sensor voltage bytes are reported with the documented 0.005 V resolution (`raw / 200`) and the
 * trimming bytes as `(raw - 128) * 100 / 128 %`. Nothing is marked pass/fail: Mode 05 limits are
 * vehicle specific and are not published in the standard, so the app shows the measurement and
 * leaves the judgement to the technician.
 */
object Mode05O2Parser {

    fun supportCommands(): List<String> = (0x00..0xE0 step 0x20).map { "05" + Mode06TestParser.hex2(it) }

    fun decodeSupportedTids(raw: String): List<Int> {
        val hex = IsoTp.assemble(raw).uppercase()
        val tids = mutableListOf<Int>()
        var search = 0
        while (true) {
            val index = hex.indexOf("45", search)
            if (index < 0 || hex.length < index + 12) break
            search = index + 2
            val base = hex.substring(index + 2, index + 4).toIntOrNull(16) ?: continue
            val bits = hex.substring(index + 4, index + 12).chunked(2).mapNotNull { it.toIntOrNull(16) }
            if (bits.size != 4) continue
            bits.forEachIndexed { byteIndex, byte ->
                for (bit in 7 downTo 0) {
                    if ((byte shr bit) and 0x01 == 1) {
                        val tid = base + 1 + byteIndex * 8 + (7 - bit)
                        if (tid in 1..0xFF && tid !in tids) tids.add(tid)
                    }
                }
            }
        }
        return tids
    }

    /** Decodes the `45 <TID> <bytes...>` response for one test id. */
    fun decodeTest(raw: String, tid: Int): List<O2TestResult> {
        val hex = IsoTp.assemble(raw).uppercase()
        val marker = "45" + Mode06TestParser.hex2(tid)
        val results = mutableListOf<O2TestResult>()
        var search = 0
        while (true) {
            val index = hex.indexOf(marker, search)
            if (index < 0) break
            search = index + marker.length
            val data = hex.substring(minOf(search, hex.length))
            results.addAll(decodePayload(tid, data))
        }
        return results
    }

    /** Decodes every supported TID response into one report. */
    fun decode(responses: Map<Int, String>): O2TestReport {
        val supported = mutableListOf<Int>()
        val results = mutableListOf<O2TestResult>()
        val raws = mutableListOf<String>()
        for ((tid, raw) in responses) {
            raws.add(raw)
            if (tid % 0x20 == 0) supported.addAll(decodeSupportedTids(raw))
            else results.addAll(decodeTest(raw, tid))
        }
        return O2TestReport(supported.distinct().sorted(), results, raws.joinToString("\n"))
    }

    private fun decodePayload(tid: Int, data: String): List<O2TestResult> {
        val bytes = data.chunked(2).mapNotNull { it.toIntOrNull(16) }
        val label = label(tid)
        return when {
            // 0x01–0x0F and 0x21–0x2F: [voltage, trim] pairs, one pair per sensor.
            tid in 0x01..0x0F || tid in 0x21..0x2F -> {
                if (bytes.size < 2) {
                    listOf(O2TestResult(tid, label, null, null, "", data))
                } else {
                    bytes.chunked(2).mapIndexed { index, pair ->
                        if (pair.size < 2) {
                            O2TestResult(tid, "$label · sensor ${index + 1}", null, null, "", pair.joinToString(""))
                        } else {
                            O2TestResult(
                                tid = tid,
                                label = "$label · sensor ${index + 1}",
                                value = pair[0] / 200f,
                                trimPct = (pair[1] - 128) * 100f / 128f,
                                unit = "V",
                                rawBytes = pair.joinToString("") { it.toString(16).uppercase().padStart(2, '0') }
                            )
                        }
                    }
                }
            }

            // 0x10–0x1F and 0x30–0x3F: four-byte constant/response values.
            tid in 0x10..0x1F || tid in 0x30..0x3F -> {
                if (bytes.size < 4) {
                    listOf(O2TestResult(tid, label, null, null, "", data))
                } else {
                    bytes.chunked(4).mapIndexed { index, quad ->
                        if (quad.size < 4) {
                            O2TestResult(tid, "$label · ${index + 1}", null, null, "", quad.joinToString(""))
                        } else {
                            val raw = (quad[0] shl 8) or quad[1]
                            O2TestResult(
                                tid = tid,
                                label = "$label · ${index + 1}",
                                value = raw / 200f,
                                trimPct = null,
                                unit = "V",
                                rawBytes = quad.joinToString("") { it.toString(16).uppercase().padStart(2, '0') }
                            )
                        }
                    }
                }
            }

            // 0x40–0x4F: pressure / other — keep the raw measurement, no invented unit.
            else -> {
                if (bytes.isEmpty()) listOf(O2TestResult(tid, label, null, null, "", data))
                else listOf(
                    O2TestResult(tid, label, null, null, "", data)
                )
            }
        }
    }

    fun label(tid: Int): String = when (tid) {
        in 0x01..0x0F -> "O2 sensor output voltage"
        in 0x10..0x1F -> "O2 sensor constant voltage"
        in 0x21..0x2F -> "O2 sensor voltage with limits"
        in 0x30..0x3F -> "O2 sensor response"
        in 0x40..0x4F -> "O2 sensor pressure / other"
        else -> "O2 sensor test 0x${Mode06TestParser.hex2(tid)}"
    }
}
