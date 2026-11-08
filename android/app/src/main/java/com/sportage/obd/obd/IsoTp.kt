package com.sportage.obd.obd

/**
 * ISO 15765-2 (ISO-TP) reassembly for ELM327 text output.
 *
 * Mode 09 (and long Mode 06 / Mode 02) replies span several CAN frames. Depending on `ATH1` and
 * on whether the adapter assembles frames itself, the same reply reaches the app as
 *
 *  1. one assembled payload:              `49 02 01 4B 4E 44 50 4D ...`
 *  2. numbered multi-line frames:         `0: 49 02 01 4B 4E ...` / `1: 44 50 4D ...`
 *  3. frames with CAN headers:            `7E8 10 14 49 02 01 ...` / `7E8 21 4E 44 ...`
 *
 * [assembleAll] normalises all three into payload hex strings. It never invents bytes: a reply
 * that cannot be reassembled is returned as the hex that was received.
 *
 * The Bluetooth transport flattens line endings, so a multi-frame reply with headers can arrive
 * as a single line (`7E8 10 14 49 ... 7E8 21 4E ...`). Frames are therefore split on the CAN
 * header tokens themselves rather than on newlines.
 */
object IsoTp {

    private const val DEFAULT_KEY = "~"

    /** Adapter chatter that is never part of a payload. */
    private val chatter = listOf(
        "SEARCHING", "NO DATA", "STOPPED", "UNABLE", "ERROR", "BUS INIT", "CAN ERROR",
        "BUS BUSY", "BUFFER FULL", "WAITING"
    )

    /**
     * Every complete message found in [raw] as upper-case hex without separators.
     * When no CAN headers are present, all received bytes form one message.
     */
    fun assembleAll(raw: String): List<String> {
        val segments = segments(raw)
        if (segments.isEmpty()) return emptyList()

        val headerless = segments.filter { it.first == null }
        val withHeaders = segments.filter { it.first != null }

        if (withHeaders.isEmpty()) {
            // ELM327 assembled the reply (or printed numbered continuation lines): the bytes are
            // already the payload. Frame index bytes are handled by the Mode 09/06 parsers.
            val bytes = headerless.flatMap { it.second }
            return if (bytes.isEmpty()) emptyList() else listOf(hex(bytes))
        }

        val collected = LinkedHashMap<String, MutableList<Int>>()
        val totals = HashMap<String, Int>()
        for ((key, bytes) in withHeaders) {
            val header = key ?: continue
            if (bytes.isEmpty()) continue
            val target = collected.getOrPut(header) { mutableListOf() }
            val frameType = bytes[0] and 0xF0
            when {
                frameType == 0x00 -> {
                    val declared = bytes[0] and 0x0F
                    if (declared in 1..7) target.addAll(bytes.drop(1).take(declared)) else target.addAll(bytes.drop(1))
                }

                frameType == 0x10 -> {
                    totals[header] = ((bytes[0] and 0x0F) shl 8) or (bytes.getOrNull(1) ?: 0)
                    target.addAll(bytes.drop(2))
                }

                frameType == 0x20 -> target.addAll(bytes.drop(1))

                else -> target.addAll(bytes)
            }
        }

        val messages = mutableListOf<String>()
        for ((header, data) in collected) {
            if (data.isEmpty()) continue
            val total = totals[header]
            val payload = if (total != null && total in 1..data.size) data.take(total) else data
            messages.add(hex(payload))
        }
        return messages
    }

    /** Longest reassembled payload, or the raw hex when nothing could be assembled. */
    fun assemble(raw: String): String =
        assembleAll(raw).maxByOrNull { it.length } ?: flattenHex(raw)

    /** All payload hex concatenated in receive order (keeps repeated frame headers). */
    fun flattenHex(raw: String): String {
        val bytes = segments(raw).flatMap { it.second }
        return if (bytes.isEmpty()) "" else hex(bytes)
    }

    /**
     * Splits the reply into frames. A three-hex-digit token (`7E8`) can only be a CAN header,
     * because data tokens always carry an even number of hex digits; everything that follows a
     * header belongs to that frame until the next header appears.
     */
    private fun segments(raw: String): List<Pair<String?, List<Int>>> {
        val result = mutableListOf<Pair<String?, List<Int>>>()
        var key: String? = null
        var buffer = mutableListOf<Int>()
        var sawHeader = false

        fun flush() {
            if (buffer.isNotEmpty()) result.add(key to buffer)
            buffer = mutableListOf()
        }

        for (token in tokens(raw)) {
            val upper = token.uppercase()
            if (chatter.any { upper.contains(it) }) continue
            if (token.length == 3 && isHex(token)) {
                flush()
                key = upper
                sawHeader = true
                continue
            }
            buffer.addAll(bytesOf(token))
        }
        flush()
        if (!sawHeader) {
            // Keep a single headerless payload even if it was printed across lines.
            val bytes = result.flatMap { it.second }
            return if (bytes.isEmpty()) emptyList() else listOf(null to bytes)
        }
        return result
    }

    /** Hex tokens from the reply, with the ELM `0:`, `1: ...` line numbers removed. */
    private fun tokens(raw: String): List<String> =
        raw
            .replace('>', ' ')
            .replace(Regex("[0-9A-Fa-f]{1,2}\\s*:"), " ")
            .split(' ', '\t', '\n', '\r')
            .filter { it.isNotBlank() }

    private fun isHex(token: String): Boolean =
        token.isNotEmpty() && token.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }

    /**
     * Expands one token into bytes. A token must be an even-length run of hex digits: this is
     * what keeps adapter chatter such as "SEARCHING..." or "NO DATA" out of the payload.
     */
    private fun bytesOf(token: String): List<Int> {
        val clean = token.trim()
        if (clean.length < 2 || clean.length % 2 != 0) return emptyList()
        if (!clean.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) return emptyList()
        return clean.chunked(2).map { it.toInt(16) }
    }

    private fun hex(bytes: List<Int>): String =
        bytes.joinToString("") { it.toString(16).uppercase().padStart(2, '0') }
}
