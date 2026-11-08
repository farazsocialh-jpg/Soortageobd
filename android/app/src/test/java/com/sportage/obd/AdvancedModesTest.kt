package com.sportage.obd

import com.sportage.obd.obd.DiagnosticReport
import com.sportage.obd.obd.DiagnosticSnapshot
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.DtcSeverity
import com.sportage.obd.obd.FreezeFrame
import com.sportage.obd.obd.FreezeFrameItem
import com.sportage.obd.obd.IsoTp
import com.sportage.obd.obd.Mode05O2Parser
import com.sportage.obd.obd.Mode06TestParser
import com.sportage.obd.obd.Mode08Actuators
import com.sportage.obd.obd.Mode09Parser
import com.sportage.obd.obd.PidDecoders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the advanced diagnostic modes added on top of Mode 01:
 * ISO-TP reassembly, Mode 09 vehicle information, Mode 06 MID/TID test results,
 * Mode 05 oxygen sensor tests, Mode 08 actuator gating and the report builder.
 */
class AdvancedModesTest {

    // --- ISO-TP reassembly ---

    @Test
    fun testAssembleMultiFrameWithCanHeaders() {
        val raw = """
            7E8 10 14 49 02 01 4B 4E 44 50 4D 43 41 43 35 4E
            7E8 21 37 31 32 33 34 35 36 00 00 00
        """.trimIndent()
        val messages = IsoTp.assembleAll(raw)
        assertEquals(1, messages.size)
        assertEquals("4902014B4E44504D434143354E" + "37313233343536", messages[0])
    }

    @Test
    fun testAssembleNumberedLines() {
        val raw = "0: 49 02 01 4B 4E 44 50 4D 43 41 43 35 4E\n1: 37 31 32 33 34 35 36"
        val assembled = IsoTp.assemble(raw)
        assertEquals("4902014B4E44504D434143354E" + "37313233343536", assembled)
    }

    @Test
    fun testAssembleSingleFrameAndChatter() {
        val raw = "SEARCHING...\nNO DATA"
        assertTrue(IsoTp.assembleAll(raw).isEmpty())
        assertEquals("410C1AF8", IsoTp.assemble("41 0C 1A F8"))
    }

    // --- Mode 09 ---

    @Test
    fun testDecodeVinFromAssembledPayload() {
        val raw = "49 02 01 4B 4E 44 50 4D 43 41 43 35 4E 37 31 32 33 34 35 36"
        assertEquals("KNDPMCAC5N7123456", Mode09Parser.decodeVin(raw))
    }

    @Test
    fun testDecodeVinFromFramesWithIndexBytes() {
        val raw = "49 02 01 4B 4E 44 50 4D 43 41 43 35 4E 49 02 02 37 31 32 33 34 35 36"
        assertEquals("KNDPMCAC5N7123456", Mode09Parser.decodeVin(raw))
    }

    @Test
    fun testDecodeVinRejectsShortPayload() {
        assertNull(Mode09Parser.decodeVin("49 02 01 4B 4E 44"))
        assertNull(Mode09Parser.decodeVin("NO DATA"))
    }

    @Test
    fun testDecodeCalibrationIdAndEcuName() {
        val cal = "49 04 01 4B 49 41 2D 51 4C 2D 32 30 32 36"
        val ecu = "49 0A 01 53 50 4F 52 54 41 47 45"
        assertEquals("KIA-QL-2026", Mode09Parser.decodeCalibrationId(cal))
        assertEquals("SPORTAGE", Mode09Parser.decodeEcuName(ecu))
    }

    @Test
    fun testDecodeCvn() {
        val raw = "49 06 01 12 34 56 78 9A BC DE F0 11 22 33 44 55 66 77 88 99"
        assertEquals("12345678" + "9ABCDEF0" + "11223344" + "55667788", Mode09Parser.decodeCvn(raw))
    }

    @Test
    fun testDecodeIdentityLeavesMissingPidsNull() {
        val identity = Mode09Parser.decode(
            mapOf(Mode09Parser.PID_VIN to "49 02 01 4B 4E 44 50 4D 43 41 43 35 4E 37 31 32 33 34 35 36")
        )
        assertEquals("KNDPMCAC5N7123456", identity.vin)
        assertNull(identity.calibrationId)
        assertNull(identity.ecuName)
        assertTrue(identity.hasAnyValue)
    }

    // --- Mode 06 ---

    @Test
    fun testDecodeSupportedMids() {
        assertEquals(listOf(1, 32), Mode06TestParser.decodeSupportedMids("46 00 80 00 00 01"))
        assertEquals(listOf(33, 49, 59), Mode06TestParser.decodeSupportedMids("46 20 80 00 80 20"))
        assertTrue(Mode06TestParser.decodeSupportedMids("NO DATA").isEmpty())
    }

    @Test
    fun testDecodeMode06TestsWithLimits() {
        val raw = "46 21 86 70 00 FA 00 32 01 F4 87 70 02 EE 00 32 01 F4"
        val report = Mode06TestParser.decodeTests(raw, 0x21)
        assertEquals(2, report.tests.size)

        val pass = report.tests[0]
        assertEquals(0x86, pass.tid)
        assertEquals("ms", pass.unitLabel)
        assertEquals(250f, pass.value, 0.01f)
        assertEquals(50f, pass.min, 0.01f)
        assertEquals(500f, pass.max, 0.01f)
        assertEquals(true, pass.passed)

        val fail = report.tests[1]
        assertEquals(750f, fail.value, 0.01f)
        assertEquals(false, fail.passed)
        assertEquals(1, report.failures.size)
        assertEquals("Catalyst Monitor — Bank 1", fail.monitorName)
    }

