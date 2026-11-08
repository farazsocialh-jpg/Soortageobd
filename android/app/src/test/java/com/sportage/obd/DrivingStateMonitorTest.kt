package com.sportage.obd

import com.sportage.obd.ui.driving.DrivingState
import com.sportage.obd.ui.driving.DrivingStateMonitor
import org.junit.Assert.assertEquals
import org.junit.Test

class DrivingStateMonitorTest {
    private val monitor = DrivingStateMonitor(
        driveThresholdKmh = 5.0,
        parkedZeroMs = 3_000L,
        hysteresisMs = 5_000L,
        unknownParkedMs = 10_000L
    )

    @Test fun unknown_speed_defaults_to_parked() {
        assertEquals(DrivingState.PARKED, monitor.update(null, 1_000L))
    }

    @Test fun unknown_speed_stays_restrictive_until_fail_safe() {
        assertEquals(DrivingState.PARKED, monitor.update(null, 1_000L))
        assertEquals(DrivingState.PARKED, monitor.update(null, 5_000L))
        assertEquals(DrivingState.PARKED, monitor.update(null, 9_999L))
        assertEquals(DrivingState.PARKED, monitor.update(null, 10_000L))
        assertEquals(DrivingState.PARKED, monitor.update(null, 10_001L))
    }

    @Test fun unknown_speed_fails_to_driving_after_stuck() {
        assertEquals(DrivingState.PARKED, monitor.update(null, 1_000L))
        assertEquals(DrivingState.DRIVING, monitor.update(null, 15_000L))
    }

    @Test fun unknown_speed_does_not_reset_driving() {
        monitor.update(10.0, 1_000L)
        assertEquals(DrivingState.DRIVING, monitor.update(null, 2_000L))
    }

    @Test fun transition_starts_after_leaving_driving() {
        monitor.update(10.0, 1_000L)
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 1_100L))
    }

    @Test fun hysteresis_holds_transition() {
        monitor.update(10.0, 1_000L)         // → DRIVING
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 1_100L)) // → TRANSITION
        assertEquals(DrivingState.TRANSITION, monitor.update(4.0, 4_000L)) // 4>0 clears zero; slowFor 2900<5000
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 6_000L)) // zeroSince=6000; slowFor 4900<5000
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 9_500L))     // zeroFor 3500>=3000 → PARKED
    }

    @Test fun drive_below_threshold_means_stop_before_hysteresis() {
        monitor.update(10.0, 1_000L)
        assertEquals(DrivingState.TRANSITION, monitor.update(4.0, 1_100L))
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 5_000L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 7_000L))
    }

    @Test fun parked_until_zero_then_restart() {
        monitor.update(0.0, 1_000L)
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 2_000L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 4_000L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 10_000L))
    }

    @Test fun zero_then_positive_then_zero() {
        monitor.update(0.0, 1_000L)
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 1_100L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 5_000L))
        assertEquals(DrivingState.DRIVING, monitor.update(11.0, 5_100L))
    }

    @Test fun quick_stop_after_speed() {
        monitor.update(10.0, 1_000L)
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 1_100L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 7_000L))
    }

    @Test fun slow_then_driving() {
        monitor.update(10.0, 1_000L)         // → DRIVING
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 1_100L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 7_000L))     // zeroFor 5900>=3000 → PARKED
        assertEquals(DrivingState.DRIVING, monitor.update(6.0, 7_100L))     // 6>5 → DRIVING
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 8_000L))  // → TRANSITION (hysteresis)
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 13_000L))     // zeroFor 5000>=3000 → PARKED
    }

    @Test fun cycles_in_transition() {
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 1_000L))       // 0 stays PARKED
        assertEquals(DrivingState.DRIVING, monitor.update(11.0, 6_000L))     // 11>5 → DRIVING
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 7_000L))   // → TRANSITION
        assertEquals(DrivingState.TRANSITION, monitor.update(4.0, 8_000L))   // 4>0 clears zero; slowFor 1000<5000
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 13_000L))      // slowFor 6000>=5000 → PARKED
    }

    @Test fun escape_negative_speed() {
        assertEquals(DrivingState.DRIVING, monitor.update(10.0, 1_000L))     // 10>5 → DRIVING
        assertEquals(DrivingState.TRANSITION, monitor.update(0.5, 1_500L))   // 0.5<=5 → TRANSITION (small positive)
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 7_000L))       // slowFor 5500>=5000 → PARKED
    }

    @Test fun negative_speed_does_not_break() {
        assertEquals(DrivingState.DRIVING, monitor.update(10.0, 1_000L))     // 10>5 → DRIVING
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 5_000L))   // → TRANSITION
        assertEquals(DrivingState.TRANSITION, monitor.update(-1.0, 5_500L))  // -1<=0; zeroSince persists; slowFor 500<5000
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 10_500L))      // zeroFor 5500>=3000 → PARKED
    }

    @Test fun driving_resets_zero_revs() {
        monitor.update(10.0, 1_000L)
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 1_100L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 7_000L))
    }

    @Test fun drive_during_transition() {
        monitor.update(10.0, 1_000L)
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 1_100L))
        assertEquals(DrivingState.TRANSITION, monitor.update(3.0, 6_000L)) // 3>0 clears zero; slowFor 4900<5000
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 7_000L))     // slowFor 5900>=5000 → PARKED
    }

    @Test fun two_for_stuck() {
        assertEquals(DrivingState.PARKED, monitor.update(null, 1_000L))
        assertEquals(DrivingState.DRIVING, monitor.update(11.0, 6_000L))
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 7_000L))  // → TRANSITION
        assertEquals(DrivingState.TRANSITION, monitor.update(4.0, 8_000L))   // 4>0 clears zero; slowFor 1000<5000
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 13_000L))      // slowFor 6000>=5000 → PARKED
    }

    @Test fun near_zero_stays_parked() {
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 1_000L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 7_000L))
    }

    @Test fun small_positive_then_zero() {
        assertEquals(DrivingState.TRANSITION, monitor.update(0.5, 1_000L))   // small positive from PARKED → TRANSITION
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 2_000L))   // zeroSince=2000
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 5_500L))       // zeroFor 3500>=3000 → PARKED
    }

    @Test fun hybrid_driving_and_stuck() {
        assertEquals(DrivingState.PARKED, monitor.update(null, 1_000L))
        assertEquals(DrivingState.DRIVING, monitor.update(11.0, 5_000L))
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 6_000L))  // → TRANSITION
        assertEquals(DrivingState.TRANSITION, monitor.update(4.0, 8_000L))   // 4>0 clears zero; slowFor 2000<5000
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 13_000L))      // slowFor 7000>=5000 → PARKED
    }

    @Test fun drive_during_transition_with_zero() {
        monitor.update(10.0, 1_000L)
        assertEquals(DrivingState.TRANSITION, monitor.update(0.0, 1_900L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 10_000L))
    }

    @Test fun long_parked_state() {
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 1_000L))
        assertEquals(DrivingState.PARKED, monitor.update(0.0, 2_000L))
    }

    @Test fun writes_that_state() {
        val m = monitor
        assertEquals(DrivingState.DRIVING, m.update(10.0, 1_000L))          // 10>5 → DRIVING
        assertEquals(DrivingState.DRIVING, m.update(11.0, 5_000L))          // 11>5 → DRIVING
        assertEquals(DrivingState.TRANSITION, m.update(0.0, 6_000L))        // → TRANSITION
        assertEquals(DrivingState.TRANSITION, m.update(4.0, 8_000L))        // 4>0 clears zero; slowFor 2000<5000
        assertEquals(DrivingState.PARKED, m.update(0.0, 13_000L))           // slowFor 7000>=5000 → PARKED
    }
}
