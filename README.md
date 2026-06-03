# Bike HUD - TTS to Bluetooth Serial Master

Dieses Projekt ist eine Android-Brücke, die Navigationsansagen (via TTS-Engine) und GPS-Geschwindigkeit abfängt und per Bluetooth Low Energy (BLE) an ein ESP32-basiertes Display sendet.

## 🚀 Kernfunktionen
- **Smart-Scan:** Automatisches Finden und Verbinden mit dem Bike-HUD beim App-Start.
- **Log-Speicher:** Persistentes Logging (`bike_log.txt`), das auch nach einem Neustart erhalten bleibt.
- **Dunkel-Design:** Seriöses Blau-Schwarzes Interface für maximale Lesbarkeit bei Sonnenlicht.
- **TTS-Abfang:** Eigener TTS-Service, der Texte von Apps wie Organic Maps direkt in BLE-Pakete umwandelt.

## 🛠 Technische Spezifikationen

### Bluetooth Low Energy (BLE)
- **Service UUID:** `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- **TX Characteristic UUID:** `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
- **MTU:** 512 Bytes (für lange Navi-Texte).

### Datenprotokoll
Die Daten werden als Klartext mit einem Newline-Zeichen (`\n`) am Ende gesendet:
- `SPD:25` -> Geschwindigkeit ist 25 km/h.
- `NAV:In 100m links abbiegen` -> Navigationsanweisung.

## 🎨 Farbkonzept
Zentral abgelegt in `res/values/colors.xml`:
- **Hintergrund:** `#121212` (Dark) / `#000000` (Log)
- **Aktiv-Blau:** `#1976D2` (Business Blue)
- **Text:** `#FFFFFF` (Maximaler Kontrast)

## 📂 Projektstruktur
- `MainActivity.kt`: Steuerung, BLE-Logik, GPS-Updates und UI-Management.
- `MyBluetoothTtsService.kt`: Schnittstelle zum Android System-TTS.
- `AndroidManifest.xml`: Berechtigungen für Bluetooth, GPS und TTS-Queries.