    @Test
    fun testDecodeMode06WithoutLimitsIsUnknown() {
        val report = Mode06TestParser.decodeTests("46 01 0B 01 00 00 00 00 00 00", 0x01)
        assertEquals(1, report.tests.size)
        assertNull(report.tests[0].passed)
        assertEquals(1, report.unknowns.size)
    }

    @Test
    fun testDecodeMode06AppliesUasScaling() {
        // UAS 0x63 = volts (unit 6), scaling 10^-3; value 0x012C (300) => 0.300 V.
        val report = Mode06TestParser.decodeTests("46 31 01 63 01 2C 00 58 04 B0", 0x31)
        val test = report.tests[0]
        assertEquals("V", test.unitLabel)
        assertEquals(0.3f, test.value, 0.0005f)
        assertEquals(1.2f, test.max, 0.0005f)
        assertEquals("Misfire Monitor — General", Mode06TestParser.monitorName(0x01))
    }

    @Test
    fun testMode06SortingByFailure() {
        val report = Mode06TestParser.decode(
            mapOf(
                0x00 to "46 00 80 00 00 00",
                0x01 to "46 01 0B 01 00 00 00 00 00 10",
                0x21 to "46 21 86 70 00 FA 00 32 01 F4 87 70 02 EE 00 32 01 F4"
            )
        )
        assertEquals(3, report.tests.size)
        val ordered = report.sortedByFailure()
        assertEquals(false, ordered.first().passed)
        assertEquals(true, ordered.last().passed)
    }

    // --- Mode 05 ---

    @Test
    fun testDecodeMode05SupportedTids() {
        assertEquals(listOf(1, 32), Mode05O2Parser.decodeSupportedTids("45 00 80 00 00 01"))
    }

    @Test
    fun testDecodeMode05VoltageAndTrim() {
        val results = Mode05O2Parser.decodeTest("45 01 64 80 4B 7E", 0x01)
        assertEquals(2, results.size)
        assertEquals(0.5f, results[0].value!!, 0.001f)
        assertEquals(0f, results[0].trimPct!!, 0.01f)
        assertEquals(0.375f, results[1].value!!, 0.001f)
        assertEquals(-1.5625f, results[1].trimPct!!, 0.01f)
        assertEquals("O2 sensor output voltage", Mode05O2Parser.label(0x01))
    }

    // --- Mode 08 ---

    @Test
    fun testActuatorCommands() {
        assertEquals("0810", Mode08Actuators.startCommand(0x10))
        assertEquals("081000", Mode08Actuators.stopCommand(0x10))
        assertEquals("08", Mode08Actuators.probeCommand())
    }

    @Test
    fun testActuatorMotionGate() {
        assertTrue(Mode08Actuators.isSafeToRun(0))
        assertFalse(Mode08Actuators.isSafeToRun(1))
        assertFalse(Mode08Actuators.isSafeToRun(null))
    }

    @Test
    fun testActuatorCatalogIsUniqueAndKnown() {
        val ids = Mode08Actuators.catalog.map { it.tid }
        assertEquals(ids.size, ids.distinct().size)
        assertNotNull(Mode08Actuators.find(0x10))
        assertNull(Mode08Actuators.find(0xEE))
        assertTrue(Mode08Actuators.catalog.all { it.name.isNotBlank() && it.observeHint.isNotBlank() })
    }

    // --- Report ---

    @Test
    fun testDiagnosticReportIncludesEverySection() {
        val snapshot = DiagnosticSnapshot(
            storedDtcs = listOf(
                DtcItem("P0420", "Powertrain / Exhaust", "Catalyst efficiency below threshold", DtcSeverity.WARNING, "Converter aging")
            ),
            pendingDtcs = listOf(
                DtcItem("P0128", "Powertrain / Engine", "Thermostat below regulating temperature", DtcSeverity.ADVISORY, "Stuck thermostat")
            ),
            freezeFrame = FreezeFrame(
                dtc = DtcItem("P0420", "Powertrain / Exhaust", "Catalyst efficiency below threshold", DtcSeverity.WARNING, "Converter aging"),
                items = listOf(FreezeFrameItem("Engine RPM", "1750"), FreezeFrameItem("Vehicle Speed", "72 km/h"))
            ),
            mode06 = Mode06TestParser.decodeTests("46 21 86 70 00 FA 00 32 01 F4 87 70 02 EE 00 32 01 F4", 0x21),
            readiness = PidDecoders.decodeReadiness("41 01 82 07 65 00")
        )
        val report = DiagnosticReport.build(snapshot)
        assertTrue(report.contains("SPORTAGE OBD DIAGNOSTIC REPORT"))
        assertTrue(report.contains("STORED CODES (Mode 03)"))
        assertTrue(report.contains("P0420"))
        assertTrue(report.contains("PENDING CODES (Mode 07)"))
        assertTrue(report.contains("P0128"))
        assertTrue(report.contains("FREEZE FRAME (Mode 02)"))
        assertTrue(report.contains("Engine RPM: 1750"))
        assertTrue(report.contains("ON-BOARD MONITOR TESTS (Mode 06)"))
        assertTrue(report.contains("FAIL"))
        assertTrue(report.contains("READINESS (Mode 01 PID 01)"))
        assertTrue(DiagnosticReport.fileName(snapshot.generatedAt).endsWith(".txt"))
    }

    @Test
    fun testDiagnosticReportWithNoDataIsHonest() {
        val report = DiagnosticReport.build(DiagnosticSnapshot())
        assertTrue(report.contains("No freeze frame stored."))
        assertTrue(report.contains("No Mode 06 test results read."))
        assertTrue(report.contains("Vehicle information was not read"))
        assertFalse(report.contains("FAIL"))
    }
}
