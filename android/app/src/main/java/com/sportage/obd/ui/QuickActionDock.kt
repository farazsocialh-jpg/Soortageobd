package com.sportage.obd.ui

import android.view.KeyEvent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sportage.obd.ui.theme.*

/** One calm action row. It never fabricates song metadata or playback state. */
@Composable
fun QuickActionDock(onMusic: () -> Unit, onMediaKey: (Int) -> Unit, onPhone: () -> Unit,
    onApps: () -> Unit, onBluetooth: () -> Unit, logging: Boolean, canLog: Boolean, onLogging: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = DarkSurface()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                DockAction(Icons.Default.Headphones, "Open music app", onMusic)
                if (wide) DockAction(Icons.Default.SkipPrevious, "Previous track") { onMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                DockAction(Icons.Default.PlayCircle, "Play or pause media") { onMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                if (wide) DockAction(Icons.Default.SkipNext, "Next track") { onMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
                DockAction(Icons.Default.Phone, "Open phone dialer", onPhone)
                DockAction(Icons.Default.Apps, "Open installed apps", onApps)
                Box {
                    DockAction(Icons.Default.MoreVert, "Quick actions") { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        if (!wide) {
                            DropdownMenuItem(text = { Text("Previous track") },
                                onClick = { menu = false; onMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) })
                            DropdownMenuItem(text = { Text("Next track") },
                                onClick = { menu = false; onMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) })
                        }
                        DropdownMenuItem(text = { Text("OBD Bluetooth") }, onClick = { menu = false; onBluetooth() })
                        DropdownMenuItem(text = { Text(if (logging) "Stop OBD recording" else "Record OBD to CSV") },
                            enabled = canLog || logging, onClick = { menu = false; onLogging() })
                    }
                }
            }
        }
    }
}

@Composable
private fun DockAction(icon: ImageVector, description: String, action: () -> Unit) {
    IconButton(action, Modifier.size(48.dp)) { Icon(icon, description, tint = SecondaryText(), modifier = Modifier.size(23.dp)) }
}

internal fun openPhoneDialer(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, "No phone dialer is installed on this device.", Toast.LENGTH_LONG).show()
    }
}
