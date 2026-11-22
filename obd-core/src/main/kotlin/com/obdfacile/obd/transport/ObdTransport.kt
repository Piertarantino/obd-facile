package com.obdfacile.obd.transport

/**
 * Canale di byte grezzo verso l'adattatore (Bluetooth classico, BLE, Wi-Fi, USB...).
 *
 * Il trasporto non sa nulla di ELM327: sposta solo byte. Tutta la logica di protocollo
 * vive in [com.obdfacile.obd.elm.ElmDevice], così ogni nuovo tipo di adattatore richiede
 * solo una piccola classe che implementa questa interfaccia.
 */
interface ObdTransport {
    /** Descrizione leggibile, es. "Bluetooth OBDII (00:1D:A5:...)". */
    val description: String

    val isConnected: Boolean

    /** Apre la connessione. Lancia IOException se non riesce. */
    suspend fun connect()

    suspend fun write(data: ByteArray)

    /**
     * Restituisce il prossimo blocco di byte ricevuti, oppure null se non arriva nulla
     * entro [timeoutMs]. Lancia IOException se la connessione è caduta.
     */
    suspend fun read(timeoutMs: Long): ByteArray?

    /** Scarta i byte ricevuti e non ancora letti (residui di risposte precedenti). */
    fun clearInput()

    suspend fun disconnect()
}
