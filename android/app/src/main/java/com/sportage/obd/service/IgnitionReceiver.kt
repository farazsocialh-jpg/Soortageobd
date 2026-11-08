package com.sportage.obd.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Ignition & Bluetooth Event Receiver
 *
 * Automatically triggers ObdService reconnection and live data stream logging when:
 * 1. Car ignition powers on (device receives 12V charger power via ACTION_POWER_CONNECTED)
 * 2. Car OBD-II ELM327 Bluetooth dongle establishes ACL link (ACTION_ACL_CONNECTED)
 * 3. Bluetooth is toggled ON (BluetoothAdapter.ACTION_STATE_CHANGED)
 * 4. Android system finishes booting (BOOT_COMPLETED)
 */
class IgnitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IgnitionReceiver"
        private const val PREFS_NAME = "sportage_obd_service"
        private const val KEY_LAST_DEVICE = "last_adapter_address"
        private const val KEY_AUTOCONNECT = "bluetooth_autoconnect"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received ignition/bluetooth trigger broadcast: $action")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoConnectEnabled = prefs.getBoolean(KEY_AUTOCONNECT, true)
        if (!isAutoConnectEnabled) {
            Log.d(TAG, "Auto-connect is disabled in settings. Skipping trigger.")
            return
        }

        val rememberedAddress = prefs.getString(KEY_LAST_DEVICE, null)
        if (rememberedAddress.isNullOrBlank()) {
            Log.d(TAG, "No previously paired OBD adapter remembered. Skipping auto-connect.")
            return
        }

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                // If the connected Bluetooth device is our target ELM327 adapter
                if (device != null && device.address.equals(rememberedAddress, ignoreCase = true)) {
                    Log.i(TAG, "Car ELM327 Bluetooth ACL link detected (${device.address})! Auto-connecting...")
                    triggerServiceConnect(context)
                }
            }

            Intent.ACTION_POWER_CONNECTED -> {
                Log.i(TAG, "Car ignition 12V power detected (ACTION_POWER_CONNECTED)! Auto-connecting to OBD...")
                triggerServiceConnect(context)
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "Bluetooth switched to STATE_ON. Initiating OBD auto-connect...")
                    triggerServiceConnect(context)
                }
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Device boot completed. Waking OBD service in auto-connect standby...")
                triggerServiceConnect(context)
            }
        }
    }

    private fun triggerServiceConnect(context: Context) {
        try {
            val serviceIntent = Intent(context, ObdService::class.java).apply {
                action = ObdService.ACTION_AUTO_CONNECT
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ObdService on ignition trigger", e)
        }
    }
}
