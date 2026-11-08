package com.sportage.obd.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var expandedSection by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", color = PrimaryText()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = PrimaryText())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground())
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ElectricCyan())) {
                        append("https://farazsocialh.github.io/sportageobd/privacy")
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
                color = ElectricCyan(),
                fontSize = 14.sp,
                textAlign = TextAlign.Start
            )

            Text(
                "Version 1.0 · Effective: 6 November 2026",
                color = SecondaryText(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.SansSerif
            )

            Divider(color = DarkSurfaceElevated(), modifier = Modifier.padding(vertical = 4.dp))

            SectionCard(
                title = "1. Introduction",
                content = """
                    Sportage OBD-II Intelligence ("we," "our," or "the App") is developed by Faraz Social. This Privacy Policy explains how the App collects, uses, stores, and shares information when you use it on your Android device. By installing or using the App, you consent to the practices described here.

                    The App is a two-in-one product: (1) an automotive OBD-II diagnostic tool that reads vehicle data from an ELM327 Bluetooth adapter, and (2) a car-launcher/cockpit replacement that provides widgets, navigation shortcuts, media controls, and quick actions while driving. Different features involve different types of data; this policy is organized so you can understand each part separately.
                """.trimIndent()
            )

            SectionCard(
                title = "2. Information We Collect",
                content = """
                    A. Vehicle and diagnostic data (local only)
                    • Real-time sensor readings from your vehicle's OBD-II port via an ELM327 adapter: engine speed (RPM), vehicle speed, coolant temperature, intake air temperature, fuel level, throttle position, engine load, battery voltage, mass airflow, fuel trim, timing advance, fuel economy, fuel used, odometer-style trip distance, and gear estimate.
                    • Diagnostic Trouble Codes (DTCs) read from the ECU: stored, pending, and permanent codes, plus freeze-frame snapshots and readiness monitor status.
                    • Adapter and ECU metadata: adapter type (Bluetooth Classic or BLE), protocol, VIN (if reported), and raw calibration/identification responses.

                    B. Device and usage data (minimal)
                    • The App does not use advertising SDKs, fingerprinting, or device-advertising identifiers.
                    • We collect only what is needed to keep the App working: crash logs you choose to share (see Section 4), and basic in-app usage events for analytics (see Section 3). No contact list, call log, SMS, audio, camera, or microphone data is collected by default.
                    • If you voluntarily enable cloud sync (future feature), your diagnostic history and trip logs may be stored on our servers; when enabled, you will be shown what is synced and can disable it at any time.

                    C. Location data
                    • The App uses coarse or fine location only to support the embedded map and weather display. Location is processed on-device; it is not sent to us for advertising or profiling. Location permission can be denied without breaking core OBD diagnostics.
                """.trimIndent()
            )

            SectionCard(
                title = "3. How We Use Information",
                content = """
                    • OBD and diagnostic data is used solely to show live telemetry, graphs, DTC scan results, fuel analytics, alerts, and trip records inside the App.
                    • Device/usage analytics help us understand whether key flows (Bluetooth pairing, scan completion, launcher usage) work as expected, so we can fix failures and improve stability. Analytics are tied to an anonymous app installation ID, not to your personal identity.
                    • Crash logs help us diagnose crashes. You choose whether to share them.
                """.trimIndent()
            )

            SectionCard(
                title = "4. Data Sharing and Third Parties",
                content = """
                    • We do not sell your data.
                    • Crash reporting and analytics: If you choose to share a crash report, the report is sent through our crash-reporting provider (Firebase Crashlytics / Google). Analytics events are sent to Firebase Analytics. Processing is governed by Google's terms and privacy policy. See their documentation for details on how Google processes such data.
                    • No vehicle data, DTCs, fuel history, or trip logs are shared with third parties by default. Any future cloud-sync feature will be opt-in and described in the App before you enable it.
                    • When you use features that open other apps (for example, navigation, phone dialer, music players, or sharing a CSV), those apps and their own privacy policies handle the data; this App does not receive information back from them.
                """.trimIndent()
            )

            SectionCard(
                title = "5. Bluetooth and OBD Adapter Permissions",
                content = """
                    • The App requests Bluetooth permissions (BLUETOOTH / BLUETOOTH_ADMIN on older Android, and BLUETOOTH_CONNECT / BLUETOOTH_SCAN on Android 12+) so it can find and connect to ELM327 adapters.
                    • Location permission (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION) is requested because Android requires location permission to scan Bluetooth devices; the App uses it only for Bluetooth scanning and map/weather features, not for tracking.
                    • Foreground service and notification permissions allow the App to keep OBD streaming active when you switch to navigation or other apps, and to show alerts.
                    • QUERY_ALL_PACKAGES is requested only so the launcher can list installed apps for its app drawer; it is not used for advertising or unrelated profiling.
                    • You can deny any permission; the App will degrade gracefully (for example, no Bluetooth scanning without Bluetooth permission, no map/weather without location).
                """.trimIndent()
            )

            SectionCard(
                title = "6. Data Storage and Retention",
                content = """
                    • Diagnostic, trip, fuel, and preference data is stored locally on your device by default (shared preferences and local JSON/CSV files). No OBD readings or DTCs leave your device unless you explicitly enable a future cloud-sync feature and consent to it.
                    • CSV telemetry logs you start manually are stored in your device's public storage so you can share them with other apps. You control when logging starts and stops.
                    • Crash logs are retained by our crash-reporting provider according to its retention settings.
                    • When you uninstall the App, local data stored only on the device is removed with it (subject to your device's uninstall behavior for shared storage).
                """.trimIndent()
            )

            SectionCard(
                title = "7. Children and Legal Basis",
                content = """
                    • The App is not directed to children under 13. We do not knowingly collect personal information from children. If you believe a child has used the App in a way that involves personal data, contact us and we will investigate.
                    • Where we rely on consent for any processing, you may withdraw it through the App's settings or by disabling the relevant feature. Withdrawal does not affect the lawfulness of prior processing.
                """.trimIndent()
            )

            SectionCard(
                title = "8. Your Rights and Controls",
                content = """
                    Depending on your jurisdiction, you may have rights such as access, correction, deletion, portability, restriction, and objection. To exercise them, contact us using the information in Section 9. For locally stored data, you can also clear in-app data through your device settings or by resetting the relevant App screens (for example, trip/fuel history screens offer clearing where applicable).
                """.trimIndent()
            )

            SectionCard(
                title = "9. Changes to This Policy",
                content = """
                    We may update this policy to reflect new features or legal requirements. Material changes will be communicated in the App or via the store listing where practicable. The "Effective" date at the top indicates the current version.
                """.trimIndent()
            )

            SectionCard(
                title = "10. Contact",
                content = """
                    If you have questions or requests about this policy or the App's data practices, contact:

                    Faraz Social
                    Email: farazsocialh@gmail.com
                    GitHub: https://github.com/farazsocialh-jpg/sportageobd

                    이 정책은 아랍어 / English / 한국어 등 필요 시 번역될 수 있으며, 해석상 충돌 시 영문본이 우선합니다.
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface()),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = PrimaryText(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content.split("\n\n").forEach { paragraph ->
                if (paragraph.isBlank()) return@forEach
                Text(
                    paragraph.trim(),
                    color = SecondaryText(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
