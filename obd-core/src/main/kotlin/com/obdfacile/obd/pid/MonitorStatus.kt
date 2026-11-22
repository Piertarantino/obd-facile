package com.obdfacile.obd.pid

import com.obdfacile.obd.util.u

data class ReadinessMonitor(val name: String, val complete: Boolean)

/**
 * PID 01 01: spia motore (MIL), numero di errori e stato dei test di prontezza
 * (readiness), utili prima della revisione: dopo una cancellazione errori i test
 * tornano "non pronti" finché non si guida per un po'.
 */
data class MonitorStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    val compressionIgnition: Boolean,
    val monitors: List<ReadinessMonitor>,
) {
    val allReady: Boolean get() = monitors.all { it.complete }

    companion object {
        private val COMMON = listOf(0 to "Mancate accensioni", 1 to "Sistema carburante", 2 to "Componenti")
        private val SPARK = listOf(
            0 to "Catalizzatore", 1 to "Catalizzatore riscaldato", 2 to "Sistema evaporativo",
            3 to "Aria secondaria", 5 to "Sensore ossigeno", 6 to "Riscaldatore sensore ossigeno",
            7 to "EGR / VVT",
        )
        private val COMPRESSION = listOf(
            0 to "Catalizzatore NMHC", 1 to "Riduzione NOx / SCR", 3 to "Pressione sovralimentazione",
            5 to "Sensore gas di scarico", 6 to "Filtro antiparticolato (DPF)", 7 to "EGR / VVT",
        )

        /** [d] = byte A, B, C, D della risposta 41 01. */
        fun decode(d: ByteArray): MonitorStatus {
            val a = d.u(0); val b = d.u(1); val c = d.u(2); val dd = d.u(3)
            val diesel = b and 0x08 != 0
            val monitors = buildList {
                for ((bit, name) in COMMON) {
                    if (b and (1 shl bit) != 0) add(ReadinessMonitor(name, b and (1 shl (bit + 4)) == 0))
                }
                for ((bit, name) in if (diesel) COMPRESSION else SPARK) {
                    if (c and (1 shl bit) != 0) add(ReadinessMonitor(name, dd and (1 shl bit) == 0))
                }
            }
            return MonitorStatus(a and 0x80 != 0, a and 0x7F, diesel, monitors)
        }
    }
}
