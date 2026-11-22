package com.obdfacile.obd

import com.obdfacile.obd.dtc.Dtc
import com.obdfacile.obd.dtc.DtcDecoder
import com.obdfacile.obd.dtc.DtcKind
import com.obdfacile.obd.dtc.DtcReport
import com.obdfacile.obd.elm.EcuMessage
import com.obdfacile.obd.elm.ElmDevice
import com.obdfacile.obd.elm.ElmText
import com.obdfacile.obd.elm.ObdProtocol
import com.obdfacile.obd.elm.ResponseParser
import com.obdfacile.obd.pid.MonitorStatus
import com.obdfacile.obd.pid.PidDefinition
import com.obdfacile.obd.pid.PidReading
import com.obdfacile.obd.util.u

data class AdapterInfo(
    val elmVersion: String,
    val protocol: ObdProtocol,
    val ecus: List<String>,
    val batteryVoltage: String?,
    val transport: String,
)

data class ClearResult(val acknowledged: Boolean, val remainingDtcCount: Int?)

/**
 * API ad alto livello: "dammi i giri motore", "leggi gli errori", "cancella gli errori".
 * Tutta la conoscenza del protocollo OBD-II (SAE J1979) sta qui e nei decoder.
 */
class ObdClient(private val elm: ElmDevice) {

    var info: AdapterInfo? = null
        private set

    private var protocol = ObdProtocol.AUTO

    /**
     * Ottimizzazione: se risponde una sola centralina, aggiungendo "1" in coda alle
     * richieste (es. "010C1") l'ELM327 restituisce il dato appena arriva invece di
     * attendere il timeout per altre risposte. Polling fino a ~5 volte più veloce.
     * Si disattiva da sola se l'adattatore non la supporta (cloni v1.0-1.2).
     */
    private var singleResponseHint = false

    suspend fun connect(onStep: (String) -> Unit = {}): AdapterInfo {
        onStep("Apertura connessione con l'adattatore…")
        elm.open()

        onStep("Reset adattatore…")
        try {
            elm.command("ATZ", RESET_TIMEOUT_MS)
        } catch (_: ElmTimeoutException) {
            elm.command("ATZ", RESET_TIMEOUT_MS) // alcuni cloni ignorano il primo comando
        }

        onStep("Configurazione adattatore…")
        elm.command("ATE0") // niente eco: meno byte da trasferire
        elm.command("ATL0") // niente line feed
        elm.command("ATS0") // niente spazi: risposte ~30% più corte
        elm.command("ATH1") // header attivi: serve per distinguere le centraline
        elm.command("ATSP0") // protocollo automatico
        runCatching { elm.command("ATAT1") } // timing adattivo (non tutti i cloni)
        val version = elm.command("ATI").firstOrNull() ?: "ELM327"

        onStep("Ricerca del protocollo dell'auto (fino a 15 secondi)…")
        val search = elm.command("0100", SEARCH_TIMEOUT_MS)
        protocol = ObdProtocol.fromDpn(elm.command("ATDPN").firstOrNull().orEmpty())
        val ecus = ResponseParser.parse(search, protocol)
            .filter { it.data.size >= 2 && it.data.u(0) == 0x41 }
            .map { it.ecu }
            .distinct()
        if (ecus.isEmpty()) {
            throw NoVehicleResponseException(ElmText.findError(search) ?: "nessuna risposta")
        }
        singleResponseHint = ecus.size == 1

        val voltage = runCatching { elm.command("ATRV").firstOrNull() }.getOrNull()
        return AdapterInfo(version, protocol, ecus, voltage, elm.transportDescription).also { info = it }
    }

    suspend fun disconnect() {
        runCatching { elm.close() }
        info = null
    }

