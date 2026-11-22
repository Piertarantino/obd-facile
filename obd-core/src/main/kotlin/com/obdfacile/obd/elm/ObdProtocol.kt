package com.obdfacile.obd.elm

/**
 * Protocolli OBD-II gestiti dall'ELM327 (numero restituito da ATDPN).
 * [canHeaderChars] = caratteri esadecimali dell'header CAN quando ATH1 è attivo.
 */
enum class ObdProtocol(
    val code: Char,
    val label: String,
    val isCan: Boolean,
    val canHeaderChars: Int,
) {
    AUTO('0', "Automatico", false, 0),
    J1850_PWM('1', "SAE J1850 PWM (41.6 kbaud)", false, 0),
    J1850_VPW('2', "SAE J1850 VPW (10.4 kbaud)", false, 0),
    ISO_9141_2('3', "ISO 9141-2 (5 baud init)", false, 0),
    KWP_5BAUD('4', "ISO 14230-4 KWP (5 baud init)", false, 0),
    KWP_FAST('5', "ISO 14230-4 KWP (fast init)", false, 0),
    CAN_11_500('6', "ISO 15765-4 CAN (11 bit, 500 kbaud)", true, 3),
    CAN_29_500('7', "ISO 15765-4 CAN (29 bit, 500 kbaud)", true, 8),
    CAN_11_250('8', "ISO 15765-4 CAN (11 bit, 250 kbaud)", true, 3),
    CAN_29_250('9', "ISO 15765-4 CAN (29 bit, 250 kbaud)", true, 8),
    J1939('A', "SAE J1939 CAN (29 bit, 250 kbaud)", true, 8),
    USER1('B', "USER1 CAN (11 bit)", true, 3),
    USER2('C', "USER2 CAN (11 bit)", true, 3);

    companion object {
        /** Interpreta la risposta di ATDPN: "6", oppure "A6" se scelto in automatico. */
        fun fromDpn(response: String): ObdProtocol {
            val r = response.trim().uppercase()
            val c = (if (r.length >= 2 && r[0] == 'A') r[1] else r.firstOrNull()) ?: return AUTO
            return entries.firstOrNull { it.code == c } ?: AUTO
        }
    }
}
