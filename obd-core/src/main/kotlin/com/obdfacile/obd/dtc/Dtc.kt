package com.obdfacile.obd.dtc

import com.obdfacile.obd.elm.EcuMessage
import com.obdfacile.obd.util.u

enum class DtcKind(val mode: Int, val label: String, val explanation: String) {
    STORED(0x03, "Memorizzati", "Errori confermati: accendono la spia motore."),
    PENDING(0x07, "In attesa", "Anomalie rilevate in questo ciclo, non ancora confermate."),
    PERMANENT(0x0A, "Permanenti", "Non si cancellano a mano: spariscono da soli quando la centralina verifica che il guasto è risolto."),
}

data class Dtc(val code: String, val kind: DtcKind, val ecu: String) {
    val description: String get() = DtcDescriptions.describe(code)
}

data class DtcReport(
    val stored: List<Dtc>,
    val pending: List<Dtc>,
    val permanent: List<Dtc>,
) {
    val isEmpty: Boolean get() = stored.isEmpty() && pending.isEmpty() && permanent.isEmpty()
    fun byKind(kind: DtcKind) = when (kind) {
        DtcKind.STORED -> stored
        DtcKind.PENDING -> pending
        DtcKind.PERMANENT -> permanent
    }
}

object DtcDecoder {
    private const val SYSTEMS = "PCBU"

    /** Due byte -> codice tipo "P0420". Restituisce null per il riempitivo 00 00. */
    fun decodePair(a: Int, b: Int): String? {
        if (a == 0 && b == 0) return null
        return "%c%d%X%X%X".format(
            SYSTEMS[a shr 6], (a shr 4) and 0x03, a and 0x0F, b shr 4, b and 0x0F,
        )
    }

    /**
     * Decodifica una risposta ai modi 03/07/0A.
     * Su CAN il primo byte dopo il servizio è il numero di codici; sui protocolli
     * vecchi seguono direttamente coppie di byte (riempite con 00 00).
     */
    fun decode(message: EcuMessage, isCan: Boolean): List<String> {
        val d = message.data
        var i = if (isCan) 2 else 1
        val codes = mutableListOf<String>()
        while (i + 1 < d.size) {
            decodePair(d.u(i), d.u(i + 1))?.let(codes::add)
            i += 2
        }
        return codes
    }
}
