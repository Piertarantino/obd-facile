package com.obdfacile.obd.dtc

/**
 * Descrizioni in italiano dei codici errore.
 *
 * Per ora: una selezione dei codici generici più frequenti + una descrizione per
 * categoria per tutti gli altri. Da sostituire con un database completo (SAE J2012)
 * caricato da file, scritto da noi o da fonti con licenza compatibile.
 */
object DtcDescriptions {

    private val KNOWN: Map<String, String> = buildMap {
        put("P0011", "Fasatura albero a camme A troppo anticipata / prestazioni (banco 1)")
        put("P0016", "Correlazione albero motore / albero a camme (banco 1, sensore A)")
        put("P0087", "Pressione carburante (rail) troppo bassa")
        put("P0088", "Pressione carburante (rail) troppo alta")
        put("P0100", "Sensore flusso aria (debimetro/MAF): malfunzionamento circuito")
        put("P0101", "Debimetro (MAF): valori fuori range / prestazioni")
        put("P0102", "Debimetro (MAF): segnale basso")
        put("P0103", "Debimetro (MAF): segnale alto")
        put("P0110", "Sensore temperatura aria aspirata: malfunzionamento circuito")
        put("P0113", "Sensore temperatura aria aspirata: segnale alto")
        put("P0115", "Sensore temperatura liquido di raffreddamento: malfunzionamento circuito")
        put("P0117", "Sensore temperatura liquido di raffreddamento: segnale basso")
        put("P0118", "Sensore temperatura liquido di raffreddamento: segnale alto")
        put("P0120", "Sensore posizione farfalla/pedale A: malfunzionamento circuito")
        put("P0128", "Temperatura liquido sotto la soglia del termostato (termostato aperto?)")
        put("P0130", "Sonda lambda (banco 1, sensore 1): malfunzionamento circuito")
        put("P0133", "Sonda lambda (banco 1, sensore 1): risposta lenta")
        put("P0135", "Riscaldatore sonda lambda (banco 1, sensore 1): malfunzionamento")
        put("P0141", "Riscaldatore sonda lambda (banco 1, sensore 2): malfunzionamento")
        put("P0171", "Miscela troppo magra (banco 1)")
        put("P0172", "Miscela troppo grassa (banco 1)")
        put("P0174", "Miscela troppo magra (banco 2)")
        put("P0175", "Miscela troppo grassa (banco 2)")
        put("P0217", "Surriscaldamento motore")
        put("P0234", "Sovralimentazione eccessiva (overboost)")
        put("P0299", "Sovralimentazione insufficiente (underboost)")
        put("P0300", "Mancate accensioni casuali / su più cilindri")
        for (n in 1..8) put("P030$n", "Mancata accensione cilindro $n")
        put("P0325", "Sensore di detonazione (battito): malfunzionamento circuito")
        put("P0335", "Sensore posizione albero motore (giri/PMS): malfunzionamento circuito")
        put("P0340", "Sensore posizione albero a camme (fase): malfunzionamento circuito")
        put("P0380", "Candelette di preriscaldamento: malfunzionamento circuito")
        put("P0400", "Ricircolo gas di scarico (EGR): malfunzionamento flusso")
        put("P0401", "EGR: flusso insufficiente")
        put("P0402", "EGR: flusso eccessivo")
        put("P0420", "Efficienza catalizzatore sotto la soglia (banco 1)")
        put("P0430", "Efficienza catalizzatore sotto la soglia (banco 2)")
        put("P0440", "Sistema di controllo evaporazione (EVAP): malfunzionamento")
        put("P0442", "EVAP: piccola perdita rilevata")
        put("P0455", "EVAP: grande perdita rilevata (tappo carburante?)")
        put("P0480", "Ventola di raffreddamento 1: malfunzionamento circuito")
        put("P0500", "Sensore velocità veicolo: malfunzionamento")
        put("P0505", "Sistema controllo minimo: malfunzionamento")
        put("P0562", "Tensione di sistema bassa (batteria/alternatore)")
        put("P0563", "Tensione di sistema alta")
        put("P0606", "Centralina motore: errore del processore")
        put("P0700", "Sistema controllo cambio: guasto (leggere la centralina del cambio)")
        put("P2002", "Efficienza filtro antiparticolato (DPF) sotto la soglia (banco 1)")
        put("P2463", "Filtro antiparticolato (DPF): accumulo eccessivo di fuliggine")
        put("U0073", "Bus di comunicazione A spento")
        put("U0100", "Comunicazione persa con la centralina motore")
    }

    fun describe(code: String): String = KNOWN[code] ?: category(code)

    fun isManufacturerSpecific(code: String): Boolean =
        code.length == 5 && (code[1] == '1' || (code[1] == '3' && code[0] == 'P'))

    private fun category(code: String): String {
        if (code.length != 5) return "Codice non riconosciuto"
        val system = when (code[0]) {
            'P' -> "Motore/cambio"
            'C' -> "Telaio"
            'B' -> "Carrozzeria"
            'U' -> "Rete di comunicazione"
            else -> "Sistema sconosciuto"
        }
        val area = if (code[0] == 'P') when (code[2]) {
            '0', '1', '2' -> "dosaggio aria/carburante"
            '3' -> "accensione o mancate accensioni"
            '4' -> "controllo emissioni"
            '5' -> "velocità, minimo e ingressi"
            '6' -> "centralina e uscite"
            '7', '8', '9' -> "trasmissione"
            'A' -> "propulsione ibrida"
            else -> null
        } else null
        val origin = if (isManufacturerSpecific(code)) "codice specifico del costruttore" else "codice generico"
        return listOfNotNull(system, area).joinToString(": ") + " ($origin)"
    }
}
