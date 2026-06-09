// Filename: BrdcastReceiver.kt
// Datum: 2026-03-29
// Funktion: Statischer Hintergrund-Receiver für TTS, Notification-Updates und Reconnect-Events
// Kopplung: Ruft die statische Methode MainActivity.sendBleStatic auf
// Update: SPD-Meldungen werden nun immer durchgelassen (Bypass für Schalter-Logik)

package ch.scriptwriter.tts2bluetoothserial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BrdcastReceiver : BroadcastReceiver() {

    private val TAG = ">>> [BRDCAST-RCV]"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        val action = intent?.action
        val rawMsg = intent?.getStringExtra("msg")

        // 1. Spezialfall: Reconnect (wird immer verarbeitet)
        if (action == "ch.scriptwriter.tts2bluetoothserial.BLE_RECONNECTED") {
            Log.d(TAG, "Reconnect erkannt, prüfe Cache...")
            val prefs = context.getSharedPreferences("TtsCache", Context.MODE_PRIVATE)
            val lastMsg = prefs.getString("last_msg", null)
            
            if (lastMsg != null) {
                Log.d(TAG, "Sende letzte Nachricht erneut: $lastMsg")
                MainActivity.sendBleStatic("NAV:$lastMsg")
            }
            return
        }

        // Sicherheitscheck: Wenn keine Nachricht da ist, abbrechen
        if (rawMsg == null) {
            Log.w(TAG, "Empfangen von $action, aber 'msg' war null.")
            return
        }

        // 2. Lade die Schalter-Zustände
        val navPrefs = context.getSharedPreferences("NaviSettings", Context.MODE_PRIVATE)
        val isNavEnabled = navPrefs.getBoolean("external_nav_enabled", false)
        val isTtsEnabled = navPrefs.getBoolean("tts_enabled", true)
        
        // Hilfsvariable für SPD-Bypass
        val isSpd = rawMsg.startsWith("SPD:")

        // 3. Schalter-Logik
        var shouldSend = false

        when (action) {
            "ch.scriptwriter.tts2bluetoothserial.NOTIFICATION_UPDATE" -> {
                // SPD immer durchlassen, sonst nur wenn NavEnabled
                if (isNavEnabled || isSpd) {
                    shouldSend = true
                } else {
                    Log.d(TAG, "Notification blockiert: Schalter 'Notifications lesen' ist AUS.")
                }
            }
            "ch.scriptwriter.tts2bluetoothserial.TTS_RECEIVED" -> {
                // SPD immer durchlassen, sonst nur wenn TtsEnabled
                if (isTtsEnabled || isSpd) {
                    shouldSend = true
                } else {
                    Log.d(TAG, "TTS blockiert: Schalter 'Sprachausgabe (TTS)' ist AUS.")
                }
            }
            else -> {
                shouldSend = true
            }
        }

        if (!shouldSend) return

        // 4. Formatierung prüfen
        val formatted = if (rawMsg.startsWith("NAV:") ||
            rawMsg.startsWith("SPD:") ||
            rawMsg.startsWith("PKT:") ||
            rawMsg.startsWith("STT:")) {
            rawMsg
        } else {
            "NAV:$rawMsg"
        }

        // 5. Übergabe an MainActivity
        try {
            MainActivity.sendBleStatic(formatted)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Übergabe an MainActivity: ${e.message}")
        }
    }
}
