# Architektur-Übersicht: Hauptkomponenten

*   **`MainActivity.kt` (Zentrale Steuereinheit)**
    *   **Rolle:** Das "Gehirn" der App.
    *   **Verantwortlichkeiten:** Verwaltet die gesamte UI, die Bluetooth-Low-Energy-Kommunikation (Scanning, Verbindung, GATT-Services, OTA-Firmware-Updates), die GPS-Standortverarbeitung, die TTS-Ausgabe und die Koordination zwischen den verschiedenen Diensten. Sie ist der zentrale Ankerpunkt für den Verbindungsstatus und die einzige Stelle, an der BLE-Logik implementiert ist.

*   **`NotificationService.kt` (Die Brücke)**
    *   **Rolle:** Hintergrund-Listener für externe Daten.
    *   **Verantwortlichkeiten:** Implementiert als `NotificationListenerService`. Überwacht eingehende Benachrichtigungen von konfigurierten Navigations-Apps, filtert und bereinigt diese (Overlap-Merging) und leitet relevante Navigationsanweisungen an die `MainActivity` weiter, damit sie via BLE an das HUD übertragen werden können.

*   **`AppLogger.kt` (Der Recorder)**
    *   **Rolle:** Zentrale, persistente Protokollierung.
    *   **Verantwortlichkeiten:** Stellt eine thread-sichere Schnittstelle für das Logging bereit. Er puffert Log-Einträge im Speicher und schreibt sie periodisch auf den internen Speicher (Disk-Persistence). Dies ist essenziell für das Debugging und die Fehleranalyse im Feld.

*   **`BootReceiver.kt` (Der Starter)**
    *   **Rolle:** System-Integration.
    *   **Verantwortlichkeiten:** Empfängt das `ACTION_BOOT_COMPLETED` Broadcast-Event des Android-Systems. Er sorgt dafür, dass die App nach einem Neustart des Geräts automatisch gestartet wird, um die Hintergrundüberwachung und Verbindung zum HUD sicherzustellen.

*   **`fortschritt.md` (Projekt-Steuerung)**
    *   **Rolle:** Single Source of Truth für den Entwicklungsfortschritt.
    *   **Verantwortlichkeiten:** Dient als integraler Bestandteil des Workflows, um den Überblick über erledigte Features, Fixes und den aktuellen Status der Codebasis zu behalten.

Diese Struktur zeigt eine **zentralisierte Architektur**, bei der die `MainActivity` die Hauptlast der Logik trägt, während spezialisierte Dienste (`NotificationService`) und Hilfsklassen (`AppLogger`) für die Peripherie-Aufgaben zuständig sind.
