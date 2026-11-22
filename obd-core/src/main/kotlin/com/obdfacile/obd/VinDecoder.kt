package com.obdfacile.obd

import com.obdfacile.obd.elm.EcuMessage
import com.obdfacile.obd.util.u

/**
 * VIN dal modo 09 PID 02.
 * CAN: un unico messaggio 49 02 01 + 17 caratteri.
 * Protocolli vecchi: 5 righe 49 02 0n + 4 byte, la prima con zeri di riempimento.
 * In entrambi i casi si saltano i primi 3 byte di ogni messaggio, si tengono i soli
 * caratteri validi in un VIN (niente I, O, Q) e si prendono gli ultimi 17.
 */
object VinDecoder {
    fun decode(messages: List<EcuMessage>): String? {
        for ((_, msgs) in messages.groupBy { it.ecu }) {
            val sb = StringBuilder()
            for (m in msgs) {
                for (i in 3 until m.data.size) {
                    val c = m.data.u(i).toChar()
                    if (isVinChar(c)) sb.append(c)
                }
            }
            if (sb.length >= 17) return sb.substring(sb.length - 17)
        }
        return null
    }

    private fun isVinChar(c: Char) = c in '0'..'9' || (c in 'A'..'Z' && c != 'I' && c != 'O' && c != 'Q')
}
