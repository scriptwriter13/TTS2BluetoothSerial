# Bluetooth Protokoll-Dokumentation

Diese App kommuniziert über Bluetooth Low Energy (BLE) mit einem Head-Up-Display (HUD) oder einem ähnlichen Gerät. Die Kommunikation erfolgt über das UART-Service-Profil (Nordic Semiconductor UART Service).

## Service & Charakteristiken
- **Service UUID:** `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- **Write Charakteristik UUID:** `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`

## Protokoll-Format
Alle Nachrichten werden als UTF-8 kodierte Strings gesendet und müssen mit einem Zeilenumbruch (`\n`) abgeschlossen werden.

### Unterstützte Befehls-Präfixe

Die App sendet Daten mit spezifischen Präfixen, um dem Empfänger die Art der Information mitzuteilen:

1. **`NAV:` (Navigation)**
   - Format: `NAV:<Nachricht>`
   - Beschreibung: Allgemeine Navigationsanweisungen oder Textnachrichten, die auf dem HUD angezeigt werden sollen.
   - Beispiel: `NAV:Links abbiegen`

2. **`SPD:` (Geschwindigkeit)**
   - Format: `SPD:<Geschwindigkeit_in_km/h>`
   - Beschreibung: Übermittelt die aktuelle Geschwindigkeit. Der Wert wird als Ganzzahl gesendet.
   - Beispiel: `SPD:25`

3. **`PKT:` (Wegpunkt-Information)**
   - Format: `PKT:<Winkel>;<Distanz>;<Index>`
   - Beschreibung: Übermittelt Navigationsdaten für den nächsten Wegpunkt.
     - `<Winkel>`: Relativer Winkel zum Ziel in Grad.
     - `<Distanz>`: Distanz zum Ziel in Metern.
     - `<Index>`: Index des aktuellen Wegpunkts in der geladenen Route.
   - Beispiel: `PKT:45;120;5`

4. **`STT:` (System-Status)**
   - Format: `STT:<Statusmeldung>`
   - Beschreibung: Systeminterne Statusmeldungen oder TTS-Bereitschaftsmeldungen.
   - Beispiel: `STT:TTS bereit.`

## Besonderheiten bei OsmAnd
Die App erkennt Benachrichtigungen von OsmAnd automatisch anhand des Paketnamens. Wenn eine Nachricht von OsmAnd stammt, wird sie speziell formatiert und mit einem Suffix versehen, um dem HUD die Herkunft zu signalisieren.

- **Format:** `NAV:<Anweisung>|OSMAND`
- **Verarbeitung:** Der `NotificationService` entfernt redundante Titel-Informationen (Overlap-Merging) und ersetzt Zeilenumbrüche durch ein Pipe-Symbol (`|`).
- **Beispiel:**
  - Original-Benachrichtigung: "In 200m rechts abbiegen"
  - Gesendetes Protokoll: `NAV:In 200m rechts abbiegen|OSMAND`

## Übertragung
- Die Nachrichten werden über die `writeCharacteristic` mit `WRITE_TYPE_NO_RESPONSE` gesendet, um die Latenz zu minimieren.
- Die App stellt sicher, dass jede Nachricht mit einem `\n` endet, falls dies nicht bereits im String enthalten ist.
