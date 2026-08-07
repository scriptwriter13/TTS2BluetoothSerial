# 🗺 Technical Blueprint: Bike HUD Android Master

## 1. System-Architektur (Data Flow)
Der Datenfluss ist unidirektional vom Android-System zum ESP32-HUD:
1. **Input:** `MyBluetoothTtsService` (TTS) & `NotificationService` (Benachrichtigungen) fangen System-Events ab.
2. **Processing:** `MainActivity` (GPS-Speed, Routen-Logik, BLE-Queue-Management, Firmware-Update-Logik).
3. **Output:** `BluetoothService` (GATT-Verbindung, UART-Schreibvorgänge, OTA-Flash-Protokoll).
4. **Lifecycle:** `BootReceiver` sorgt für den automatischen App-Start nach System-Reboot.

## 2. Kommunikations-Stack (BLE)
| Schicht | Spezifikation |
| :--- | :--- |
| **Profile** | BLE GATT (Generic Attribute Profile) |
| **MTU Size** | 517 Bytes (angefordert) |
| **Write Type** | `WRITE_TYPE_NO_RESPONSE` (für minimale Latenz) |
| **Reconnect** | Manuelle Steuerung (`autoConnect=false`), 3s Intervall, 15s Scan-Zyklus |
| **UART UUIDs** | Service: `6E400001...`, TX: `6E400002...` |
| **OTA UUIDs** | Service: `1D14D6EE...`, TX: `1D14D6EF...` |

## 3. UI-Komponenten & State-Management
Das UI basiert auf einem **Tab-Switcher-Modell**:
- **State `isDevices`:** Zeigt `containerDevices` (Scan-Ergebnisse).
- **State `isLog`:** Zeigt `containerLog` (Echtzeit-Konsole).
- **State `isRoute`:** Zeigt `containerRoute` (Routen-Management).
- **State `isFirmware`:** Zeigt `containerFirmware` (OTA-Update, Hardware-Info).
- **Persistenz:** `AppLogger` verwaltet Log-Dateien (`bike_log.txt`) mit Disk-Persistence.

## 4. Firmware & OTA-Logik
- **GitHub Integration:** Abfrage der Releases via GitHub API (mit `User-Agent` Header).
- **Hardware-Filterung:** Assets werden nach Hardware-ID im Dateinamen gefiltert (`GET_HW`).
- **OTA-Protokoll:**
    - Handshake: `START:<SECRET_KEY>` -> `READY`.
    - Chunking: 514 Bytes Chunks mit 30ms Flow-Control-Pause.
    - Abschluss: `END`.
- **Caching:** Firmware-Dateien werden lokal in `filesDir` gespeichert und visuell in der Liste markiert (Grün=Cache, Hellblau=Installiert).

## 5. Berechtigungs-Matrix
- `ACCESS_FINE_LOCATION`: GPS-Speed & BLE-Scanning.
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`: BLE-Kommunikation.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: Abfangen von Navigations-Benachrichtigungen.
- `POST_NOTIFICATIONS`: Für Foreground-Services.

## 6. Error Handling Strategy
- **BLE-Abbruch:** Automatischer Reconnect-Timer (3s) im `BluetoothService`.
- **GPS-Verlust:** 2-Minuten-Timeout-Logik (`GPS_TIMEOUT`) zur Schonung des Akkus.
- **OTA-Fehler:** Timeout-Handling bei Handshake und Validierung durch ESP32-Core.

## 7. Deployment
- **Skript:** `scripts/deploy_to_github.sh` automatisiert den Sync zum GitHub-Repository.
- **Prozess:** Bereinigung von Build-Artefakten, APK-Kopie und interaktiver Git-Workflow.
