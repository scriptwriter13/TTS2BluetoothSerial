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
// FILE: app/src/main/java/ch/scriptwriter/tts2bluetoothserial/MainActivity.kt
// STATUS: FULL ABSOLUTE CONTROL (MASTER ANKER)
// DATE: 2026-03-29

package ch.scriptwriter.tts2bluetoothserial

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Xml
import android.view.View
import android.view.ViewGroup
import org.json.JSONArray
import org.json.JSONObject
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.service.notification.NotificationListenerService

class MainActivity : AppCompatActivity(), LocationListener {

    private val TAG = "BikeNav_Main"
    private val ALLOWED_EXTENSIONS = listOf("gpx", "kml", "xml", "json")

    private val PRIORITY_PACKAGES = listOf(
        "com.google.android.apps.maps",
        "de.komoot.android",
        "net.osmand",
        "net.osmand.plus",
        "org.kurviger.android",
        "com.strava",
        "com.mapfactor.navigator",
        "com.sygic.aura",
        "app.organicmaps"
    )

    companion object {
        private var instance: MainActivity? = null

        // Zentraler Einstiegspunkt für den NotificationService
        fun sendBleStatic(data: String) {
            instance?.let { main ->
                if (!main.isBleConnected) {
                    main.pendingMessage = data // Nachricht zwischenspeichern
                    // Force=true, damit der Scan sofort startet, wenn eine Navi-App aktiv wird
                    main.runOnUiThread { main.triggerScanIfDisconnected(force = true) }
                } else {
                    // Hier explizit restartGps = true, da eine Nachricht reinkommt
                    main.updateLog(data, restartGps = true)
                }
                Log.d("BikeNav_Main", ">>> [STATIC-IN]: $data")
            } ?: Log.e("BikeNav_Main", ">>> Fehler: MainActivity Instanz nicht bereit!")
        }
    }

    // UI Elemente
    private lateinit var tvLogContent: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var deviceListView: ListView
    private lateinit var btnTabDevices: Button
    private lateinit var btnTabLog: Button
    private lateinit var btnTabRoute: Button
    private lateinit var btnTabFirmware: Button
    private lateinit var containerDevices: LinearLayout
    private lateinit var containerLog: LinearLayout
    private lateinit var containerRoute: LinearLayout
    private lateinit var containerFirmware: LinearLayout
    private lateinit var tvSelectedRoute: TextView
    private lateinit var btnSelectFile: Button
    private lateinit var btnClearRoute: Button
    private lateinit var switchExternalNav: Switch
    private lateinit var switchTtsEnabled: Switch
    private lateinit var btnSelectNavApps: Button
    private lateinit var tvSelectedAppsCount: TextView
    private lateinit var firmwareListView: ListView
    private lateinit var btnCheckFirmware: Button
    private lateinit var btnDownloadFirmware: Button
    private lateinit var btnClearCache: Button
    private lateinit var tvHardwareVersion: TextView
    private lateinit var tvFirmwareVersion: TextView

    private lateinit var etManualCommand: EditText
    private lateinit var btnSendMessage: Button
    private lateinit var btnShareLog: Button
    private lateinit var btnResetService: Button

    // BLE Variablen
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    var isBleConnected = false // Jetzt intern lesbar für Companion
    private var lastConnectedDeviceAddress: String? = null
    
    // Map statt Liste, um RSSI zu speichern
    private val foundDevices = mutableMapOf<String, ScanResult>() 
    
    private var isScanning = false
    private var lastScanTimestamp = 0L
    private var lastLogTimestamp = 0L // Anti-Spam für Log

    private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CHAR_UUID    = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    // OTA Konstanten
    private val OTA_SERVICE_UUID = UUID.fromString("1D14D6EE-FD63-4FA1-BFA4-8F47B42119F0")
    private val OTA_CHAR_UUID = UUID.fromString("1D14D6EF-FD63-4FA1-BFA4-8F47B42119F0")
    private val OTA_SECRET_KEY = "BIKE_HUD_OTA_2026"
    private var otaLatch: java.util.concurrent.CountDownLatch? = null

    // GPS & Logik
    private lateinit var locationManager: LocationManager
    private val LOG_FILE = "bike_log.txt"
    private val PREFS_NAME = "BikeNavPrefs"
    private val NAVI_PREFS = "NaviSettings"
    private val KEY_ROUTE_PATH = "selected_route_path"
    private val KEY_ROUTE_NAME = "selected_route_name"
    private val KEY_LAST_PICKER_PATH = "last_picker_path"
    private val KEY_LAST_BLE_ADDR = "last_ble_address"

    private val handler = Handler(Looper.getMainLooper())
    private var lastSpeedSent = -1
    private var speedBuffer = mutableListOf<Float>()
    private var lastSendTime = 0L
    private var routePoints = mutableListOf<Location>()
    private var nextPointIndex = 0
    private var lastPktSendTime = 0L
    private var isFirstFix = true
    private var isWritingBle = false
    private var lastLogContent = "" // Tracking für Auto-Scroll
    private var pendingMessage: String? = null // Puffer für Nachrichten

    // Firmware Variablen
    private var firmwareReleases = mutableListOf<Pair<String, String>>() // Pair(Tag, DownloadUrl)
    private var selectedFirmware: Pair<String, String>? = null
    private var detectedHardwareId: String = "" // Wird durch GET_HW gesetzt

