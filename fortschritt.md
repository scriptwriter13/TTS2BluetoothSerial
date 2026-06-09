// FILE: fortschritt.md
// STATUS: FULL ABSOLUTE CONTROL (MASTER ANKER)
// DATE: 2026-03-29

# Fortschritt

- [x] Versionsnummer im Gerätetab anzeigen
- [x] Git-Commit-Hash im Gerätetab anzeigen
- [x] Build-Fehler behoben: buildConfig aktiviert
- [x] Fix: Routen-Springen durch begrenztes Suchfenster in reLockPoint behoben
- [x] App-Package auf ch.scriptwriter.tts2bluetoothserial umgestellt
- [x] AndroidManifest.xml auf neues Package aktualisiert
- [x] Fix: package-Attribut aus AndroidManifest.xml entfernt
- [x] Fix: BootReceiver.kt auf neues Package aktualisiert
- [x] Fix: Paketstruktur auf ch.scriptwriter.tts2bluetoothserial umgestellt
- [x] Fix: Import von NotificationService korrigieren
- [x] Feature: GPS-Standortzugriff bei Inaktivität (>2min) deaktivieren
- [x] Docs: Bluetooth-Protokoll-Dokumentation inkl. OsmAnd-Beispielen aktualisiert
- [x] Feature: Robuste Bluetooth-Reconnect-Logik implementiert (Optimierung: autoConnect=false für manuelle Kontrolle, Intervall auf 3s verkürzt)
- [x] GPLv3 Header in alle Kotlin-Dateien eingefügt
- [x] Optimierung: Aggressiverer Reconnect-Scan (Cooldown 10s, periodischer Scan alle 15s)
- [x] Feature: 2-Minuten-Timeout für BLE-Verbindungsaufbau bei Inaktivität in MyBluetoothTtsService implementiert
- [x] Feature: 2-Minuten-Timeout für BLE-Verbindungsaufbau bei Inaktivität in NotificationService implementiert
- [x] Feature: Zentrale AppLogger-Klasse mit Cache- und Disk-Persistence implementiert
- [x] Feature: Caching der letzten TTS-Nachricht und Re-Send bei Reconnect (Fix: Umstellung auf statischen BrdcastReceiver + SharedPreferences)
- [x] Fix: BrdcastReceiver lässt SPD-Meldungen jetzt auch bei deaktiviertem TTS/Notification durch (Bypass-Logik)
- [x] Feature: Commit-Message als Parameter in deploy_to_github.sh
- [x] Feature: Interaktive Commit/Push-Abfrage in deploy_to_github.sh
- [x] Fix: Automatisches Tagging aus deploy_to_github.sh entfernt
