// Filename: MyBluetoothTtsService.kt
// Datum: 2026-03-29
// Funktion: TTS Service, der als Foreground Service läuft

package ch.scriptwriter.tts2bluetoothserial

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import androidx.core.app.NotificationCompat

class MyBluetoothTtsService : TextToSpeechService() {

    private val TAG = ">>> [TTS-SERVICE]"
    private val CHANNEL_ID = "BikeNav_TTS_Channel"
    private val NOTIFICATION_ID = 102

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        Log.d(TAG, "onStop aufgerufen")
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        val text = request?.charSequenceText?.toString() ?: request?.text

        Log.d(TAG, "onSynthesizeText getriggert. Text-Input: '$text'")

        if (!text.isNullOrBlank()) {
            val intent = Intent("ch.scriptwriter.tts2bluetoothserial.TTS_RECEIVED")
            intent.putExtra("msg", "NAV:$text")
            intent.setPackage(packageName)

            Log.d(TAG, "Sende Broadcast: NAV:$text")
            sendBroadcast(intent)
        } else {
            Log.w(TAG, "Synthese abgebrochen: Text ist null oder leer")
        }

        // Standard-Callback zur Vermeidung von Timeouts
        callback?.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1)
        callback?.done()
        Log.d(TAG, "Synthese-Callback abgeschlossen")
    }
}