    // GPS Inaktivitäts-Management
    private var lastActivityTime = System.currentTimeMillis()
    private var isGpsActive = false
    private val GPS_TIMEOUT = 120_000L // 2 Minuten

    private var tts: TextToSpeech? = null

    private fun isSystemActive(): Boolean {
        val navActive = NotificationService.instance?.isAnyNavAppActive() ?: false
        val ttsActive = tts?.isSpeaking ?: false
        return navActive || ttsActive
    }

    private val gpsCheckRunnable = object : Runnable {
        override fun run() {
            // Wenn System inaktiv UND Timeout überschritten -> GPS aus
            if (isGpsActive && !isSystemActive() && (System.currentTimeMillis() - lastActivityTime > GPS_TIMEOUT)) {
                stopGpsUpdates()
                Log.d(TAG, "System: GPS Standby (Inaktiv)")
            }
            handler.postDelayed(this, 30000) // Alle 30 Sekunden prüfen
        }
    }

    // Periodischer Reconnect-Check
    private val reconnectRunnable = object : Runnable {
        override fun run() {
            // Nur automatisch scannen, wenn wir nicht verbunden sind UND das System aktiv ist
            if (!isBleConnected && isSystemActive()) {
                triggerScanIfDisconnected()
            }
            handler.postDelayed(this, 60000) // Alle 60 Sekunden prüfen
        }
    }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            val active = isSystemActive()
            if (isBleConnected && active) {
                sendMessageToBle("STT:ALIVE")
                Log.d(TAG, "Heartbeat gesendet (System aktiv)")
            }
            
