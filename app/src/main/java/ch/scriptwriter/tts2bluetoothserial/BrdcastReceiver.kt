// Filename: BrdcastReceiver.kt
// Datum: 2026-03-28
// Funktion: Statischer Hintergrund-Receiver für TTS und Notification-Updates
// Kopplung: Ruft die statische Methode MainActivity.sendBleStatic auf
// Update: Getrennte Schalter-Logik für NAV und TTS integriert

package ch.scriptwriter.tts2bluetoothserial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BrdcastReceiver : BroadcastReceiver() {

    private val TAG = ">>> [BRDCAST-RCV]"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        // 1. Lade die Schalter-Zustände aus den SharedPreferences
        val navPrefs = context.getSharedPreferences("NaviSettings", Context.MODE_PRIVATE)
        val isNavEnabled = navPrefs.getBoolean("external_nav_enabled", false)
        val isTtsEnabled = navPrefs.getBoolean("tts_enabled", true)

        // 2. Extrahiere die Action und die Nachricht (msg)
        val action = intent?.action
        val rawMsg = intent?.getStringExtra("msg")

        // Sicherheitscheck: Wenn keine Nachricht da ist, abbrechen
        if (rawMsg == null) {
            Log.w(TAG, "Empfangen von $action, aber 'msg' war null.")
            return
        }

        // 3. Schalter-Logik: Filtern basierend auf der Quelle (Action)
        var shouldSend = false

        when (action) {
            "ch.scriptwriter.tts2bluetoothserial.NOTIFICATION_UPDATE" -> {
                if (isNavEnabled) {
                    shouldSend = true
                } else {
                    Log.d(TAG, "Notification blockiert: Schalter 'Notifications lesen' ist AUS.")
                }
            }
            "ch.scriptwriter.tts2bluetoothserial.TTS_RECEIVED" -> {
                if (isTtsEnabled) {
                    shouldSend = true
                } else {
                    Log.d(TAG, "TTS blockiert: Schalter 'Sprachausgabe (TTS)' ist AUS.")
                }
            }
            else -> {
                // Andere Actions (falls vorhanden) werden standardmäßig durchgelassen
                shouldSend = true
            }
        }

        if (!shouldSend) return

        // 4. Logging für den Monitor
        Log.d(TAG, "Action: $action")
        Log.d(TAG, "Inhalt: $rawMsg")

        // 5. Formatierung prüfen (Präfix-Logik analog zur MainActivity)
        val formatted = if (rawMsg.startsWith("NAV:") ||
            rawMsg.startsWith("SPD:") ||
            rawMsg.startsWith("PKT:") ||
            rawMsg.startsWith("STT:")) {
            rawMsg
        } else {
            "NAV:$rawMsg"
        }

        // 6. Übergabe an die "Master Anchor" Logik der MainActivity
        try {
            MainActivity.sendBleStatic(formatted)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Übergabe an MainActivity: ${e.message}")
        }
    }
}
