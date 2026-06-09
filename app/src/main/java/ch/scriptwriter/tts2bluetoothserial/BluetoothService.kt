// FILE: app/src/main/java/ch/scriptwriter/tts2bluetoothserial/BluetoothService.kt
// STATUS: FULL ABSOLUTE CONTROL (MASTER ANKER)
// DATE: 2026-03-29

package ch.scriptwriter.tts2bluetoothserial

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

@SuppressLint("MissingPermission")
class BluetoothService(
    private val context: Context,
    private val deviceAddress: String,
    private val onConnectionStateChange: (Boolean) -> Unit
) {
    private val TAG = "BluetoothService"
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isConnecting = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Verbunden mit GATT Server")
                isConnecting = false
                gatt.discoverServices()
                onConnectionStateChange(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Verbindung verloren (Status: $status). Starte Reconnect-Timer...")
                isConnecting = false
                closeConnection()
                onConnectionStateChange(false)
                
                // Aggressiverer Reconnect: 3 Sekunden statt 5, und wir steuern es manuell
                handler.postDelayed({ 
                    Log.d(TAG, "Triggering manual reconnect...")
                    connect() 
                }, 3000)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services entdeckt")
            }
        }
    }

    fun connect() {
        if (isConnecting) return
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val device = adapter?.getRemoteDevice(deviceAddress)

        if (device == null) {
            Log.e(TAG, "Gerät nicht gefunden")
            return
        }

        isConnecting = true
        Log.d(TAG, "Verbindungsaufbau zu $deviceAddress (autoConnect=false)")
        
        // autoConnect = false gibt uns die volle Kontrolle über den Verbindungsversuch.
        // Wir versuchen aktiv zu verbinden, anstatt auf das OS zu warten.
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun closeConnection() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Schließen der Verbindung: ${e.message}")
        }
        bluetoothGatt = null
    }

    fun stopMonitoring() {
        handler.removeCallbacksAndMessages(null)
        closeConnection()
    }
}
