# OBD Facile — contesto per Claude

Motto: **"Gratis, senza pubblicità, per sempre. L'auto è tua: riparala tu. Mortacci loro."**

App Android OBD-II gratuita, semplice, per diagnosticare e cancellare errori sulle auto
di proprietà dell'utente, in alternativa ai pacchetti commerciali costosi.
Lingua del progetto: **italiano** (UI, commenti, documentazione).

## Struttura
- Vedi `README.md` per architettura, scelte di comunicazione con l'ELM327 e roadmap.
- `obd-core/`: Kotlin puro (ELM327, parser CAN/ISO-TP e K-Line, PID, DTC, VIN). Testabile su JVM: `./gradlew :obd-core:test`.
- `app/`: Android, Kotlin + Jetpack Compose, minSdk 26, trasporti Bluetooth classico / BLE / Wi-Fi.

## Regole
- Nessuna pubblicità, nessun tracciamento, nessun acquisto in-app.
- Niente codice o dati copiati da app commerciali (es. Car Scanner): solo standard pubblici (SAE J1979/J2012, ISO 15765) e prove sulle nostre auto.
- Il dialogo con l'ELM327 passa sempre da `ElmDevice` (un comando alla volta, mai interrotto a metà).

## Stato (22/11/2026)
- v0.1 scritta ma **mai compilata**: sul portatile iniziale mancavano JDK/SDK. Primo passo sul PC fisso: aprire in Android Studio, sistemare eventuali errori di compilazione, far girare i test di obd-core.
- Manca `gradle/wrapper/gradle-wrapper.jar` (lo rigenera Android Studio o `gradle wrapper`).
- Prossimi passi: vedi "Prossimi passi" nel README (foreground service, USB, freeze frame, DB DTC completo, multi-PID, funzioni per marca via UDS).
