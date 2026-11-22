package com.obdfacile.obd.elm

/** Pulizia del testo restituito dall'ELM327. */
object ElmText {

    /** Errori che l'ELM327 (e i cloni) possono stampare al posto dei dati. */
    private val ERROR_TOKENS = listOf(
        "NO DATA",
        "UNABLE TO CONNECT",
        "CAN ERROR",
        "BUS ERROR",
        "BUS BUSY",
        "BUFFER FULL",
        "DATA ERROR",
        "FB ERROR",
        "RX ERROR",
        "LV RESET",
        "STOPPED",
        "ERR",
    )

    /**
     * Divide la risposta in righe, togliendo righe vuote, l'eco del comando
     * (se ATE0 non è ancora attivo) e i messaggi informativi (SEARCHING..., BUS INIT: ...OK).
     */
    fun splitLines(raw: String, echo: String? = null): List<String> {
        val echoNorm = echo?.replace(" ", "")?.uppercase()
        return raw.split('\r', '\n')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .filterNot { echoNorm != null && it.replace(" ", "") == echoNorm }
            .filterNot { it.startsWith("SEARCHING") }
            .filterNot { it.startsWith("BUS INIT") && !it.contains("ERROR") }
    }

    /** Primo messaggio d'errore presente nelle righe, o null. */
    fun findError(lines: List<String>): String? = lines.firstOrNull { line ->
        line == "?" || ERROR_TOKENS.any { line.contains(it) }
    }
}
