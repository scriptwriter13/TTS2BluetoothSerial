# 🗺 Technical Blueprint: Bike HUD Android Master

## 1. System-Architektur (Data Flow)
Der Datenfluss ist unidirektional vom Android-System zum ESP32-HUD:
1. **Input:** `MyBluetoothTtsService` (fängt System-TTS-Events ab).
2. **Processing:** `MainActivity` (filtert GPS-Speed, mittelt Werte, verwaltet BLE-Queue).
3. **Output:** `BluetoothLeService` (schreibt in die UART-Charakteristik des C3).

## 2. Kommunikations-Stack (BLE)
| Schicht | Spezifikation |
| :--- | :--- |
| **Profile** | BLE GATT (Generic Attribute Profile) |
| **MTU Size** | 512 Bytes (angefordert durch Client) |
| **Write Type** | `WRITE_TYPE_NO_RESPONSE` (für minimale Latenz) |
| **UUIDs** | Service: `...DCCA9E`, TX: `...DCCA9E` (Nordic UART) |

## 3. UI-Komponenten & State-Management
Das UI basiert auf einem **Tab-Switcher-Modell**:
- **State `isDevices`:** Zeigt `containerDevices` (Scan-Ergebnisse).
- **State `!isDevices`:** Zeigt `containerLog` (Echtzeit-Konsole).
- **Persistenz:** Das Log wird beim Schreiben in `bike_log.txt` gestreamt und beim `onCreate` vollständig in den Speicher geladen.

## 4. Farb- & Design-System
Zentrale Variablen für die Konsistenz zwischen XML und Kotlin:
- `bike_blue_active`: Primäre Handlungsfarbe für Buttons und aktive Tabs.
- `gray_inactive`: Sekundärfarbe für inaktive Zustände.
- `black`: Hintergrundfarbe für das Log-Fenster (maximale Energieeffizienz bei OLED).

## 5. Berechtigungs-Matrix
Die App fordert zur Laufzeit folgende kritische Rechte an:
- `ACCESS_FINE_LOCATION`: Notwendig für GPS-Speed und BLE-Scanning.
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`: (Ab Android 12) Für die Kommunikation.
- `QUERY_ALL_PACKAGES`: Um Organic Maps im TTS-Dienst zu identifizieren.

## 6. Error Handling Strategy
- **BLE-Abbruch:** Automatischer Null-Check der `writeCharacteristic`.
- **GPS-Verlust:** Letzter bekannter Speed wird gehalten, bis Timeout greift.
- **Resource Linking:** Synchronisierte `values` und `values-night` Ordner zur Vermeidung von Build-Abstürzen.