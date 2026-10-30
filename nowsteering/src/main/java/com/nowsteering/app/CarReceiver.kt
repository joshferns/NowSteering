package com.nowsteering.app

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Logs Bluetooth audio connections; starts the service when the chosen car connects, stops it on disconnect. */
class CarReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val dev = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val name = try { dev.name } catch (e: SecurityException) { "(no Bluetooth permission)" }
        val isCar = dev.address == Prefs.carAddress
        val tag = "'$name' ${dev.address}${if (isCar) " (CAR)" else ""}"
        val state = i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        val connected = when (i.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> { NsLog.log("BT", "ACL connected $tag"); true }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> { NsLog.log("BT", "ACL disconnected $tag"); false }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                NsLog.log("BT", "A2DP state $state $tag (2=connected, 0=disconnected)")
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> true
                    BluetoothProfile.STATE_DISCONNECTED -> false
                    else -> return
                }
            }
            else -> return
        }
        if (!isCar) return
        if (!Prefs.autoStart) {
            NsLog.log("BT", "car ${if (connected) "connected" else "disconnected"} but auto-start is off")
            return
        }
        if (connected) {
            if (SteeringService.instance == null) SteeringService.start(ctx, "car connected")
        } else if (SteeringService.instance != null) {
            SteeringService.stop(ctx, "car disconnected")
        }
    }
}