            // Dynamisches Intervall: 10s bei Aktivität, 30s bei Inaktivität
            val delay = if (active) 10000L else 30000L
            handler.postDelayed(this, delay)
        }
    }

    private val logUpdateRunnable = object : Runnable {
        override fun run() {
            if (containerLog.visibility == View.VISIBLE) {
                val allLogs = AppLogger.getLogs()
                
                // 1. Filtere ACKs für die Anzeige
                val displayLogs = allLogs.filter { !it.contains("ACK", ignoreCase = true) }
                
                // 2. Prüfe, ob das letzte Log ein ACK war, um den Effekt auszulösen
                if (allLogs.isNotEmpty() && allLogs.last().contains("ACK", ignoreCase = true)) {
                    triggerAckVisual()
                }

                val content = displayLogs.takeLast(100).joinToString("\n")
                if (content != lastLogContent) {
                    tvLogContent.text = content
                    logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
                    lastLogContent = content
                }
            }
            handler.postDelayed(this, 2000)
        }
    }

    // Visueller Effekt: Hintergrund kurz grün
    private fun triggerAckVisual() {
        tvLogContent.setBackgroundColor(Color.parseColor("#004400")) // Dunkelgrün
        handler.postDelayed({
            tvLogContent.setBackgroundColor(Color.parseColor("#1E1E1E")) // Zurück zum Standard
        }, 200)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        instance = this
        initUI()
        setupExternalNavLogic()
        loadPersistedData()
        switchTab(0)

        performTtsHardReset()

        lastConnectedDeviceAddress = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_LAST_BLE_ADDR, null)

        // V5.1: Erster Scan beim Start, danach nur noch auf Trigger
        handler.postDelayed({ triggerScanIfDisconnected() }, 1500)
        
        // GPS Timeout Check starten
        handler.postDelayed(gpsCheckRunnable, 30000)
        
        // Periodischen Reconnect-Check starten
        handler.postDelayed(reconnectRunnable, 60000)
        
        // Heartbeat starten
        handler.post(heartbeatRunnable)
        
        // Log-Update-Throttling starten
        handler.postDelayed(logUpdateRunnable, 500)
    }

    /**
     * V5.1: Die "Passive-Trigger" Kernfunktion.
     * Wird aufgerufen, wenn eine Nachricht reinkommt, aber keine Verbindung besteht.
     */
    fun triggerScanIfDisconnected(force: Boolean = false) {
        if (isBleConnected || isScanning) return

        // Anti-Spam: Scanne maximal einmal alle 10 Sekunden automatisch, außer es ist erzwungen
        val now = System.currentTimeMillis()
        if (!force && (now - lastScanTimestamp < 10000L)) {
            return
        }

        lastScanTimestamp = now
        startSmartScan()
    }

    private fun performTtsHardReset() {
        Log.d(TAG, "Starte TTS Hard-Reset...")
        updateLog("System: TTS Reset...")
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim TTS Shutdown: ${e.message}")
        }
        handler.postDelayed({ initTts() }, 500)
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.GERMANY
                updateLog("STT:TTS bereit.")
                handler.postDelayed({ triggerSelfTest() }, 1000)
            } else {
                updateLog("Fehler: TTS Start")
            }
        }
    }

    private fun triggerSelfTest() {
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "INIT_TEST")
        tts?.speak("System aktiv", TextToSpeech.QUEUE_FLUSH, params, "INIT_TEST")
    }

    private fun initUI() {
        tvLogContent = findViewById(R.id.tvLogContent)
        logScrollView = findViewById(R.id.logScrollView)
        deviceListView = findViewById(R.id.deviceListView)
        btnTabDevices = findViewById(R.id.btnTabDevices)
        btnTabLog = findViewById(R.id.btnTabLog)
        btnTabRoute = findViewById(R.id.btnTabRoute)
        btnTabFirmware = findViewById(R.id.btnTabFirmware)
        containerDevices = findViewById(R.id.containerDevices)
        containerLog = findViewById(R.id.containerLog)
        containerRoute = findViewById(R.id.containerRoute)
        containerFirmware = findViewById(R.id.containerFirmware)
        tvSelectedRoute = findViewById(R.id.tvSelectedRoute)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnClearRoute = findViewById(R.id.btnClearRoute)
        switchExternalNav = findViewById(R.id.switchExternalNav)
        switchTtsEnabled = findViewById(R.id.switchTtsEnabled)
        btnSelectNavApps = findViewById(R.id.btnSelectNavApps)
        tvSelectedAppsCount = findViewById(R.id.tvSelectedAppsCount)

        etManualCommand = findViewById(R.id.etTestMessage)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        btnShareLog = findViewById(R.id.btnShareLog)
        btnResetService = findViewById(R.id.btnResetService)
        firmwareListView = findViewById(R.id.firmwareListView)

        // Version & Git-Hash anzeigen
        val tvVersion = TextView(this)
        tvVersion.text = "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_HASH})"
        tvVersion.textSize = 12f
        tvVersion.setTextColor(Color.GRAY)
        tvVersion.gravity = android.view.Gravity.CENTER
        tvVersion.setPadding(0, 16, 0, 16)
        // Hinzufügen an Index 0, damit es oben erscheint
        containerDevices.addView(tvVersion, 0)

        btnCheckFirmware = findViewById(R.id.btnCheckFirmware)
        btnCheckFirmware.setOnClickListener {
            fetchFirmwareReleases()
        }

        btnDownloadFirmware = findViewById(R.id.btnDownloadFirmware)
        btnDownloadFirmware.isEnabled = false
        btnDownloadFirmware.setOnClickListener {
            downloadFirmware()
        }

        tvHardwareVersion = TextView(this)
        tvHardwareVersion.setTextColor(Color.GRAY)
        tvHardwareVersion.textSize = 14f
        tvHardwareVersion.text = "Hardware: Wird geladen..."
        containerFirmware.addView(tvHardwareVersion, 1)

        tvFirmwareVersion = TextView(this)
        tvFirmwareVersion.setTextColor(Color.GRAY)
        tvFirmwareVersion.textSize = 14f
        tvFirmwareVersion.text = "Firmware: Wird geladen..."
        containerFirmware.addView(tvFirmwareVersion, 2)

        btnClearCache = Button(this)
        btnClearCache.text = "Cache leeren"
        btnClearCache.setOnClickListener { clearFirmwareCache() }
        containerFirmware.addView(btnClearCache, 3)

        btnTabDevices.setOnClickListener { switchTab(0) }
        btnTabLog.setOnClickListener { switchTab(1) }
        btnTabRoute.setOnClickListener { switchTab(2) }
        btnTabFirmware.setOnClickListener { switchTab(3) }

        btnSelectFile.setOnClickListener {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val lastPath = prefs.getString(KEY_LAST_PICKER_PATH, null)
            val startDir = if (lastPath != null && File(lastPath).exists()) File(lastPath) else File(Environment.getExternalStorageDirectory(), "Download")
            showNavPicker(if (startDir.exists()) startDir else Environment.getExternalStorageDirectory())
        }

        btnClearRoute.setOnClickListener { clearRoute() }

        btnSendMessage.setOnClickListener {
            val cmd = etManualCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                updateLog(cmd)
                etManualCommand.setText("")
            }
        }

        btnResetService.setOnClickListener {
            performTtsHardReset()
            updateLog("System: Service Reset")
        }

        btnShareLog.setOnClickListener {
            shareLogFile()
        }

        findViewById<Button>(R.id.btnRefreshLog).setOnClickListener {
            lastScanTimestamp = 0L // Reset Cooldown für manuelle Suche
            lastLogTimestamp = 0L // Auch Log-Cooldown resetten
            triggerScanIfDisconnected()
        }

        tvLogContent.setOnLongClickListener {
            File(filesDir, LOG_FILE).delete()
            tvLogContent.text = "Log gelöscht."
            lastLogContent = "" // Reset für Auto-Scroll
            performTtsHardReset()
            true
        }
    }

    private fun shareLogFile() {
        val originalFile = File(filesDir, LOG_FILE)
        if (!originalFile.exists() || originalFile.length() == 0L) {
            Toast.makeText(this, "Log ist leer", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Erzeuge einen Dateinamen mit Zeitstempel für den Share-Vorgang
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
            val dateStr = sdf.format(Date())
            val shareFile = File(filesDir, "bike_log_$dateStr.txt")

            // Kopiere den Inhalt in die neue Datei
            originalFile.copyTo(shareFile, overwrite = true)

            // Erzeuge URI über den FileProvider
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.provider", shareFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "BikeNav Log teilen"))

            // Die temporäre Share-Datei nach kurzer Zeit löschen (optional, oder beim nächsten App-Start aufräumen)
            handler.postDelayed({ try { shareFile.delete() } catch(e:Exception){} }, 60000)

        } catch (e: Exception) {
            updateLog("Fehler beim Teilen: ${e.message}")
            Log.e(TAG, "Share Error", e)
        }
    }

    private fun switchTab(i: Int) {
        containerDevices.visibility = if (i == 0) View.VISIBLE else View.GONE
        containerLog.visibility = if (i == 1) View.VISIBLE else View.GONE
        containerRoute.visibility = if (i == 2) View.VISIBLE else View.GONE
        containerFirmware.visibility = if (i == 3) View.VISIBLE else View.GONE
        
        if (i == 3) {
            if (isBleConnected) {
                sendMessageToBle("GET_HW")
                // Kleine Verzögerung, damit das Flag 'isWritingBle' zurückgesetzt werden kann
                handler.postDelayed({ sendMessageToBle("GET_FW") }, 200)
            } else {
                tvHardwareVersion.text = "Hardware: Nicht verbunden"
                tvFirmwareVersion.text = "Firmware: Nicht verbunden"
            }
        }
        
        if (i == 1) logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showNavPicker(dir: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            AlertDialog.Builder(this).setTitle("Berechtigung erforderlich").setPositiveButton("Einstellungen") { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }.show()
            return
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_LAST_PICKER_PATH, dir.absolutePath).apply()
        val files = dir.listFiles()?.filter { it.isDirectory || ALLOWED_EXTENSIONS.contains(it.extension.lowercase()) }?.sortedWith(compareBy({ it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        val displayNames = mutableListOf<String>()
        if (dir.path != Environment.getExternalStorageDirectory().path && dir.parentFile != null) displayNames.add(".. (Zurück)")
        files.forEach { displayNames.add(if (it.isDirectory) "📁 ${it.name}" else "📄 ${it.name}") }
        AlertDialog.Builder(this).setTitle("Pfad: ${dir.name}").setItems(displayNames.toTypedArray()) { _, which ->
            if (displayNames[which] == ".. (Zurück)") showNavPicker(dir.parentFile ?: dir)
            else {
                val actualIndex = if (displayNames.isNotEmpty() && displayNames[0] == ".. (Zurück)") which - 1 else which
                val selectedFile = files[actualIndex]
                if (selectedFile.isDirectory) showNavPicker(selectedFile) else saveRouteFromPath(selectedFile)
            }
        }.show()
    }

    private fun saveRouteFromPath(file: File) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_ROUTE_PATH, file.absolutePath).putString(KEY_ROUTE_NAME, file.name).apply()
        updateLog("Datei OK: ${file.name}"); displayStoredRoute(); loadRouteIntoMemory()
    }

    private fun startSmartScan() {
        if (!checkPermissions()) { requestPermissions(); return }
        if (isBleConnected || isScanning) return

        isScanning = true
        
        // Anti-Spam für Log: Nur einmal pro Minute loggen
        val now = System.currentTimeMillis()
        if (now - lastLogTimestamp > 60000L) {
            updateLog("Trigger: Suche HUD...")
            lastLogTimestamp = now
        }
        
        foundDevices.clear()
        updateDeviceListView()

        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (!adapter.isEnabled) { updateLog("Bluetooth ist AUS"); isScanning = false; return }

        val scanner = adapter.bluetoothLeScanner
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        // Akku-Optimierung: BALANCED statt LOW_LATENCY
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            scanner?.startScan(listOf(filter), settings, bleScanCallback)
            handler.postDelayed({
                stopScanning()
                if (!isBleConnected && foundDevices.isNotEmpty()) {
                    val target = lastConnectedDeviceAddress
                    val autoTarget = if (target != null) foundDevices[target] else foundDevices.values.firstOrNull()
                    autoTarget?.let { connectToDevice(it.device.address) }
                }
            }, 6000)
        }
    }

    private fun stopScanning() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            adapter.bluetoothLeScanner?.stopScan(bleScanCallback)
        }
        isScanning = false
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: ""
            
            // Filter: Nur Geräte zulassen, die mit "BikeNav" beginnen
            if (!name.startsWith("BikeNav")) return

            foundDevices[device.address] = result
            
            runOnUiThread { updateDeviceListView() }
            
            if (device.address == lastConnectedDeviceAddress && !isBleConnected) {
                stopScanning()
                connectToDevice(device.address)
            }
        }
    }

    private fun connectToDevice(address: String) {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = adapter.getRemoteDevice(address)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            updateLog("Verbinde: ${device.name ?: "HUD"}...")
            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isBleConnected = true
                lastConnectedDeviceAddress = gatt.device.address
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_LAST_BLE_ADDR, lastConnectedDeviceAddress).apply()
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) gatt.discoverServices()
                runOnUiThread { updateFirmwareButtonState() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isBleConnected = false
                writeCharacteristic = null
                runOnUiThread { 
                    updateDeviceListView()
                    updateFirmwareButtonState()
                }
                updateLog("Warnung: HUD verloren")
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            writeCharacteristic = service?.getCharacteristic(CHAR_UUID)
            
            if (writeCharacteristic != null) {
                // 1. MTU anfordern
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.requestMtu(517)
                    
                    // 2. WICHTIG: Notifications für UART-Antworten aktivieren
                    gatt.setCharacteristicNotification(writeCharacteristic, true)
                    val cccUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    val descriptor = writeCharacteristic?.getDescriptor(cccUuid)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }
                
                updateLog("System: HUD bereit")
                // Sofortiger Heartbeat nach Verbindungsaufbau
                sendMessageToBle("STT:ALIVE", force = true)
                
                // Falls eine Nachricht gepuffert war, jetzt senden (mit kleiner Verzögerung für BLE-Stack)
                pendingMessage?.let { msg ->
                    handler.postDelayed({
                        updateLog(msg, restartGps = true, force = true)
                    }, 200)
                    pendingMessage = null
                }
                
                runOnUiThread { startGpsUpdates(); updateDeviceListView() }
            }
        }

        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "OTA: Descriptor erfolgreich geschrieben (Notifications aktiv)")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // 1. Rohdaten als String lesen
            val value = characteristic.value
            val response = if (value != null) String(value, Charsets.UTF_8) else ""
            
            // 2. Debug-Log mit Hex-Werten, um versteckte Zeichen zu sehen
            val hexString = value?.joinToString("") { "%02x".format(it) } ?: "null"
            Log.d(TAG, "OTA-DEBUG: Empfangen: '$response' (Hex: $hexString) auf UUID: ${characteristic.uuid}")

            // 3. Robuste Prüfung: UUID-Check entfernen, nur auf Inhalt prüfen
            // Wir prüfen, ob "READY" im String enthalten ist (egal welche UUID)
            if (response.contains("READY", ignoreCase = true)) {
                Log.d(TAG, "OTA-DEBUG: READY erkannt!")
                otaLatch?.countDown()
            }

            if (response.startsWith("HW:")) {
                detectedHardwareId = response.substring(3).trim()
                runOnUiThread {
                    tvHardwareVersion.text = "Hardware: $detectedHardwareId"
                    updateLog("System: Hardware erkannt: $detectedHardwareId")
                }
            }

            if (response.startsWith("FW:")) {
                val fwVersion = response.substring(3).trim()
                runOnUiThread {
                    tvFirmwareVersion.text = "Firmware: $fwVersion"
                    updateLog("System: Firmware erkannt: $fwVersion")
                    // Liste aktualisieren, damit das Häkchen erscheint
                    updateFirmwareListView()
                }
            }
        }
    }

    fun updateLog(message: String, restartGps: Boolean = false, force: Boolean = false) {
        if (restartGps) {
            lastActivityTime = System.currentTimeMillis()
            if (!isGpsActive) startGpsUpdates()
        }

        // 1. Zuerst BLE-Senden (Priorität!)
        sendMessageToBle(message, force = force)

        // 2. Dann Logging
        AppLogger.log(TAG, message)
    }

    override fun onLocationChanged(l: Location) {
        if (!isBleConnected || l.accuracy > 25) return
        speedBuffer.add(l.speed)
        val now = System.currentTimeMillis()
        if (now - lastSendTime >= 5000L && speedBuffer.isNotEmpty()) {
            val avg = (speedBuffer.average() * 3.6).toInt()
            speedBuffer.clear(); lastSendTime = now
            if (avg != lastSpeedSent) { lastSpeedSent = avg; updateLog("SPD:$avg", restartGps = true) }
        }
        if (routePoints.isNotEmpty() && l.speed > 0.4) handleRoutePoints(l)
    }

    private fun handleRoutePoints(l: Location) {
        val now = System.currentTimeMillis()
        var curD = l.distanceTo(routePoints[nextPointIndex])
        if (isFirstFix || curD > 150f) { reLockPoint(l); isFirstFix = false }
        else {
            while (nextPointIndex < routePoints.size - 1) {
                val nD = l.distanceTo(routePoints[nextPointIndex + 1])
                if (nD < curD) { curD = nD; nextPointIndex++ } else break
            }
        }
        if (now - lastPktSendTime >= 5000L && l.hasBearing()) {
            val target = routePoints[nextPointIndex]
            var rel = l.bearingTo(target) - l.bearing
            if (rel > 180) rel -= 360 else if (rel < -180) rel += 360
            updateLog("PKT:${rel.toInt()};${l.distanceTo(target).toInt()};$nextPointIndex", restartGps = true)
            lastPktSendTime = now
        }
    }

    private fun reLockPoint(l: Location) {
        if (routePoints.isEmpty()) return
        
        // Suche nur in einem Fenster von 50 Punkten ab dem aktuellen Index
        val searchWindow = 50
        val startIndex = nextPointIndex
        val endIndex = minOf(startIndex + searchWindow, routePoints.size - 1)
        
        var bI = startIndex
        var mD = Float.MAX_VALUE
        
        for (i in startIndex..endIndex) {
            val d = l.distanceTo(routePoints[i])
            if (d < mD) {
                mD = d
                bI = i
            }
        }
        
        nextPointIndex = bI
        updateLog("System: Re-Lock P$bI", restartGps = true)
    }

    private fun startGpsUpdates() {
        if (isGpsActive) return
        if (checkPermissions()) {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                // Optimierung: Intervall auf 10s, Distanz auf 20m
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 20.0f, this)
                isGpsActive = true
                updateLog("System: Eco-GPS aktiv (10s/20m)", restartGps = false)
            } catch (e: Exception) {
                updateLog("Fehler: GPS Start", restartGps = false)
            }
        }
    }

    private fun stopGpsUpdates() {
        if (!isGpsActive) return
        try {
            locationManager.removeUpdates(this)
            isGpsActive = false
            updateLog("System: GPS Standby", restartGps = false)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim GPS Stoppen: ${e.message}")
        }
    }

    private fun checkPermissions(): Boolean {
        val loc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val scan = if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
        return loc && scan
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { perms.add(Manifest.permission.BLUETOOTH_SCAN); perms.add(Manifest.permission.BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { perms.add(Manifest.permission.POST_NOTIFICATIONS) }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
    }

    private fun setupExternalNavLogic() {
        val navPrefs = getSharedPreferences(NAVI_PREFS, MODE_PRIVATE)
        switchExternalNav.isChecked = navPrefs.getBoolean("external_nav_enabled", false)
        switchTtsEnabled.isChecked = navPrefs.getBoolean("tts_enabled", true)
        updateAppCountText()
        switchExternalNav.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isNotificationServiceEnabled()) { showNotificationAccessDialog(); switchExternalNav.isChecked = false }
            else navPrefs.edit().putBoolean("external_nav_enabled", isChecked).apply()
        }
        switchTtsEnabled.setOnCheckedChangeListener { _, isChecked -> navPrefs.edit().putBoolean("tts_enabled", isChecked).apply() }
        btnSelectNavApps.setOnClickListener { showAppSelectionDialog() }
    }

    private fun showAppSelectionDialog() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { !it.activityInfo.packageName.startsWith("com.android.") }
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .sortedWith(compareBy({ !PRIORITY_PACKAGES.contains(it.second) }, { it.first.lowercase() }))
        val names = apps.map { if (PRIORITY_PACKAGES.contains(it.second)) "⭐ ${it.first}" else it.first }.toTypedArray()
        val pks = apps.map { it.second }.toTypedArray()
        val navPrefs = getSharedPreferences(NAVI_PREFS, MODE_PRIVATE)
        val saved = navPrefs.getStringSet("allowed_packages", emptySet()) ?: emptySet()
        val checked = BooleanArray(names.size) { i -> saved.contains(pks[i]) }

        AlertDialog.Builder(this).setTitle("Navi-Apps wählen").setMultiChoiceItems(names, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("OK") { _, _ ->
                val set = mutableSetOf<String>()
                checked.forEachIndexed { i, c -> if (c) set.add(pks[i]) }
                navPrefs.edit().putStringSet("allowed_packages", set).apply()
                updateAppCountText()
                if (Build.VERSION.SDK_INT >= 24) {
                    try { NotificationListenerService.requestRebind(ComponentName(this, NotificationService::class.java)) } catch(e:Exception){}
                }
            }.show()
    }

    private fun updateAppCountText() {
        val count = getSharedPreferences(NAVI_PREFS, MODE_PRIVATE).getStringSet("allowed_packages", emptySet())?.size ?: 0
        tvSelectedAppsCount.text = "$count Apps aktiv"
    }

    private fun isNotificationServiceEnabled() = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
    private fun showNotificationAccessDialog() {
        AlertDialog.Builder(this).setMessage("Benachrichtigungszugriff erlauben?").setPositiveButton("OK") { _, _ -> startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }.show()
    }

    private fun updateDeviceListView() {
        val results = foundDevices.values.toList()
        
        if (deviceListView.adapter is DeviceAdapter) {
            (deviceListView.adapter as DeviceAdapter).updateData(results)
        } else {
            deviceListView.adapter = DeviceAdapter(this, results) { address ->
                isBleConnected && bluetoothGatt?.device?.address == address
            }
        }
        
        deviceListView.setOnItemClickListener { _, _, i, _ -> 
            stopScanning(); connectToDevice(results[i].device.address) 
        }
    }

    private class DeviceAdapter(
        private val context: Context,
        private var devices: List<ScanResult>,
        private val isConnected: (String) -> Boolean
    ) : BaseAdapter() {
        fun updateData(newDevices: List<ScanResult>) {
            devices = newDevices
            notifyDataSetChanged()
        }
        override fun getCount() = devices.size
        override fun getItem(position: Int) = devices[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = (convertView as? TextView) ?: TextView(context).apply {
                setPadding(16, 16, 16, 16)
                textSize = 16f
            }
            val result = devices[position]
            val device = result.device
            val conn = isConnected(device.address)
            
            v.text = "${device.name ?: "HUD"}\n${device.address} (${result.rssi} dBm)"
            v.setTextColor(Color.WHITE)
            v.setBackgroundColor(if (conn) Color.parseColor("#2E7D32") else Color.TRANSPARENT)
            return v
        }
    }

    private fun sendMessageToBle(message: String, force: Boolean = false) {
        val char = writeCharacteristic ?: return
        if (!isBleConnected || (isWritingBle && !force)) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        
        if (!force) isWritingBle = true
        
        val payload = if (message.endsWith("\n")) message else "$message\n"
        val bytes = payload.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(char)
        }
        
        if (!force) {
            handler.postDelayed({ isWritingBle = false }, 50)
        }
    }

    private fun fetchFirmwareReleases() {
        updateLog("System: Starte Release-Abfrage...")
        
        Thread {
            try {
                val url = java.net.URL("https://api.github.com/repos/scriptwriter13/Navi_Display_ESP32C3/releases")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                // FIX: User-Agent hinzufügen (GitHub API Anforderung)
                connection.setRequestProperty("User-Agent", "BikeNav-App")
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(response)
                    
                    val newList = mutableListOf<Pair<String, String>>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val tagName = obj.getString("tag_name")
                        val assets = obj.getJSONArray("assets")
                        var downloadUrl = ""
                        
                        // Debug: Logge, wenn ein Release keine Assets hat
                        if (assets.length() == 0) Log.d(TAG, "Release $tagName hat keine Assets")

                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            val name = asset.getString("name")
                            
                            // Filter: Asset muss .bin/.zip sein UND die Hardware-ID im Namen enthalten
                            // Wenn noch keine HW erkannt wurde (leer), zeigen wir zur Sicherheit alles an
                            val isCorrectHardware = detectedHardwareId.isEmpty() || name.contains(detectedHardwareId, ignoreCase = true)
                            
                            if (isCorrectHardware && (name.endsWith(".bin", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true))) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                        
                        if (downloadUrl.isNotEmpty()) {
                            newList.add(tagName to downloadUrl)
                        } else {
                            Log.d(TAG, "Release $tagName übersprungen (kein passendes Asset)")
                        }
                    }
                    
                    runOnUiThread {
                        firmwareReleases.clear()
                        firmwareReleases.addAll(newList)
                        updateFirmwareListView()
                        updateLog("System: ${newList.size} Releases gefunden")
                    }
                } else {
                    // Verbessertes Fehler-Logging
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Kein Fehlertext"
                    runOnUiThread { updateLog("Fehler: API Antwort ${connection.responseCode} - $error") }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unbekannter Fehler"
                runOnUiThread { updateLog("Fehler: $errorMsg") }
                Log.e(TAG, "Firmware Fetch Error", e)
            }
        }.start()
    }

    private fun updateFirmwareButtonState() {
        runOnUiThread {
            btnDownloadFirmware.isEnabled = isBleConnected && selectedFirmware != null
        }
    }

    private fun updateFirmwareListView() {
        val names = firmwareReleases.map { it.first }
        
        firmwareListView.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, names) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val tagName = getItem(position) ?: ""
                val file = File(filesDir, "firmware_$tagName.bin")
                
                val currentRunningVersion = tvFirmwareVersion.text.toString().replace("Firmware: ", "").trim()
                val isInstalled = (tagName == currentRunningVersion && currentRunningVersion != "Wird geladen...")
                val isCached = file.exists()

                val icon = when {
                    isInstalled -> "✅"
                    isCached -> "📱"
                    else -> "🌐"
                }

                view.text = "$icon $tagName"
                
                // Hintergrund-Logik beibehalten
                if (isInstalled) {
                    view.setBackgroundColor(Color.parseColor("#ADD8E6")) // Hellblau
                } else if (isCached) {
                    view.setBackgroundColor(Color.parseColor("#2E7D32")) // Dunkelgrün
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
                return view
            }
        }
        
        firmwareListView.choiceMode = ListView.CHOICE_MODE_SINGLE
        // Auswahl zurücksetzen, da sich der Cache-Status geändert hat
        firmwareListView.clearChoices() 
        
        firmwareListView.setOnItemClickListener { _, _, position, _ ->
            selectedFirmware = firmwareReleases[position]
            val tagName = selectedFirmware!!.first
            val file = File(filesDir, "firmware_$tagName.bin")
            
            if (file.exists()) {
                btnDownloadFirmware.text = "Flash Firmware"
                btnDownloadFirmware.setOnClickListener { flashFirmware(file) }
            } else {
                btnDownloadFirmware.text = "Firmware herunterladen"
                btnDownloadFirmware.setOnClickListener { downloadFirmware() }
            }
            updateLog("Ausgewählt: $tagName")
            updateFirmwareButtonState()
        }
    }

    private fun flashFirmware(file: File) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(OTA_SERVICE_UUID)
        val char = service?.getCharacteristic(OTA_CHAR_UUID)
        
        if (char == null) {
            updateLog("Fehler: OTA Service nicht gefunden")
            return
        }

        // UI-Update auf dem Main-Thread erzwingen
        runOnUiThread {
            btnDownloadFirmware.isEnabled = false
            btnDownloadFirmware.text = "Flashe..."
        }

        Thread {
            try {
                updateLog("System: Starte OTA...")
                otaLatch = java.util.concurrent.CountDownLatch(1)
                
                // 1. Notification lokal aktivieren
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.setCharacteristicNotification(char, true)
                    
                    val cccUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    val descriptor = char.getDescriptor(cccUuid)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                        Thread.sleep(1000) 
                    }
                }

                // 2. START senden
                val startBytes = "START:$OTA_SECRET_KEY".toByteArray()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(char, startBytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                } else {
                    @Suppress("DEPRECATION")
                    char.value = startBytes
                    @Suppress("DEPRECATION")
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }

                // 3. Warten auf Antwort (max 5s)
                if (!otaLatch!!.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw Exception("Timeout: Keine Antwort vom HUD")
                }

                // 4. Daten senden
                val bytes = file.readBytes()
                val chunkSize = 514 
                var offset = 0
                
                while (offset < bytes.size) {
                    val end = minOf(offset + chunkSize, bytes.size)
                    val chunk = bytes.copyOfRange(offset, end)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(char, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    } else {
                        @Suppress("DEPRECATION")
                        char.value = chunk
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(char)
                    }
                    
                    offset += chunkSize
                    Thread.sleep(30) 
                }

                // 5. END senden
                val endBytes = "END".toByteArray()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(char, endBytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                } else {
                    @Suppress("DEPRECATION")
                    char.value = endBytes
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }
                
                runOnUiThread {
                    updateLog("System: OTA Übertragung abgeschlossen")
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("OTA Update")
                        .setMessage("Firmware erfolgreich übertragen!")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    updateLog("Fehler: OTA fehlgeschlagen: ${e.message}")
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("OTA Update Fehler")
                        .setMessage("Fehler: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } finally {
                // KORREKTUR HIER:
                // Nicht hart auf true setzen, sondern den Status neu berechnen lassen
                runOnUiThread {
                    btnDownloadFirmware.text = "Flash Firmware"
                    updateFirmwareButtonState() 
                }
            }
        }.start()
    }

    private fun clearFirmwareCache() {
        val files = filesDir.listFiles { _, name -> name.startsWith("firmware_") && name.endsWith(".bin") }
        var deletedCount = 0
        files?.forEach { 
            if (it.delete()) deletedCount++ 
        }
        updateLog("System: Cache gelöscht ($deletedCount Dateien)")
        
        // UI aktualisieren
        updateFirmwareListView()
        
        // WICHTIG: ListView explizit invalidieren, falls das Adapter-Setzen nicht reicht
        firmwareListView.invalidateViews()
        
        // Button-Status zurücksetzen
        btnDownloadFirmware.text = "Firmware herunterladen"
        btnDownloadFirmware.setOnClickListener { downloadFirmware() }
        selectedFirmware = null
        updateFirmwareButtonState()
    }

    private fun downloadFirmware() {
        val firmware = selectedFirmware
        if (firmware == null) {
            updateLog("Fehler: Keine Firmware gewählt")
            return
        }

        updateLog("System: Download startet...")
        Thread {
            try {
                val connection = java.net.URL(firmware.second).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                
                if (connection.responseCode == 200) {
                    // Speichern mit Tag-Name
                    val fileName = "firmware_${firmware.first}.bin"
                    val file = File(filesDir, fileName)
                    val inputStream = connection.inputStream
                    val outputStream = FileOutputStream(file)
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    
                    outputStream.close()
                    inputStream.close()
                    
                    runOnUiThread { 
                        updateLog("System: Download fertig: $fileName")
                        updateFirmwareListView()
                        // Button auf Flash umstellen nach Download
                        btnDownloadFirmware.text = "Flash Firmware"
                        btnDownloadFirmware.setOnClickListener { flashFirmware(file) }
                    }
                } else {
                    runOnUiThread { updateLog("Fehler: Download fehlgeschlagen (${connection.responseCode})") }
                }
            } catch (e: Exception) {
                runOnUiThread { updateLog("Fehler: ${e.message}") }
            }
        }.start()
    }

    private fun clearRoute() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_ROUTE_PATH).remove(KEY_ROUTE_NAME).apply()
        routePoints.clear(); displayStoredRoute()
    }

    private fun displayStoredRoute() {
        val name = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ROUTE_NAME, null)
        tvSelectedRoute.text = if (name != null) "Route: $name" else "Keine Route"
    }

    private fun loadRouteIntoMemory() {
        val path = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ROUTE_PATH, null) ?: return
        Thread {
            try {
                val file = File(path)
                if (!file.exists()) return@Thread
                val content = file.readText(); val points = mutableListOf<Location>()
                if (content.contains("<gpx", true)) parseGpx(content, points)
                if (points.isNotEmpty()) {
                    runOnUiThread { routePoints = points; nextPointIndex = 0; isFirstFix = true; updateLog("System: Wegpunkte geladen (${points.size})") }
                }
            } catch (e: Exception) { runOnUiThread { updateLog("System: Ladefehler") } }
        }.start()
    }

    private fun parseGpx(content: String, points: MutableList<Location>) {
        try {
            val parser = Xml.newPullParser(); parser.setInput(content.reader())
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name.lowercase()
                    if (tagName == "trkpt" || tagName == "rtept" || tagName == "wpt") {
                        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        if (lat != null && lon != null) points.add(Location("xml").apply { latitude = lat; longitude = lon })
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {}
    }

    private fun loadPersistedData() { loadLogFromFile(); displayStoredRoute(); loadRouteIntoMemory() }

    private fun loadLogFromFile() {
        val logs = AppLogger.getLogs()
        val content = logs.takeLast(200).joinToString("\n")
        runOnUiThread { tvLogContent.text = content }
    }

    override fun onResume() {
        super.onResume()
        instance = this
        if (tts == null) performTtsHardReset()
        // Force=true, damit beim Öffnen der App sofort gescannt wird
        triggerScanIfDisconnected(force = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        handler.removeCallbacks(reconnectRunnable) // Cleanup
        handler.removeCallbacks(logUpdateRunnable) // Cleanup
        tts?.stop(); tts?.shutdown()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) bluetoothGatt?.close()
    }

    override fun onFlushComplete(requestCode: Int) {}
    override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
}