    /**
     * Invia una richiesta OBD e restituisce le risposte positive.
     * "NO DATA" -> lista vuota. Altri errori dell'adattatore -> eccezione.
     */
    suspend fun query(
        mode: Int,
        pid: Int? = null,
        timeoutMs: Long = ElmDevice.DEFAULT_TIMEOUT_MS,
        singleFrame: Boolean = false,
    ): List<EcuMessage> {
        val base = buildString {
            append("%02X".format(mode))
            if (pid != null) append("%02X".format(pid))
        }
        val useHint = singleFrame && singleResponseHint
        val lines = elm.command(if (useHint) base + "1" else base, timeoutMs)
        if (useHint && lines.any { it == "?" }) {
            singleResponseHint = false
            return query(mode, pid, timeoutMs, singleFrame = false)
        }

        val messages = ResponseParser.parse(lines, protocol)
        if (messages.isEmpty()) {
            val err = ElmText.findError(lines)
            if (err != null && err != "NO DATA") throw ElmErrorException(base, err)
            return emptyList()
        }
        val positive = messages.filter {
            it.data.isNotEmpty() && it.data.u(0) == mode + 0x40 &&
                (pid == null || (it.data.size > 1 && it.data.u(1) == pid))
        }
        if (positive.isEmpty()) {
            messages.firstOrNull { it.data.size >= 3 && it.data.u(0) == 0x7F }
                ?.let { throw NegativeResponseException(mode, it.data.u(2)) }
        }
        return positive
    }

    /** PID del modo 01 supportati (unione su tutte le centraline). */
    suspend fun supportedPids(): Set<Int> {
        val result = sortedSetOf<Int>()
        var base = 0x00
        while (base <= 0xE0) {
            var hasNextBlock = false
            for (m in query(0x01, base)) {
                if (m.data.size < 6) continue
                for (i in 0 until 32) {
                    if (m.data.u(2 + i / 8) and (0x80 shr (i % 8)) != 0) {
                        val pid = base + i + 1
                        result += pid
                        if (pid == base + 0x20) hasNextBlock = true
                    }
                }
            }
            if (!hasNextBlock) break
            base += 0x20
        }
        return result
    }

    suspend fun readPid(def: PidDefinition): PidReading? {
        val msg = query(0x01, def.pid, singleFrame = true)
            .firstOrNull { it.data.size >= 2 + def.bytes } ?: return null
        val value = def.decode(msg.data.copyOfRange(2, 2 + def.bytes))
        return PidReading(def, value, msg.ecu)
    }

    /** Spia motore, numero errori, readiness. Se rispondono più centraline si aggrega. */
    suspend fun readStatus(): MonitorStatus? {
        val statuses = query(0x01, 0x01)
            .filter { it.data.size >= 6 }
            .map { MonitorStatus.decode(it.data.copyOfRange(2, 6)) }
        if (statuses.isEmpty()) return null
        val main = statuses.maxBy { it.monitors.size }
        return main.copy(
            milOn = statuses.any { it.milOn },
            dtcCount = statuses.sumOf { it.dtcCount },
        )
    }

    suspend fun readDtcs(kind: DtcKind): List<Dtc> =
        query(kind.mode, timeoutMs = DTC_TIMEOUT_MS)
            .flatMap { m -> DtcDecoder.decode(m, protocol.isCan).map { Dtc(it, kind, m.ecu) } }
            .distinctBy { it.code to it.ecu }

    suspend fun readAllDtcs(): DtcReport = DtcReport(
        stored = readDtcs(DtcKind.STORED),
        pending = readDtcs(DtcKind.PENDING),
        // il modo 0A esiste solo dalle auto ~2010 in poi: se non c'è, lista vuota
        permanent = try {
            readDtcs(DtcKind.PERMANENT)
        } catch (_: NegativeResponseException) {
            emptyList()
        },
    )

    /**
     * Modo 04: cancella errori memorizzati e in attesa, dati freeze frame e
     * azzera i test di readiness. Va fatto a motore SPENTO e quadro ACCESO.
     */
    suspend fun clearDtcs(): ClearResult {
        val ack = query(0x04, timeoutMs = CLEAR_TIMEOUT_MS).isNotEmpty()
        val status = runCatching { readStatus() }.getOrNull()
        return ClearResult(ack, status?.dtcCount)
    }

    suspend fun readVin(): String? = VinDecoder.decode(query(0x09, 0x02, timeoutMs = VIN_TIMEOUT_MS))

    companion object {
        const val RESET_TIMEOUT_MS = 5_000L
        const val SEARCH_TIMEOUT_MS = 15_000L
        const val DTC_TIMEOUT_MS = 4_000L
        const val CLEAR_TIMEOUT_MS = 6_000L
        const val VIN_TIMEOUT_MS = 5_000L
    }
}
