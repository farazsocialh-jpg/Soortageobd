package com.sportage.obd.obd

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything a full diagnostic scan produced, in one immutable snapshot.
 * Fields are optional so a report can be generated from a partial scan.
 */
data class DiagnosticSnapshot(
    val vehicle: VehicleIdentity? = null,
    val storedDtcs: List<DtcItem> = emptyList(),
    val pendingDtcs: List<DtcItem> = emptyList(),
    val permanentDtcs: List<DtcItem> = emptyList(),
    val freezeFrame: FreezeFrame? = null,
    val mode06: Mode06TestReport? = null,
    val o2Tests: O2TestReport? = null,
    val readiness: ReadinessState? = null,
    val generatedAt: Long = System.currentTimeMillis()
) {
    val totalDtcCount: Int get() = storedDtcs.size + pendingDtcs.size + permanentDtcs.size
    val hasAnyData: Boolean
        get() = vehicle?.hasAnyValue == true || totalDtcCount > 0 || freezeFrame != null ||
            mode06?.tests?.isNotEmpty() == true || o2Tests?.results?.isNotEmpty() == true || readiness != null
}

/**
 * Builds the shareable plain-text diagnostic report.
 *
 * The report states what the ECU returned and nothing else: it never labels a fault as
 * confirmed, never assigns a repair, and explicitly marks checks that were not performed.
 */
object DiagnosticReport {

    fun fileName(timestamp: Long): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(timestamp))
        return "sportage-diagnostic-report-$stamp.txt"
    }

    fun build(snapshot: DiagnosticSnapshot): String = buildString {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(snapshot.generatedAt))
        appendLine("SPORTAGE OBD DIAGNOSTIC REPORT")
        appendLine("Generated: $stamp")
        appendLine("Source: ELM327-compatible adapter, SAE J1979 standard services")
        appendLine("=".repeat(56))
        appendLine()

        appendLine("VEHICLE")
        val vehicle = snapshot.vehicle
        if (vehicle == null || !vehicle.hasAnyValue) {
            appendLine("  Vehicle information was not read (Mode 09 unavailable).")
        } else {
            appendLine("  VIN: ${vehicle.vin ?: "not reported"}")
            appendLine("  Calibration ID: ${vehicle.calibrationId ?: "not reported"}")
            appendLine("  Calibration verification: ${vehicle.calibrationVerificationNumber ?: "not reported"}")
            appendLine("  ECU name: ${vehicle.ecuName ?: "not reported"}")
        }
        appendLine()

        appendLine("READINESS (Mode 01 PID 01)")
        val readiness = snapshot.readiness
        if (readiness == null) {
            appendLine("  Not read.")
        } else {
            appendLine("  MIL / check engine lamp: ${if (readiness.milOn) "ON" else "OFF"}")
            appendLine("  Stored code count reported by ECU: ${readiness.storedDtcCount}")
            val monitors = readiness.sparkIgnitionMonitors + readiness.compressionIgnitionMonitors
            if (monitors.isEmpty()) {
                appendLine("  No readiness monitors reported.")
            } else {
                monitors.forEach { monitor ->
                    appendLine("    ${monitor.name}: ${if (monitor.complete) "COMPLETE" else "NOT READY"}")
                }
            }
        }
        appendLine()

        appendDtcSection("STORED CODES (Mode 03)", snapshot.storedDtcs)
        appendDtcSection("PENDING CODES (Mode 07)", snapshot.pendingDtcs)
        appendDtcSection("PERMANENT CODES (Mode 0A)", snapshot.permanentDtcs)

        appendLine("FREEZE FRAME (Mode 02)")
        val freezeFrame = snapshot.freezeFrame
        if (freezeFrame == null || freezeFrame.items.isEmpty()) {
            appendLine("  No freeze frame stored.")
        } else {
            appendLine("  Captured for: ${freezeFrame.dtc?.code ?: "the last confirmed fault"}")
            freezeFrame.items.forEach { item ->
                appendLine("    ${item.label}: ${item.value}")
            }
        }
        appendLine()

        appendLine("ON-BOARD MONITOR TESTS (Mode 06)")
        val mode06 = snapshot.mode06
        if (mode06 == null || mode06.tests.isEmpty()) {
            appendLine("  No Mode 06 test results read.")
            if (mode06 != null && mode06.supportedMids.isNotEmpty()) {
                appendLine("  Supported MIDs: ${mode06.supportedMids.joinToString(" ") { Mode06TestParser.hex2(it) }}")
            }
        } else {
            val failures = mode06.failures
            appendLine("  Tests read: ${mode06.tests.size} · failed: ${failures.size} · no limits: ${mode06.unknowns.size}")
            mode06.sortedByMonitor().forEach { test ->
                val verdict = when (test.passed) {
                    true -> "PASS"
                    false -> "FAIL"
                    null -> "NO LIMITS"
                }
                appendLine(
                    "    [${Mode06TestParser.hex2(test.mid)}] ${test.monitorName} · " +
                        "$verdict · ${test.testName}: ${test.displayValue}${test.unitSuffix} " +
                        "(limits ${test.displayRange})"
                )
            }
        }
        appendLine()

        appendLine("OXYGEN SENSOR TESTS (Mode 05)")
        val o2 = snapshot.o2Tests
        if (o2 == null || o2.results.isEmpty()) {
            appendLine("  No Mode 05 oxygen sensor tests read.")
        } else {
            o2.results.forEach { result ->
                val trim = result.displayTrim?.let { " · trim $it" } ?: ""
                appendLine("    TID ${Mode06TestParser.hex2(result.tid)} · ${result.label}: ${result.displayValue}$trim")
            }
        }
        appendLine()

        appendLine("NOTES")
        appendLine("  • Values are exactly what the ECU reported; nothing is inferred.")
        appendLine("  • Mode 06 pass/fail uses the limits the ECU returned for each test.")
        appendLine("  • Mode 08 actuator tests are not part of this report (they are live actions).")
        appendLine("  • This report supports a diagnosis; it is not a substitute for a technician.")
    }

    private fun StringBuilder.appendDtcSection(title: String, dtcs: List<DtcItem>) {
        appendLine(title)
        if (dtcs.isEmpty()) {
            appendLine("  None reported.")
        } else {
            dtcs.forEach { dtc ->
                appendLine("  ${dtc.code} · ${dtc.severity.name} · ${dtc.description}")
                appendLine("      ${dtc.system} · probable cause: ${dtc.probableCause}")
            }
        }
        appendLine()
    }
}
