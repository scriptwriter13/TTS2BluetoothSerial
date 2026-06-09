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
// FILE: app/src/main/java/ch/scriptwriter/tts2bluetoothserial/AppLogger.kt
// STATUS: FULL ABSOLUTE CONTROL (MASTER ANKER)
// DATE: 2026-03-29

package ch.scriptwriter.tts2bluetoothserial

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var context: Context? = null
    private val logCache = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private const val FLUSH_INTERVAL = 60 * 1000L // 1 Minute
    private const val LOG_FILE_NAME = "app_log.txt"

    fun init(ctx: Context) {
        context = ctx.applicationContext
        startFlushTimer()
    }

    fun log(tag: String, message: String) {
        // Auch in Logcat ausgeben für Debugging
        Log.d(tag, message)
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "$timestamp [$tag] $message"
        synchronized(logCache) {
            logCache.add(entry)
        }
    }

    fun getLogs(): List<String> {
        val diskLogs = readFromDisk()
        synchronized(logCache) {
            return diskLogs + logCache
        }
    }

    private fun startFlushTimer() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                flushToDisk()
                handler.postDelayed(this, FLUSH_INTERVAL)
            }
        }, FLUSH_INTERVAL)
    }

    private fun flushToDisk() {
        val logsToFlush = synchronized(logCache) {
            val copy = ArrayList(logCache)
            logCache.clear()
            copy
        }
        if (logsToFlush.isEmpty()) return

        context?.let { ctx ->
            val file = File(ctx.filesDir, LOG_FILE_NAME)
            try {
                file.appendText(logsToFlush.joinToString("\n") + "\n")
            } catch (e: Exception) {
                Log.e("AppLogger", "Fehler beim Schreiben auf Disk", e)
            }
        }
    }

    private fun readFromDisk(): List<String> {
        context?.let { ctx ->
            val file = File(ctx.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                try {
                    return file.readLines()
                } catch (e: Exception) {
                    Log.e("AppLogger", "Fehler beim Lesen von Disk", e)
                }
            }
        }
        return emptyList()
    }
}
