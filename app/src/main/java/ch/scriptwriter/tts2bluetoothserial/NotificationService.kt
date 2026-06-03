// Filename: NotificationService.kt
// Datum: 2026-03-29
// Status: Master-Anker BikeNav_App V4.9 (Overlap-Merger & OsmAnd-Special-Parser)
// Fokus: Intelligente Zusammenführung von Title und BigText zur Redundanzvermeidung

package ch.scriptwriter.tts2bluetoothserial

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * V4.9: Spezial-Logik für OsmAnd und Overlap-Merger.
 * Verhindert Textverdopplung, wenn der Titel im Hauptinhalt erneut beginnt.
 */
class NotificationService : NotificationListenerService() {

    private var lastMessage = ""
    private val CHANNEL_ID = "BikeNav_Foreground_Channel"
    private val NOTIFICATION_ID = 101

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == this.packageName) return

        val preferences = getSharedPreferences("NaviSettings", Context.MODE_PRIVATE)
        val isEnabled = preferences.getBoolean("external_nav_enabled", false)
        val allowedApps = preferences.getStringSet("allowed_packages", emptySet())

        if (isEnabled && (allowedApps?.contains(sbn.packageName) == true)) {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

            // --- DEBUG LOG START: Felder gefiltert anzeigen ---
            Log.d("BikeNav_Debug", "--------------------------------------")
            Log.d("BikeNav_Debug", ">>> APP: ${sbn.packageName}")
            Log.d("BikeNav_Debug", ">>> TITLE: $title")
            Log.d("BikeNav_Debug", ">>> TEXT: ${text.replace("\n", "[\\n]")}")
            Log.d("BikeNav_Debug", ">>> BIGTEXT: ${bigText.replace("\n", "[\\n]")}")
            // --- DEBUG LOG END ---

            // Inhalts-Priorisierung
            val contentSource = if (text.isNotEmpty()) text else bigText

            if (contentSource.isNotEmpty()) {
                val isOsmAnd = sbn.packageName.contains("osmand", ignoreCase = true)
                val separator = if (isOsmAnd) "|" else " "

                // 1. Overlap-Merging: Verhindert "Titel: Titel + Rest"
                var mergedBody = mergeOverlap(title.trim(), contentSource.trim())

                // 2. Sanitizing: Umbrüche durch Separator ersetzen
                var processedContent = mergedBody.replace(Regex("[\\n\\r]+"), separator).trim()

                // 3. Finale Nachricht zusammenbauen
                var fullMsg = if (title.isNotEmpty() && !processedContent.startsWith(title)) {
                    "$title: $processedContent"
                } else {
                    processedContent
                }

                // OsmAnd-Tagging
                if (isOsmAnd) {
                    fullMsg = "$fullMsg|OSMAND"
                }

                // Doubletten-Schutz & Senden
                if (fullMsg != lastMessage) {
                    lastMessage = fullMsg
                    Log.d("BikeNav_Service", ">>> RX-NOTIFY: $fullMsg")
                    sendToMainDirect(fullMsg)
                }
            }
        }
    }

    /**
     * Prüft, ob das Ende von 'head' mit dem Anfang von 'tail' überlappt
     * und verschmilzt diese zu einem einzigen String.
     */
    private fun mergeOverlap(head: String, tail: String): String {
        if (head.isEmpty()) return tail
        if (tail.isEmpty()) return head

        val maxOverlap = minOf(head.length, tail.length)
        for (i in maxOverlap downTo 1) {
            if (head.endsWith(tail.substring(0, i))) {
                return head + tail.substring(i)
            }
        }
        return tail // Falls kein Overlap, nehmen wir primär den Tail (Hauptinhalt)
    }

    private fun sendToMainDirect(message: String) {
        try {
            MainActivity.sendBleStatic(message)
        } catch (e: Exception) {
            Log.e("BikeNav_Service", ">>> Fallback via Broadcast")
            val intent = Intent("ch.scriptwriter.tts2bluetoothserial.NOTIFICATION_UPDATE")
            intent.putExtra("msg", message)
            intent.setPackage(this.packageName)
            sendBroadcast(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "BikeNav Service", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onListenerConnected() {
        Log.i("BikeNav_Service", ">>> Listener verbunden!")
    }

    private fun createForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BikeNav HUD Aktiv")
            .setContentText("Navi-Überwachung läuft")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
    override fun onListenerDisconnected() {}
}
