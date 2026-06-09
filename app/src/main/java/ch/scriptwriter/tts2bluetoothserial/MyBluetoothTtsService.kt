/*                                                                                                                                        
 * Copyright (C) 2026 by scriptwriter13                                                                                       
 *                                                                                                                                        
 * Dieses Programm ist freie Software: Sie können es unter den Bedingungen der                                                            
 * GNU General Public License, wie von der Free Software Foundation veröffentlicht,                                                       
 * entweder Version 3 der Lizenz oder (nach Ihrer Option) jeder späteren                                                                  
 * Version, weiterverbreiten und/oder modifizieren.                                                                                       
 *                                                                                                                                        
 * Dieses Programm wird in der Hoffnung, dass es nützlich sein wird, aber                                                                 
 * OHNE JEDE GEWÄHRLEISTUNG, sogar ohne die implizite Gewährleistung der                                                                  
 * MARKTGÄNGIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK. Siehe die                                                                     
 * GNU General Public License für weitere Details.                                                                                        
 *                                                                                                                                        
 * Sie sollten eine Kopie der GNU General Public License zusammen mit diesem                                                              
 * Programm erhalten haben. Wenn nicht, siehe <https://www.gnu.org/licenses/>.                                                            
 */         
// FILE: app/src/main/java/ch/scriptwriter/tts2bluetoothserial/MyBluetoothTtsService.kt
// STATUS: FULL ABSOLUTE CONTROL (MASTER ANKER)
// DATE: 2026-03-29

package ch.scriptwriter.tts2bluetoothserial

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MyBluetoothTtsService : TextToSpeechService() {

    private val TAG = ">>> [TTS-SERVICE]"
    private val CHANNEL_ID = "BikeNav_TTS_Channel"
    private val NOTIFICATION_ID = 102
    private val TIMEOUT_DURATION = 2 * 60 * 1000L // 2 Minuten

    private val handler = Handler(Looper.getMainLooper())
    private var lastMessage: String? = null

    private val timeoutRunnable = Runnable {
        AppLogger.log(TAG, "Timeout erreicht: Sende STOP_BLE_CONNECTION")
        val intent = Intent("ch.scriptwriter.tts2bluetoothserial.STOP_BLE_CONNECTION")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private val reconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppLogger.log(TAG, "Broadcast empfangen: ${intent?.action}")
            lastMessage?.let { msg ->
                AppLogger.log(TAG, "Reconnect erkannt, sende letzte Nachricht erneut: $msg")
                sendTtsBroadcast(msg)
            } ?: AppLogger.log(TAG, "Reconnect erkannt, aber keine letzte Nachricht vorhanden.")
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.log(TAG, "Service onCreate aufgerufen.")
        
        // Registriere den Receiver für Reconnect-Events
        // Wir verwenden ContextCompat.RECEIVER_EXPORTED, um sicherzustellen, dass der Broadcast ankommt
        val filter = IntentFilter("ch.scriptwriter.tts2bluetoothserial.BLE_RECONNECTED")
        try {
            ContextCompat.registerReceiver(this, reconnectReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            AppLogger.log(TAG, "ReconnectReceiver erfolgreich registriert.")
        } catch (e: Exception) {
            AppLogger.log(TAG, "Fehler bei Registrierung des ReconnectReceiver: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.log(TAG, "Service onStartCommand aufgerufen.")
        createNotificationChannel()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "BikeNav TTS Service", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun createForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BikeNav TTS Aktiv")
            .setContentText("Sprachausgabe läuft")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?) = TextToSpeech.LANG_AVAILABLE
    override fun onGetLanguage() = arrayOf("deu", "DEU", "")
    override fun onLoadLanguage(lang: String?, country: String?, variant: String?) = TextToSpeech.LANG_AVAILABLE

    override fun onStop() {
        AppLogger.log(TAG, "onStop aufgerufen")
        handler.removeCallbacks(timeoutRunnable)
    }

    override fun onDestroy() {
        AppLogger.log(TAG, "Service onDestroy aufgerufen.")
        handler.removeCallbacks(timeoutRunnable)
        try {
            unregisterReceiver(reconnectReceiver)
        } catch (e: Exception) {
            AppLogger.log(TAG, "Fehler beim Unregister des Receivers: ${e.message}")
        }
        super.onDestroy()
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        // Timer zurücksetzen bei Aktivität
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, TIMEOUT_DURATION)

        val text = request?.charSequenceText?.toString() ?: request?.text

        AppLogger.log(TAG, "onSynthesizeText getriggert. Text-Input: '$text'")

        if (!text.isNullOrBlank()) {
            lastMessage = text // Nachricht zwischenspeichern
            sendTtsBroadcast(text)
        } else {
            AppLogger.log(TAG, "Synthese abgebrochen: Text ist null oder leer")
        }

        // Standard-Callback zur Vermeidung von Timeouts
        callback?.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1)
        callback?.done()
        AppLogger.log(TAG, "Synthese-Callback abgeschlossen")
    }

    private fun sendTtsBroadcast(text: String) {
        val intent = Intent("ch.scriptwriter.tts2bluetoothserial.TTS_RECEIVED")
        intent.putExtra("msg", "NAV:$text")
        intent.setPackage(packageName) // Explizit an unsere App senden

        AppLogger.log(TAG, "Sende Broadcast: NAV:$text")
        sendBroadcast(intent)
    }
}
