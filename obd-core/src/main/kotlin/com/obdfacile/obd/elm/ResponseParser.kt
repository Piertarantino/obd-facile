package com.obdfacile.obd.elm

import com.obdfacile.obd.util.Hex
import com.obdfacile.obd.util.u

/**
 * Un messaggio OBD completo da una centralina.
 * [ecu] = header CAN (es. "7E8") o indirizzo sorgente sui protocolli vecchi (es. "10").
 * [data] inizia con il byte di servizio di risposta (es. 0x41 per il modo 01).
 */
class EcuMessage(val ecu: String, val data: ByteArray) {
    override fun toString() = "EcuMessage($ecu: ${Hex.encode(data)})"
}

/**
 * Converte le righe esadecimali dell'ELM327 (ATH1 + ATS0) in messaggi OBD completi.
 *
 * CAN (ISO 15765-4): raggruppa per centralina e riassembla i messaggi multi-frame
 * ISO-TP (First Frame + Consecutive Frames), necessari per VIN e liste DTC lunghe.
 * Protocolli vecchi (K-Line, J1850): 3 byte di header + dati + 1 byte checksum per riga.
 */
object ResponseParser {
    private val HEX_LINE = Regex("^[0-9A-F]+$")

    fun parse(lines: List<String>, protocol: ObdProtocol): List<EcuMessage> {
        val clean = lines.map { it.replace(" ", "") }.filter { HEX_LINE.matches(it) }
        return if (protocol.isCan) parseCan(clean, protocol.canHeaderChars) else parseLegacy(clean)
    }

    private fun parseCan(lines: List<String>, headerChars: Int): List<EcuMessage> {
        val framesByEcu = LinkedHashMap<String, MutableList<ByteArray>>()
        for (line in lines) {
            if (line.length < headerChars + 4) continue
            val payload = line.substring(headerChars)
            if (payload.length % 2 != 0) continue
            framesByEcu.getOrPut(line.substring(0, headerChars)) { mutableListOf() }
                .add(Hex.decode(payload))
        }

        val out = mutableListOf<EcuMessage>()
        for ((ecu, frames) in framesByEcu) {
            var buffer: MutableList<Byte>? = null
            var expected = 0
            for (frame in frames) {
                val pci = frame.u(0)
                when (pci shr 4) {
                    0x0 -> { // Single Frame
                        val len = pci and 0x0F
                        if (len in 1 until frame.size) out += EcuMessage(ecu, frame.copyOfRange(1, 1 + len))
                    }
                    0x1 -> { // First Frame
                        if (frame.size < 3) continue
                        expected = ((pci and 0x0F) shl 8) or frame.u(1)
                        buffer = frame.copyOfRange(2, frame.size).toMutableList()
                    }
                    0x2 -> buffer?.addAll(frame.copyOfRange(1, frame.size).toList()) // Consecutive Frame
                }
                val b = buffer
                if (b != null && b.size >= expected) {
                    out += EcuMessage(ecu, b.take(expected).toByteArray())
                    buffer = null
                }
            }
        }
        return out
    }

    private fun parseLegacy(lines: List<String>): List<EcuMessage> = lines.mapNotNull { line ->
        if (line.length % 2 != 0) return@mapNotNull null
        val bytes = Hex.decode(line)
        if (bytes.size < 5) return@mapNotNull null // 3 header + almeno 1 dato + checksum
        EcuMessage(Hex.byte(bytes.u(2)), bytes.copyOfRange(3, bytes.size - 1))
    }
}
