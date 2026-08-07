# Bluetooth Protokoll-Dokumentation

Diese App kommuniziert über Bluetooth Low Energy (BLE) mit einem Head-Up-Display (HUD). Es werden zwei separate Services verwendet: einer für die allgemeine Kommunikation (UART) und einer für Firmware-Updates (OTA).

## 1. UART Service (Allgemeine Kommunikation)
Dieser Service wird für Navigation, Geschwindigkeit und System-Status verwendet.
- **Service UUID:** `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- **Write Charakteristik UUID:** `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`

## 2. OTA Service (Firmware Updates)
Dieser Service wird ausschließlich für den Flash-Vorgang genutzt.
- **Service UUID:** `1D14D6EE-FD63-4FA1-BFA4-8F47B42119F0`
- **Write Charakteristik UUID:** `1D14D6EF-FD63-4FA1-BFA4-8F47B42119F0`

## Protokoll-Format
Die meisten Nachrichten werden als UTF-8 kodierte Strings gesendet und müssen mit einem Zeilenumbruch (`\n`) abgeschlossen werden. Ausnahmen bilden die binären OTA-Datenpakete.

### 1. Unterstützte Befehls-Präfixe (Text-basiert)

1. **`NAV:` (Navigation)**
   - Format: `NAV:<Nachricht>`
   - Beispiel: `NAV:Links abbiegen`

2. **`SPD:` (Geschwindigkeit)**
   - Format: `SPD:<Geschwindigkeit_in_km/h>`
   - Beispiel: `SPD:25`

3. **`PKT:` (Wegpunkt-Information)**
   - Format: `PKT:<Winkel>;<Distanz>;<Index>`
   - Beispiel: `PKT:45;120;5`

4. **`STT:` (System-Status)**
   - Format: `STT:<Statusmeldung>`
   - Beispiel: `STT:TTS bereit.`

### 2. System-Abfragen (Request/Response)

Diese Befehle werden von der App gesendet, um Geräteinformationen abzurufen.

1. **`GET_HW` (Hardware-Abfrage)**
   - App sendet: `GET_HW`
   - Gerät antwortet: `HW:<Hardware_ID>` (z.B. `HW:ESP32-2424S012-V1.0`)

2. **`GET_FW` (Firmware-Abfrage)**
   - App sendet: `GET_FW`
   - Gerät antwortet: `FW:<Version>` (z.B. `FW:1.0.0`)

### 3. OTA-Protokoll (Binär-basiert)

Für Firmware-Updates wird ein spezielles Protokoll verwendet.

1. **Handshake:**
   - App sendet: `START:<OTA_SECRET_KEY>`
   - Gerät antwortet: `READY` (bei Erfolg)

2. **Datenübertragung:**
   - Binäre Chunks (MTU-optimiert, ca. 514 Bytes).
   - Modus: `WRITE_NO_RESPONSE`.
   - Flow Control: 30ms Pause zwischen den Paketen.

3. **Abschluss:**
   - App sendet: `END`
   - Gerät validiert und startet neu.

## Besonderheiten bei OsmAnd
Die App erkennt Benachrichtigungen von OsmAnd automatisch.
- **Format:** `NAV:<Anweisung>|OSMAND`
- **Verarbeitung:** Entfernt redundante Titel, ersetzt Zeilenumbrüche durch `|`.

## Übertragung
- Die Nachrichten werden über die `writeCharacteristic` gesendet.
- Die App stellt sicher, dass jede Text-Nachricht mit einem `\n` endet.
