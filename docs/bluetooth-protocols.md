# Bluetooth Protocol Documentation / Bluetooth Protokoll-Dokumentation

This document describes the BLE communication between the Android App and the HUD.
Dieses Dokument beschreibt die BLE-Kommunikation zwischen der Android-App und dem HUD.

## 1. Services & UUIDs
The system uses two separate services. / Das System verwendet zwei separate Services.

### UART Service (General Communication / Allgemeine Kommunikation)
- **Service UUID:** `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- **Write Characteristic UUID:** `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`

### OTA Service (Firmware Updates)
- **Service UUID:** `1D14D6EE-FD63-4FA1-BFA4-8F47B42119F0`
- **Write Characteristic UUID:** `1D14D6EF-FD63-4FA1-BFA4-8F47B42119F0`

## 2. Protocol Format / Protokoll-Format
Messages are UTF-8 encoded strings, terminated by a newline (`\n`). OTA data is binary.
Nachrichten sind UTF-8 kodierte Strings, abgeschlossen mit einem Zeilenumbruch (`\n`). OTA-Daten sind binär.

### Text Commands / Text-Befehle
1. **`NAV:` (Navigation)**
   - Format: `NAV:<Message>`
   - Example: `NAV:Turn left` / `NAV:Links abbiegen`

2. **`SPD:` (Speed / Geschwindigkeit)**
   - Format: `SPD:<Speed_in_km/h>`
   - Example: `SPD:25`

3. **`PKT:` (Waypoint / Wegpunkt)**
   - Format: `PKT:<Angle>;<Distance>;<Index>`
   - Example: `PKT:45;120;5`

4. **`STT:` (System Status)**
   - Format: `STT:<Status_Message>`
   - Example: `STT:TTS ready.` / `STT:TTS bereit.`

### System Requests / System-Abfragen
1. **`GET_HW` (Hardware ID)**
   - Request: `GET_HW`
   - Response: `HW:<Hardware_ID>` (e.g., `HW:ESP32-2424S012-V1.0`)

2. **`GET_FW` (Firmware Version)**
   - Request: `GET_FW`
   - Response: `FW:<Version>` (e.g., `FW:1.0.0`)

## 3. OTA Protocol (Binary / Binär)
1. **Handshake:**
   - App sends: `START:<OTA_SECRET_KEY>`
   - Device responds: `READY`

2. **Data Transfer / Datenübertragung:**
   - Binary chunks (MTU-optimized, ~514 bytes).
   - Mode: `WRITE_NO_RESPONSE`.
   - Flow Control: 30ms delay between packets. / 30ms Pause zwischen Paketen.

3. **Completion / Abschluss:**
   - App sends: `END`
   - Device validates and reboots. / Gerät validiert und startet neu.

## OsmAnd Integration
- **Format:** `NAV:<Instruction>|OSMAND`
- **Processing:** Removes redundant titles, replaces newlines with `|`. / Entfernt redundante Titel, ersetzt Zeilenumbrüche durch `|`.

## Transmission / Übertragung
- Messages are sent via `writeCharacteristic`.
- App ensures every text message ends with `\n`. / Die App stellt sicher, dass jede Text-Nachricht mit `\n` endet.
