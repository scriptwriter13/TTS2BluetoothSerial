# 🗺 Technical Blueprint: Bike HUD Android Master

## 1. System-Architektur (Data Flow)
Der Datenfluss ist unidirektional vom Android-System zum ESP32-HUD:
1. **Input:** `MyBluetoothTtsService` (TTS) & `NotificationService` (Benachrichtigungen) fangen System-Events ab.
2. **Processing:** `MainActivity` (GPS-Speed, Routen-Logik, BLE-Queue-Management).
3. **Output:** `BluetoothService` (GATT-Verbindung, UART-Schreibvorgänge).
4. **Lifecycle:** `BootReceiver` sorgt für den automatischen App-Start nach System-Reboot.

## 2. Kommunikations-Stack (BLE)
| Schicht | Spezifikation |
| :--- | :--- |
| **Profile** | BLE GATT (Generic Attribute Profile) |
| **MTU Size** | 512 Bytes |
| **Write Type** | `WRITE_TYPE_NO_RESPONSE` (für minimale Latenz) |
| **Reconnect** | Manuelle Steuerung (`autoConnect=false`), 3s Intervall, 15s Scan-Zyklus |
| **UUIDs** | Service: `6E400001...`, TX: `6E400002...` (Nordic UART) |

## 3. UI-Komponenten & State-Management
Das UI basiert auf einem **Tab-Switcher-Modell**:
- **State `isDevices`:** Zeigt `containerDevices` (Scan-Ergebnisse).
- **State `!isDevices`:** Zeigt `containerLog` (Echtzeit-Konsole).
- **Persistenz:** `AppLogger` verwaltet Log-Dateien (`bike_log.txt`) mit Disk-Persistence.

## 4. Logging & Debugging
- **Zentraler Logger:** `AppLogger` Klasse für konsistente Log-Ausgabe auf Disk und UI.
- **Anti-Spam:** Log-Cooldowns für BLE-Scans und GPS-Updates.
- **Share-Funktion:** Log-Dateien können via `FileProvider` geteilt werden.

## 5. Berechtigungs-Matrix
- `ACCESS_FINE_LOCATION`: GPS-Speed & BLE-Scanning.
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`: BLE-Kommunikation.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: Abfangen von Navigations-Benachrichtigungen.
- `POST_NOTIFICATIONS`: Für Foreground-Services.

## 6. Error Handling Strategy
- **BLE-Abbruch:** Automatischer Reconnect-Timer (3s) im `BluetoothService`.
- **GPS-Verlust:** 2-Minuten-Timeout-Logik (`GPS_TIMEOUT`) zur Schonung des Akkus.
- **TTS-Reset:** `performTtsHardReset()` bei Verbindungs- oder Service-Problemen.

## 7. Deployment
- **Skript:** `scripts/deploy_to_github.sh` automatisiert den Sync zum GitHub-Repository.
- **Prozess:** Bereinigung von Build-Artefakten, APK-Kopie und interaktiver Git-Workflow.
