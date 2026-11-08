package com.sportage.obd.ui.driving

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay

enum class DrivingState { PARKED, TRANSITION, DRIVING }

val DrivingState.allowsDiagnostics: Boolean
    get() = this == DrivingState.PARKED

/**
 * Pure, testable monitor for the driving-state machine.
 *
 * - [PARKED]: speed is 0 for at least [parkedZeroMs].
 * - [DRIVING]: speed > [driveThresholdKmh].
 * - [TRANSITION]: after leaving driving, until the hysteresis window [hysteresisMs] closes.
 */
class DrivingStateMonitor(
    val driveThresholdKmh: Double = 5.0,
    val parkedZeroMs: Long = 3_000L,
    val hysteresisMs: Long = 5_000L,
    val unknownParkedMs: Long = 10_000L
) {
    var state: DrivingState = DrivingState.PARKED
        private set

    private var zeroSinceMs: Long? = null
    private var slowSinceMs: Long? = null
    private var unknownSinceMs: Long? = null

    /** True while a time-based transition is still pending. */
    val hasPendingTimer: Boolean
        get() = state == DrivingState.TRANSITION ||
            (unknownSinceMs != null && state != DrivingState.DRIVING)

    fun update(speedKmh: Double?, nowMs: Long): DrivingState {
        if (speedKmh == null) return updateUnknown(nowMs)

        unknownSinceMs = null
        if (speedKmh > driveThresholdKmh) {
            state = DrivingState.DRIVING
            zeroSinceMs = null
            slowSinceMs = null
            return state
        }

        if (speedKmh <= 0.0) {
            zeroSinceMs = zeroSinceMs ?: nowMs
        } else {
            zeroSinceMs = null
        }

        when (state) {
            DrivingState.DRIVING -> {
                state = DrivingState.TRANSITION
                slowSinceMs = nowMs
            }
            DrivingState.TRANSITION -> {
                val zeroFor = zeroSinceMs?.let { nowMs - it } ?: 0L
                val slowFor = slowSinceMs?.let { nowMs - it } ?: 0L
                if (zeroFor >= parkedZeroMs || slowFor >= hysteresisMs) {
                    state = DrivingState.PARKED
                    zeroSinceMs = null
                    slowSinceMs = null
                }
            }
            DrivingState.PARKED -> {
                if (speedKmh > 0.0) {
                    state = DrivingState.TRANSITION
                    slowSinceMs = nowMs
                    zeroSinceMs = null
                }
            }
        }
        return state
    }

    private fun updateUnknown(nowMs: Long): DrivingState {
        if (state == DrivingState.DRIVING) return state
        val since = unknownSinceMs ?: nowMs
        unknownSinceMs = since
        if (nowMs - since >= unknownParkedMs) {
            state = DrivingState.DRIVING
        }
        return state
    }
}

val LocalDrivingState = staticCompositionLocalOf { DrivingState.PARKED }

@Composable
fun rememberDrivingState(speedKmh: Int?): DrivingState {
    val monitor = remember { DrivingStateMonitor() }
    var state by remember { mutableStateOf(monitor.state) }
    val speed by rememberUpdatedState(speedKmh)
    LaunchedEffect(speedKmh) {
        state = monitor.update(speed?.toDouble(), System.currentTimeMillis())
        while (monitor.hasPendingTimer) {
            delay(500)
            state = monitor.update(speed?.toDouble(), System.currentTimeMillis())
        }
    }
    return state
}
