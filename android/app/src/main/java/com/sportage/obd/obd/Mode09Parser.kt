package com.sportage.obd.obd

/**
 * Static vehicle metadata reported through SAE J1979 Mode 09.
 *
 * Every value is optional: an ECU may answer some PIDs and reject others, and a value is only
 * ever populated from bytes the ECU actually returned.
 */
data class VehicleIdentity(
    val vin: String? = null,
    val calibrationId: String? = null,
    val calibrationVerificationNumber: String? = null,
    val ecuName: String? = null,
    /** Raw adapter text per PID, kept for the terminal/report so nothing is silently dropped. */
    val rawResponses: Map<String, String> = emptyMap()
) {
    val hasAnyValue: Boolean
        get() = vin != null || calibrationId != null || calibrationVerificationNumber != null || ecuName != null
}

/**
 * Mode 09 decoder.
 *
 * Mode 09 replies are multi-frame, and adapters print them in several shapes (see [IsoTp]).
 * Each PID is decoded from the payload bytes that follow its `49 <pid>` marker; frame index
 * bytes such as `49 02 01`/`49 02 02` are treated as framing, not as data.
 */
object Mode09Parser {

    const val PID_VIN = "02"
    const val PID_CALIBRATION_ID = "04"
    const val PID_CVN = "06"
    const val PID_ECU_NAME = "0A"

    /** Commands sent once per connection handshake, in display order. */
    val handshakePids: List<String> = listOf(PID_VIN, PID_CALIBRATION_ID, PID_CVN, PID_ECU_NAME)

    /** Builds a [VehicleIdentity] from the raw response of each Mode 09 PID. */
    fun decode(responses: Map<String, String>): VehicleIdentity {
        val raw = responses.toMap()
        return VehicleIdentity(
            vin = responses[PID_VIN]?.let { decodeVin(it) },
            calibrationId = responses[PID_CALIBRATION_ID]?.let { decodeAscii(it, PID_CALIBRATION_ID) },
            calibrationVerificationNumber = responses[PID_CVN]?.let { decodeCvn(it) },
            ecuName = responses[PID_ECU_NAME]?.let { decodeAscii(it, PID_ECU_NAME) },
            rawResponses = raw
        )
    }

    /** 17-character VIN, or null when the reply is truncated/unreadable. */
    fun decodeVin(raw: String): String? =
        decodeAscii(raw, PID_VIN)?.filter { it.isLetterOrDigit() }?.takeIf { it.length == 17 }

    /** Calibration ID (0904) as printable text. */
    fun decodeCalibrationId(raw: String): String? = decodeAscii(raw, PID_CALIBRATION_ID)

    /** ECU name (090A) as printable text. */
    fun decodeEcuName(raw: String): String? = decodeAscii(raw, PID_ECU_NAME)

    /**
     * Calibration verification numbers (0906). These are binary values, not text, so they are
     * returned as upper-case hex; a full reply carries four 4-byte CVNs (32 hex characters).
     */
    fun decodeCvn(raw: String): String? {
        // payloadHex already removed the frame-index byte, so what remains is raw CVN bytes:
        // up to four 4-byte values (32 hex characters).
        val hex = payloadHex(raw, PID_CVN) ?: return null
        val even = if (hex.length % 2 == 0) hex else hex.dropLast(1)
        return even.take(32).takeIf { it.length >= 8 }
    }

    /**
     * Reassembles the payload for [pid] and returns its printable characters.
     * Handles both an assembled multi-frame payload and repeated `49 02 0N` frame headers.
     */
    fun decodeAscii(raw: String, pid: String): String? {
        val hex = payloadHex(raw, pid) ?: return null
        val text = buildString {
            hex.chunked(2).forEach { byte ->
                val value = byte.toIntOrNull(16) ?: return@forEach
                val char = value.toChar()
                if (char.isLetterOrDigit() || char == ' ' || char == '-' || char == '/' || char == '.' || char == '_') {
                    append(char)
                }
            }
        }.trim()
        return text.ifBlank { null }
    }

    /**
     * Returns every data byte that follows a `49 <pid>` marker, concatenated as hex.
     *
     * Frame index bytes are removed: `49 02 01 4B 4E 44` and `49 02 02 50 4D 43` become
     * `4B4E44504D43`. A trailing partial byte is ignored.
     */
    private fun payloadHex(raw: String, pid: String): String? {
        if (raw.isBlank()) return null
        val payloads = IsoTp.assembleAll(raw)
        val sources = if (payloads.isEmpty()) listOf(IsoTp.flattenHex(raw)) else payloads
        val marker = "49" + pid.uppercase()

        val collected = StringBuilder()
        var found = false
        for (payload in sources) {
            val hex = payload.uppercase()
            var from = 0
            while (true) {
                val start = hex.indexOf(marker, from)
                if (start < 0) break
                found = true
                val dataStart = start + marker.length
                // Repeated frame headers inside one payload (49 02 01 ... 49 02 02 ...).
                val next = hex.indexOf(marker, dataStart)
                val end = if (next < 0) hex.length else next
                var chunk = hex.substring(dataStart, minOf(end, hex.length))
                // Drop the one-byte frame/message index that follows each marker.
                if (chunk.length >= 2) chunk = chunk.substring(2)
                collected.append(chunk)
                from = if (next < 0) hex.length else next
            }
        }
        if (!found) return null
        val hex = collected.toString().filter { it.isDigit() || it in 'A'..'F' }
        return hex.ifEmpty { null }
    }
}
