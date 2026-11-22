package com.obdfacile.obd.pid

import com.obdfacile.obd.util.u

/**
 * Definizione di un PID del modo 01 (dati in tempo reale), formule SAE J1979.
 * [decode] riceve i soli byte dati (A, B, ...), senza 41 e numero PID.
 * [fast] = PID che cambiano rapidamente e vanno letti a ogni giro di polling;
 * gli altri (temperature, livelli) vengono letti più di rado per non rallentare.
 */
class PidDefinition(
    val pid: Int,
    val name: String,
    val unit: String,
    val bytes: Int,
    val min: Double,
    val max: Double,
    val decimals: Int = 0,
    val fast: Boolean = false,
    val decode: (ByteArray) -> Double,
)

data class PidReading(val definition: PidDefinition, val value: Double, val ecu: String) {
    val formatted: String get() = "%.${definition.decimals}f".format(value)
}

object Pids {
    private fun a(d: ByteArray) = d.u(0).toDouble()
    private fun ab(d: ByteArray) = (d.u(0) * 256 + d.u(1)).toDouble()

    val all: List<PidDefinition> = listOf(
        PidDefinition(0x0C, "Giri motore", "rpm", 2, 0.0, 8000.0, fast = true) { ab(it) / 4 },
        PidDefinition(0x0D, "Velocità", "km/h", 1, 0.0, 255.0, fast = true) { a(it) },
        PidDefinition(0x04, "Carico motore", "%", 1, 0.0, 100.0, fast = true) { a(it) * 100 / 255 },
        PidDefinition(0x11, "Acceleratore (farfalla)", "%", 1, 0.0, 100.0, fast = true) { a(it) * 100 / 255 },
        PidDefinition(0x0B, "Pressione collettore", "kPa", 1, 0.0, 255.0, fast = true) { a(it) },
        PidDefinition(0x10, "Flusso aria (MAF)", "g/s", 2, 0.0, 300.0, 1, fast = true) { ab(it) / 100 },
        PidDefinition(0x5E, "Consumo carburante", "L/h", 2, 0.0, 50.0, 1, fast = true) { ab(it) / 20 },
        PidDefinition(0x0E, "Anticipo accensione", "°", 1, -64.0, 64.0, 1, fast = true) { a(it) / 2 - 64 },
        PidDefinition(0x05, "Temp. liquido raffreddamento", "°C", 1, -40.0, 130.0) { a(it) - 40 },
        PidDefinition(0x5C, "Temp. olio motore", "°C", 1, -40.0, 150.0) { a(it) - 40 },
        PidDefinition(0x0F, "Temp. aria aspirata", "°C", 1, -40.0, 100.0) { a(it) - 40 },
        PidDefinition(0x46, "Temp. esterna", "°C", 1, -40.0, 60.0) { a(it) - 40 },
        PidDefinition(0x42, "Tensione centralina", "V", 2, 0.0, 16.0, 2) { ab(it) / 1000 },
        PidDefinition(0x2F, "Livello carburante", "%", 1, 0.0, 100.0) { a(it) * 100 / 255 },
        PidDefinition(0x33, "Pressione atmosferica", "kPa", 1, 0.0, 120.0) { a(it) },
        PidDefinition(0x06, "Correzione breve banco 1", "%", 1, -100.0, 100.0, 1) { (a(it) - 128) * 100 / 128 },
        PidDefinition(0x07, "Correzione lunga banco 1", "%", 1, -100.0, 100.0, 1) { (a(it) - 128) * 100 / 128 },
        PidDefinition(0x1F, "Tempo dall'avvio", "s", 2, 0.0, 65535.0) { ab(it) },
        PidDefinition(0x21, "Km percorsi con spia accesa", "km", 2, 0.0, 65535.0) { ab(it) },
        PidDefinition(0x31, "Km dalla cancellazione errori", "km", 2, 0.0, 65535.0) { ab(it) },
    )

    private val byPid = all.associateBy { it.pid }

    operator fun get(pid: Int): PidDefinition? = byPid[pid]
}
