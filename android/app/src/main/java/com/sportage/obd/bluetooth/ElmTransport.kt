package com.sportage.obd.bluetooth

import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for any ELM327 link: classic Bluetooth SPP, Bluetooth LE
 * GATT or (later) WiFi/TCP. ObdService talks to this interface only.
 */
interface ElmTransport {
    val connectionState: StateFlow<ConnectionState>

    /** Establishes the link. Returns true when connected. */
    suspend fun connect(device: android.bluetooth.BluetoothDevice): Boolean

    /** Sends one ELM327 command and returns the response (prompt stripped). */
    suspend fun sendCommand(command: String, timeoutMs: Long = 2500): String

    /**
     * Sends a standard OBD service request and returns the reassembled ISO-TP payload.
     *
     * Multi-frame replies (Mode 09 VIN/calibration, Mode 06 result blocks) are normalised into a
     * single hex payload by [com.sportage.obd.obd.IsoTp], so callers never have to deal with
     * sequence bytes. When the reply cannot be reassembled the raw adapter text is returned
     * unchanged, so nothing is lost.
     */
    suspend fun sendObdCommand(mode: String, pid: String = "", timeoutMs: Long = 2500): String {
        val command = mode.trim().uppercase() + pid.trim().uppercase()
        val raw = sendCommand(command, timeoutMs)
        val assembled = com.sportage.obd.obd.IsoTp.assembleAll(raw)
        if (assembled.isEmpty()) return raw
        return assembled.maxByOrNull { it.length } ?: raw
    }

    fun disconnect()
}

/**
 * ELM327 wire framing shared by every transport.
 *
 * A command must end with a real carriage return (0x0D). A literal backslash followed by 'r'
 * leaves the line unterminated, so the adapter never answers and every read times out.
 */
object Elm327Command {

    /** Terminates [command] with the carriage return the ELM327 expects. */
    fun format(command: String): String = command.trim() + "\r"

    /** Strips the ELM prompt and collapses line endings into single spaces. */
    fun cleanResponse(raw: String): String = raw
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace(">", "")
        .trim()
}
