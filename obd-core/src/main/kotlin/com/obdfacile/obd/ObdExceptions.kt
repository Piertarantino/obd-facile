package com.obdfacile.obd

import java.io.IOException

/** Errori di dialogo con adattatore/auto (la connessione fisica di per sé è ok). */
open class ObdException(message: String) : IOException(message)

/** L'adattatore non ha restituito il prompt '>' in tempo. */
class ElmTimeoutException(val command: String) :
    ObdException("L'adattatore non risponde (comando $command)")

/** L'ELM327 ha risposto con un errore (CAN ERROR, BUS INIT...ERROR, ?, ...). */
class ElmErrorException(val command: String, val elmMessage: String) :
    ObdException("Errore adattatore su $command: $elmMessage")

/** Adattatore ok, ma nessuna centralina risponde (quadro spento, presa sbagliata...). */
class NoVehicleResponseException(detail: String) :
    ObdException("L'auto non risponde ($detail)")

/** La centralina ha rifiutato la richiesta (risposta negativa 7F). */
class NegativeResponseException(val service: Int, val nrc: Int) :
    ObdException("Richiesta rifiutata dalla centralina: ${describe(nrc)}") {
    companion object {
        fun describe(nrc: Int): String = when (nrc) {
            0x10 -> "rifiuto generico"
            0x11 -> "servizio non supportato"
            0x12 -> "funzione non supportata"
            0x13 -> "formato messaggio errato"
            0x21 -> "centralina occupata, riprova"
            0x22 -> "condizioni non corrette (spegni il motore e lascia il quadro acceso)"
            0x31 -> "valore fuori intervallo"
            0x33 -> "accesso negato"
            0x78 -> "risposta in elaborazione"
            else -> "codice 0x%02X".format(nrc)
        }
    }
}
