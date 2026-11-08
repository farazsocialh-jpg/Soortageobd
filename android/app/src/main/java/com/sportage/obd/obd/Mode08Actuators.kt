package com.sportage.obd.obd

/**
 * One actuator the app can ask the ECU to toggle through SAE J1979 Mode 08.
 *
 * [tid] is the standard test id group, [name] the component it drives, and [observeHint] the
 * Mode 01 signal a technician should watch to confirm the component actually reacted. Ids are
 * the generic SAE J1979 test groups; a Kia ECU may reject any of them, in which case the app
 * reports the rejection instead of pretending the test ran.
 */
data class ActuatorDefinition(
    val tid: Int,
    val name: String,
    val detail: String,
    val observeHint: String,
    val severity: ActuatorSeverity = ActuatorSeverity.CAUTION
)

enum class ActuatorSeverity { CAUTION, HIGH }

/**
 * Curated Mode 08 catalogue.
 *
 * Only components that are safe to toggle with the vehicle stationary are listed. Nothing here
 * changes fuel delivery, ignition timing or braking; those are deliberately excluded.
 */
object Mode08Actuators {

    val catalog: List<ActuatorDefinition> = listOf(
        ActuatorDefinition(
            tid = 0x01,
            name = "EVAP leak detection pump",
            detail = "Runs the evaporative-emissions leak pump so the system can be checked for leaks.",
            observeHint = "Fuel tank pressure (PID 32/53) should move while the pump runs.",
            severity = ActuatorSeverity.CAUTION
        ),
        ActuatorDefinition(
            tid = 0x02,
            name = "EVAP purge valve",
            detail = "Opens the canister purge valve to draw stored vapour into the intake.",
            observeHint = "Short-term fuel trim and intake pressure should react; a stuck valve will not change them.",
            severity = ActuatorSeverity.CAUTION
        ),
        ActuatorDefinition(
            tid = 0x03,
            name = "EVAP vent valve",
            detail = "Closes the canister vent so the tank can be sealed for a leak test.",
            observeHint = "Tank pressure should hold while the valve is closed.",
            severity = ActuatorSeverity.CAUTION
        ),
        ActuatorDefinition(
            tid = 0x10,
            name = "Cooling fan relay",
            detail = "Commands the engine cooling fan on. The fan can start without warning.",
            observeHint = "Listen for the fan; coolant temperature should fall slowly.",
            severity = ActuatorSeverity.HIGH
        ),
        ActuatorDefinition(
            tid = 0x11,
            name = "A/C compressor clutch relay",
            detail = "Engages the air-conditioning compressor clutch.",
            observeHint = "RPM should dip slightly and the vent air should turn cold.",
            severity = ActuatorSeverity.HIGH
        ),
        ActuatorDefinition(
            tid = 0x12,
            name = "Fuel pump relay",
            detail = "Runs the fuel pump. The engine bay will be pressurised.",
            observeHint = "Fuel rail pressure (where supported) should rise; listen for the pump.",
            severity = ActuatorSeverity.HIGH
        ),
        ActuatorDefinition(
            tid = 0x13,
            name = "Secondary air pump",
            detail = "Runs the secondary air injection pump used during cold start.",
            observeHint = "Listen for the pump; O2 readings may go lean while it runs.",
            severity = ActuatorSeverity.CAUTION
        ),
        ActuatorDefinition(
            tid = 0x20,
            name = "MIL / check-engine lamp",
            detail = "Toggles the instrument-cluster malfunction indicator lamp.",
            observeHint = "Watch the dashboard lamp itself.",
            severity = ActuatorSeverity.CAUTION
        )
    )

    fun find(tid: Int): ActuatorDefinition? = catalog.firstOrNull { it.tid == tid }

    /** `08 <TID>` — start (or acknowledge) one actuator test. */
    fun startCommand(tid: Int): String = "08" + Mode06TestParser.hex2(tid)

    /**
     * `08 <TID> 00` — ask the ECU to end the test.
     *
     * Most ECUs time the test out on their own; this command is a best-effort stop request and
     * the response is reported verbatim, so a refused stop is visible instead of assumed.
     */
    fun stopCommand(tid: Int): String = startCommand(tid) + "00"

    /** `08` on its own — enumerate the supported actuator test ids. */
    fun probeCommand(): String = "08"

    /**
     * Mode 08 must never run with the vehicle moving. A missing speed reading is treated as
     * "not safe", because an unknown speed is not evidence that the car is stationary.
     */
    fun isSafeToRun(speedKmh: Int?): Boolean = speedKmh != null && speedKmh <= 0

    fun safetyWarning(definition: ActuatorDefinition): String = buildString {
        append("This sends Mode 08 to the ECU and will activate \"${definition.name}\". ")
        append("Only continue with the vehicle parked, the transmission in P or N, and the parking brake applied. ")
        if (definition.severity == ActuatorSeverity.HIGH) {
            append("This actuator can start a motor or pressurise a fuel system — keep hands, tools and loose clothing clear. ")
        }
        append(definition.detail)
    }
}

/** Result of the most recent actuator command, surfaced to the UI. */
data class ActuatorTestState(
    val activeTid: Int? = null,
    val activeName: String? = null,
    val isRunning: Boolean = false,
    val lastCommand: String? = null,
    val lastResponse: String? = null,
    val message: String? = null
)
