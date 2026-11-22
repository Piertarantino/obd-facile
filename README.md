# OBD Facile

> **Gratis, senza pubblicità, per sempre. L'auto è tua: riparala tu. Mortacci loro.**

Scanner OBD-II per Android, gratuito e semplice, per leggere e cancellare gli errori
delle **nostre** auto senza pagare abbonamenti o pacchetti da centinaia di euro.
Funziona con gli adattatori ELM327 economici (Bluetooth, Bluetooth LE, Wi-Fi).

## Cosa fa già (v0.1)

- Connessione ad adattatori **Bluetooth classico**, **BLE** e **Wi-Fi**
- Riconoscimento automatico del protocollo (CAN 11/29 bit, KWP2000, ISO 9141, J1850)
- **Errori**: lettura dei codici memorizzati, in attesa e permanenti, con descrizione in italiano
- **Cancellazione errori** (modo 04) con avvisi chiari e verifica dopo la cancellazione
- **Cruscotto** in tempo reale (giri, velocità, temperature, carico, MAF, tensione, …)
- Stato spia motore e **test di prontezza** (utile prima della revisione)
- VIN, protocollo, tensione batteria, console dei comandi per il debug

## Struttura

```
obd-core/   Kotlin puro, nessuna dipendenza Android -> testabile su PC
  transport/   ObdTransport (interfaccia), StreamTransport, WifiTransport
  elm/         ElmDevice (dialogo ELM327), ResponseParser (CAN/ISO-TP, K-Line), ObdProtocol
  pid/         Pids (formule SAE J1979), MonitorStatus (spia + readiness)
  dtc/         DtcDecoder, DtcDescriptions (italiano)
  ObdClient    API ad alto livello: connect, readPid, readAllDtcs, clearDtcs, readVin
app/        Android (Kotlin + Jetpack Compose)
  transport/   BluetoothClassicTransport, BleTransport, DeviceScanner
  ObdViewModel stato, polling, gestione errori
  ui/          Connessione, Cruscotto, Errori, Auto
```

## Come parla con l'auto (le scelte che contano)

| Scelta | Perché |
|---|---|
| Un comando alla volta (Mutex) e mai interrotto a metà | l'ELM327 è half-duplex: un byte inviato durante un comando lo annulla |
| Svuotamento buffer prima di ogni comando, CR di recupero dopo un timeout | evita risposte "sfasate", il problema n.1 con i cloni |
| `ATE0 ATL0 ATS0` | niente eco/spazi: circa il 30% di byte in meno per ogni risposta |
| `ATH1` (header attivi) | distingue le centraline e permette di riassemblare i messaggi multi-frame |
| `ATAT1` | timing adattivo: l'adattatore aspetta solo il necessario |
| Suffisso `1` nelle richieste (`010C1`) se risponde una sola centralina | l'ELM non aspetta il timeout per altre risposte: polling molto più veloce. Si disattiva da solo sui cloni che non lo supportano |
| PID veloci a ogni giro, temperature/livelli ogni 5 giri | aggiornamento fluido di giri e velocità |
| PID che non rispondono mai esclusi dal polling | niente tempo perso in timeout |
| Socket Wi-Fi legato alla rete dell'adattatore | altrimenti Android usa i dati mobili e la connessione fallisce |
| Bluetooth: socket standard, poi non cifrato, poi canale 1 diretto | compatibilità con i cloni cinesi |

## Aprire e compilare

1. Installa **Android Studio** (include JDK e SDK).
2. *File → Open* → cartella `obd-facile`.
3. Test del core (girano su PC, senza auto): `./gradlew :obd-core:test`
   APK di debug da terminale: `./gradlew assembleDebug` (serve `local.properties` con `sdk.dir=...`, Android Studio lo crea da solo).
4. Avvia l'app su un telefono vero (l'emulatore non ha il Bluetooth).

## Prossimi passi

1. **Servizio in primo piano** (foreground service `connectedDevice`) per non perdere la connessione a schermo spento
2. **Adattatori USB** (OTG) e scelta manuale del protocollo se l'automatico fallisce
3. **Freeze frame** (modo 02): i valori del motore nel momento in cui è nato l'errore
4. **Database DTC completo** in italiano, da file (nostro o con licenza compatibile)
5. Richieste **multi-PID** (fino a 6 PID per richiesta su CAN) e `ATSH 7E0` per parlare solo alla centralina motore: polling ancora più veloce
6. **Registrazione viaggi** ed esportazione CSV dei dati
7. Profili adattatore (cloni noti con bug) e test sul campo con più auto
8. **Funzioni avanzate per marca** (UDS / modo 22: centralina cambio, ABS, airbag, reset service, rigenerazione DPF): qui stanno le cose che fanno pagare. Vanno costruite con documentazione pubblica e con prove sulle nostre auto

## Note

- Cancellare gli errori **non ripara il guasto** e azzera i test di prontezza: prima della revisione serve guidare qualche giorno.
- Cancella a **motore spento e quadro acceso**.
- Le formule dei PID vengono dallo standard pubblico SAE J1979. Niente codice o dati presi da app commerciali.
