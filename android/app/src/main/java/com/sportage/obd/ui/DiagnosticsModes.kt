package com.sportage.obd.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.obd.ActuatorDefinition
import com.sportage.obd.obd.ActuatorTestState
import com.sportage.obd.obd.Mode05O2Parser
import com.sportage.obd.obd.Mode06TestParser
import com.sportage.obd.obd.Mode06TestReport
import com.sportage.obd.obd.Mode08Actuators
import com.sportage.obd.obd.O2TestReport
import com.sportage.obd.obd.VehicleIdentity
import com.sportage.obd.ui.theme.AmberWarning
import com.sportage.obd.ui.theme.CriticalRed
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.DeepShade
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.OnAccent
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.TealAccent
import com.sportage.obd.viewmodel.ObdViewModel
import kotlinx.coroutines.launch

/** Shares the plain-text diagnostic report through any app that accepts text. */
fun shareDiagnosticReport(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Sportage OBD diagnostic report")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Share diagnostic report"))
}

@Composable
fun ShareReportButton(viewModel: ObdViewModel?) {
    val context = LocalContext.current
    Button(
        onClick = { shareDiagnosticReport(context, viewModel?.buildDiagnosticReport() ?: "") },
        enabled = viewModel != null,
        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Icon(Icons.Default.Share, contentDescription = null, tint = ElectricCyan(), modifier = Modifier.height(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Report", color = ElectricCyan(), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
    }
}

@Composable
private fun ModeActionButton(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.height(14.dp), color = OnAccent(), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text("Reading...", color = OnAccent(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = OnAccent(), modifier = Modifier.height(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = OnAccent(), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    accent: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        color = DeepShade(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, DarkSurfaceElevated()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                color = if (accent) ElectricCyan() else SecondaryText(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            content()
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
        Text(value, color = PrimaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
    }
}

// ---------------------------------------------------------------------------
// MODE 09 — vehicle information
// ---------------------------------------------------------------------------

@Composable
fun VehicleIdentityContent(identity: VehicleIdentity?, viewModel: ObdViewModel?) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeActionButton("Read Identity (09)", busy, viewModel != null) {
            busy = true
            scope.launch {
                viewModel?.readVehicleIdentityNow()
                busy = false
            }
        }
        if (identity == null || !identity.hasAnyValue) {
            InfoCard("Mode 09 vehicle information", accent = true) {
                Text(
                    "No vehicle information read yet. VIN, calibration ID, calibration verification numbers " +
                        "and the ECU name are requested once per connection, because they do not change while " +
                        "driving. A 2016+ Sportage ECU answers Mode 09 only while the ignition is on.",
                    color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                )
            }
        } else {
            InfoCard("VEHICLE IDENTIFICATION", accent = true) {
                Text(
                    identity.vin ?: "VIN not reported",
                    color = PrimaryText(), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif
                )
                KeyValueRow("Calibration ID", identity.calibrationId ?: "not reported")
                KeyValueRow("Calibration verification", identity.calibrationVerificationNumber ?: "not reported")
                KeyValueRow("ECU name", identity.ecuName ?: "not reported")
                Text(
                    "Stored per adapter so a reconnect shows the last known identity immediately; " +
                        "a live read always replaces it.",
                    color = SecondaryText(), fontSize = 10.sp, fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MODE 06 — on-board monitor tests
// ---------------------------------------------------------------------------

@Composable
fun Mode06TestsContent(report: Mode06TestReport?, viewModel: ObdViewModel?) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failuresOnly by remember { mutableStateOf(false) }
    var sortByFailure by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeActionButton("Scan Monitor Tests (06)", busy, viewModel != null) {
                busy = true
                scope.launch {
                    viewModel?.refreshMode06TestsNow()
                    busy = false
                }
            }
            Button(
                onClick = { failuresOnly = !failuresOnly },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (failuresOnly) CriticalRed() else DarkSurfaceElevated()
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Failures", color = if (failuresOnly) OnAccent() else SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
            }
            Button(
                onClick = { sortByFailure = !sortByFailure },
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    if (sortByFailure) "Sort: fail first" else "Sort: monitor ID",
                    color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                )
            }
        }

        if (report == null || report.tests.isEmpty()) {
            InfoCard("Mode 06 monitor tests", accent = true) {
                Text(
                    if (report == null) {
                        "Not scanned. Mode 06 returns the ECU's own self-test counts against its internal limits — " +
                            "a failing test is often the first sign of a problem, before a DTC is stored."
                    } else {
                        "The ECU reported no Mode 06 test results. Some ECUs answer only the support bitmap, " +
                            "and many Kia models expose these tests only on their manufacturer-specific services."
                    },
                    color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                )
                if (report != null && report.supportedMids.isNotEmpty()) {
                    Text(
                        "Supported MIDs: " + report.supportedMids.joinToString(" ") { Mode06TestParser.hex2(it) },
                        color = SecondaryText(), fontSize = 10.sp, fontFamily = FontFamily.SansSerif
                    )
                }
            }
        } else {
            InfoCard("SUMMARY", accent = true) {
                KeyValueRow("Tests read", report.tests.size.toString())
                KeyValueRow("Passed", report.passes.size.toString())
                KeyValueRow("Failed", report.failures.size.toString())
                KeyValueRow("No limits reported", report.unknowns.size.toString())
            }
            val visible = (if (failuresOnly) report.failures else (if (sortByFailure) report.sortedByFailure() else report.sortedByMonitor()))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible) { test ->
                    val color = when (test.passed) {
                        true -> TealAccent()
                        false -> CriticalRed()
                        null -> AmberWarning()
                    }
                    Surface(
                        color = DeepShade(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "MID ${Mode06TestParser.hex2(test.mid)} · ${test.monitorName}",
                                    color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    when (test.passed) {
                                        true -> "PASS"
                                        false -> "FAIL"
                                        null -> "NO LIMITS"
                                    },
                                    color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif
                                )
                            }
                            Text(test.testName, color = PrimaryText(), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "TID ${Mode06TestParser.hex2(test.tid)} · UAS ${Mode06TestParser.hex2(test.uas)}",
                                    color = SecondaryText(), fontSize = 10.sp, fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    "${test.displayValue}${test.unitSuffix}",
                                    color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif
                                )
                            }
                            Text(
                                "Limits: ${test.displayRange}",
                                color = SecondaryText(), fontSize = 10.sp, fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MODE 05 — oxygen sensor tests
// ---------------------------------------------------------------------------

@Composable
fun O2TestsContent(report: O2TestReport?, viewModel: ObdViewModel?) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeActionButton("Scan O2 Tests (05)", busy, viewModel != null) {
            busy = true
            scope.launch {
                viewModel?.refreshO2TestsNow()
                busy = false
            }
        }
        if (report == null || report.results.isEmpty()) {
            InfoCard("Mode 05 oxygen sensor tests", accent = true) {
                Text(
                    "Mode 05 is the pre-CAN oxygen sensor monitor. A CAN vehicle such as the Sportage QL " +
                        "normally answers Mode 06 instead, so an empty result here is expected rather than a fault. " +
                        "It is kept as a compatibility path for older adapters and ECUs.",
                    color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(report.results) { result ->
                    Surface(
                        color = DeepShade(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkSurfaceElevated()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "TID ${Mode06TestParser.hex2(result.tid)}",
                                    color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    Mode05O2Parser.label(result.tid),
                                    color = ElectricCyan(), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif
                                )
                            }
                            Text(result.label, color = PrimaryText(), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Reading", color = SecondaryText(), fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                                Text(result.displayValue, color = PrimaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                            }
                            result.displayTrim?.let {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Short-term trim", color = SecondaryText(), fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                                    Text(it, color = PrimaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                                }
                            }
                            Text(
                                "Raw: ${result.rawBytes.ifBlank { "—" }} · no pass/fail limits are published for Mode 05",
                                color = SecondaryText(), fontSize = 10.sp, fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MODE 08 — actuator tests
// ---------------------------------------------------------------------------

@Composable
fun ActuatorTestsContent(
    state: ActuatorTestState,
    speedKmh: Int?,
    viewModel: ObdViewModel?
) {
    val scope = rememberCoroutineScope()
    var pending: ActuatorDefinition? by remember { mutableStateOf(null) }
    val safe = Mode08Actuators.isSafeToRun(speedKmh)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoCard("BIDIRECTIONAL CONTROL (MODE 08)", accent = true) {
            Text(
                when {
                    speedKmh == null -> "Vehicle speed is unknown, so actuator tests are disabled. An unknown speed is not proof the car is stationary."
                    !safe -> "Vehicle speed is $speedKmh km/h. Actuator tests are only allowed at 0 km/h."
                    else -> "Vehicle stationary. Each test asks for confirmation before anything is activated."
                },
                color = if (safe) TealAccent() else AmberWarning(),
                fontSize = 11.sp, fontFamily = FontFamily.SansSerif
            )
            if (state.message != null) {
                Text(state.message, color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
            }
            if (state.lastCommand != null) {
                KeyValueRow("Last command", state.lastCommand)
            }
            if (state.lastResponse != null) {
                KeyValueRow("ECU response", state.lastResponse.take(120))
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Mode08Actuators.catalog) { definition ->
                Surface(
                    color = DeepShade(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (definition.severity == com.sportage.obd.obd.ActuatorSeverity.HIGH) CriticalRed().copy(alpha = 0.6f)
                        else DarkSurfaceElevated()
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "TID ${Mode06TestParser.hex2(definition.tid)}",
                                color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                            )
                            if (definition.severity == com.sportage.obd.obd.ActuatorSeverity.HIGH) {
                                Text("HIGH IMPACT", color = CriticalRed(), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                            }
                        }
                        Text(definition.name, color = PrimaryText(), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text(definition.detail, color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                        Text("Verify by: ${definition.observeHint}", color = SecondaryText(), fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pending = definition },
                                enabled = safe && viewModel != null,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OnAccent(), modifier = Modifier.height(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Start test", color = OnAccent(), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                            }
                            Button(
                                onClick = {
                                    scope.launch { viewModel?.runActuatorTest(definition.tid, start = false) }
                                },
                                enabled = safe && viewModel != null && state.activeTid == definition.tid,
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Stop", color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { definition ->
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = AmberWarning()) },
            title = {
                Text("Activate ${definition.name}?", fontFamily = FontFamily.SansSerif, color = PrimaryText())
            },
            text = {
                Text(
                    Mode08Actuators.safetyWarning(definition),
                    fontFamily = FontFamily.SansSerif, fontSize = 11.sp, color = SecondaryText()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pending = null
                        scope.launch { viewModel?.runActuatorTest(definition.tid, start = true) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed())
                ) {
                    Text("Run test", fontFamily = FontFamily.SansSerif, color = OnAccent())
                }
            },
            dismissButton = {
                Button(
                    onClick = { pending = null },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated())
                ) {
                    Text("Cancel", fontFamily = FontFamily.SansSerif, color = SecondaryText())
                }
            },
            containerColor = DeepShade()
        )
    }
}

// ---------------------------------------------------------------------------
// Report card
// ---------------------------------------------------------------------------

@Composable
fun DiagnosticReportCard(viewModel: ObdViewModel?, hasData: Boolean) {
    val context = LocalContext.current
    val storedCount = viewModel?.dtcList?.collectAsState()?.value?.size ?: 0
    val pendingCount = viewModel?.pendingDtcList?.collectAsState()?.value?.size ?: 0
    val mode06 = viewModel?.mode06Tests?.collectAsState()?.value
    InfoCard("ENHANCED DIAGNOSTIC REPORT", accent = true) {
        Text(
            "Stored codes: $storedCount · pending: $pendingCount · " +
                "Mode 06 failures: ${mode06?.failures?.size ?: 0}",
            color = PrimaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif
        )
        Text(
            "Combines stored, pending and permanent codes, the freeze frame, Mode 06 monitor results, " +
                "oxygen sensor tests and readiness into one plain-text report you can share.",
            color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
        )
        Button(
            onClick = { shareDiagnosticReport(context, viewModel?.buildDiagnosticReport() ?: "") },
            enabled = viewModel != null && hasData,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = OnAccent(), modifier = Modifier.height(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Build & share report", color = OnAccent(), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
        }
    }
}

@Suppress("unused")
@Composable
private fun EmptyCheck() {
    // Keeps a visible, shared empty-state icon in this file for future mode sections.
    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealAccent(), modifier = Modifier.height(20.dp))
    Spacer(Modifier.height(4.dp))
}
