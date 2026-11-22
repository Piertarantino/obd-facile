package com.obdfacile.obd.util

object Hex {
    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Stringa esadecimale di lunghezza dispari: $hex" }
        return ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }

    fun byte(b: Int): String = "%02X".format(b and 0xFF)

    fun encode(bytes: ByteArray): String = bytes.joinToString(" ") { byte(it.toInt()) }
}

/** Byte senza segno all'indice [i]. */
fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF
