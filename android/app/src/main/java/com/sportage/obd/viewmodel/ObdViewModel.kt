package com.sportage.obd.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.ActuatorTestState
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.FreezeFrame
import com.sportage.obd.obd.Mode06MonitorResult
import com.sportage.obd.obd.Mode06TestReport
import com.sportage.obd.obd.O2TestReport
import com.sportage.obd.obd.ReadinessState
import com.sportage.obd.obd.VehicleIdentity
import com.sportage.obd.repository.ObdRepository
import com.sportage.obd.service.ActiveAlert
import com.sportage.obd.service.AlertThresholds
import com.sportage.obd.service.ObdTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ObdViewModel: Bridges the ObdRepository to Jetpack Compose UI.
 * Handles UI state derivation, graph telemetry history, trip stats,
 * and background operations cleanly decoupled from the UI.
 */
class ObdViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ObdRepository(application.applicationContext)

    val telemetry: StateFlow<ObdTelemetry> = repository.telemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ObdTelemetry())

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    val isPollingPaused: StateFlow<Boolean> = repository.isPollingPaused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isDemoMode: StateFlow<Boolean> = repository.isDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dtcList: StateFlow<List<DtcItem>> = repository.dtcList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingDtcList: StateFlow<List<DtcItem>> = repository.pendingDtcList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val permanentDtcList: StateFlow<List<DtcItem>> = repository.permanentDtcList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val freezeFrame: StateFlow<FreezeFrame?> = repository.freezeFrame
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val readiness: StateFlow<ReadinessState?> = repository.readiness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val mode06MonitorResult: StateFlow<Mode06MonitorResult?> = repository.mode06MonitorResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val vehicleIdentity: StateFlow<VehicleIdentity?> = repository.vehicleIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val mode06Tests: StateFlow<Mode06TestReport?> = repository.mode06Tests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val o2Tests: StateFlow<O2TestReport?> = repository.o2Tests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val actuatorState: StateFlow<ActuatorTestState> = repository.actuatorState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActuatorTestState())

    val vin: StateFlow<String?> = repository.vin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val supportedPids: StateFlow<Set<String>> = repository.supportedPids
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val activeAlerts: StateFlow<List<ActiveAlert>> = repository.activeAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isCsvLogging: StateFlow<Boolean> = repository.isCsvLogging
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isScanningDtc = MutableStateFlow(false)
    val isScanningDtc: StateFlow<Boolean> = _isScanningDtc.asStateFlow()

    // Real-time telemetry history for live graphs (sliding window)
    private val _speedHistory = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val speedHistory: StateFlow<List<Pair<Long, Int>>> = _speedHistory.asStateFlow()

    private val _rpmHistory = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val rpmHistory: StateFlow<List<Pair<Long, Int>>> = _rpmHistory.asStateFlow()

    init {
        // A real 60-second window. Sensor-only state updates must not duplicate core samples.
        viewModelScope.launch {
            repository.telemetry.collect { telem ->
                val now = telem.timestamp
                val spd = telem.speedKmh
                val rpm = telem.rpm

                if (spd == null && rpm == null) {
                    _speedHistory.value = emptyList()
                    _rpmHistory.value = emptyList()
                }
                if (spd != null && _speedHistory.value.lastOrNull()?.first != now) {
                    val updatedSpd = (_speedHistory.value + Pair(now, spd)).filter { it.first >= now - 60_000 }.takeLast(3000)
                    _speedHistory.value = updatedSpd
                }
                if (rpm != null && _rpmHistory.value.lastOrNull()?.first != now) {
                    val updatedRpm = (_rpmHistory.value + Pair(now, rpm)).filter { it.first >= now - 60_000 }.takeLast(3000)
                    _rpmHistory.value = updatedRpm
                }
            }
        }
    }

    fun connectDevice(device: BluetoothDevice, ble: Boolean = false) {
        repository.connectDevice(device, ble)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun togglePausePolling(): Boolean {
        val next = !repository.isPollingPaused.value
        repository.togglePausePolling(next)
        return next
    }

    fun isConnected(): Boolean = connectionState.value is ConnectionState.Connected

    fun setDemoMode(enable: Boolean) {
        repository.setDemoMode(enable)
    }

    suspend fun scanDtcNow(): List<DtcItem> {
        _isScanningDtc.value = true
        return try {
            repository.scanDtcCodes()
        } finally {
            _isScanningDtc.value = false
        }
    }

    suspend fun scanPendingDtcsNow(): List<DtcItem> = repository.scanPendingDtcs()

    suspend fun scanPermanentDtcsNow(): List<DtcItem> = repository.scanPermanentDtcs()

    suspend fun readFreezeFrameNow(): FreezeFrame? = repository.readFreezeFrame()

    suspend fun readReadinessNow(): ReadinessState? = repository.readReadiness()

    suspend fun refreshMode06Monitors() = repository.refreshMode06Monitors()

    suspend fun readVehicleIdentityNow(): VehicleIdentity? = repository.readVehicleIdentity()

    suspend fun refreshMode06TestsNow(): Mode06TestReport? = repository.refreshMode06Tests()

    suspend fun refreshO2TestsNow(): O2TestReport? = repository.refreshO2Tests()

    suspend fun runActuatorTest(tid: Int, start: Boolean = true): ActuatorTestState =
        repository.executeActuatorTest(tid, start)

    fun buildDiagnosticReport(): String = repository.buildDiagnosticReport()

    suspend fun readVinNow(): String? = repository.readVin()

    suspend fun refreshSensorCapabilities() = repository.refreshSensorCapabilities()

    suspend fun readVehicleInformation() = repository.readVehicleInformation()

    suspend fun clearDtcNow(): Boolean = repository.clearDtcCodes()

    fun setFuelCalibration(factor: Float) = repository.setFuelCalibration(factor)

    fun resetTripFuel() = repository.resetTripFuel()

    fun alertThresholds(): AlertThresholds = repository.alertThresholds()

    fun updateAlertThresholds(thresholds: AlertThresholds) {
        repository.updateAlertThresholds(thresholds)
    }

    fun startCsvLogging() = repository.startCsvLogging()

    fun stopCsvLogging() = repository.stopCsvLogging()

    fun csvLogFile(): java.io.File? = repository.csvLogFile()

    fun csvAutostart(): Boolean = repository.csvAutostart()

    fun setCsvAutostart(enabled: Boolean) = repository.setCsvAutostart(enabled)

    fun isAutoConnectEnabled(): Boolean = repository.isAutoConnectEnabled()

    fun setAutoConnectEnabled(enabled: Boolean) = repository.setAutoConnectEnabled(enabled)

    fun startAndBindService() = repository.startAndBindService()

    suspend fun sendRawCommand(cmd: String): String {
        return repository.sendRawCommand(cmd)
    }

    override fun onCleared() {
        super.onCleared()
        // Unbinding leaves the foreground service (and the Bluetooth link) running on purpose:
        // only the UI teardown happens here.
        repository.close()
    }
}
