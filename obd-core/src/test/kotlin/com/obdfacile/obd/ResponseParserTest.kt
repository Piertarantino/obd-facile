package com.obdfacile.obd

import com.obdfacile.obd.dtc.DtcDecoder
import com.obdfacile.obd.elm.ElmText
import com.obdfacile.obd.elm.ObdProtocol
import com.obdfacile.obd.elm.ResponseParser
import com.obdfacile.obd.pid.MonitorStatus
import com.obdfacile.obd.pid.Pids
import com.obdfacile.obd.util.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {

    private fun hex(s: String) = Hex.decode(s.replace(" ", "")).toList()

    @Test
    fun `CAN 11 bit single frame`() {
        val msgs = ResponseParser.parse(listOf("7E8064100BE3EB813"), ObdProtocol.CAN_11_500)
        assertEquals(1, msgs.size)
        assertEquals("7E8", msgs[0].ecu)
        assertEquals(hex("41 00 BE 3E B8 13"), msgs[0].data.toList())
    }

    @Test
    fun `CAN multiple ECUs`() {
        val msgs = ResponseParser.parse(
            listOf("7E8064100BE3EB813", "7E906410080180001"),
            ObdProtocol.CAN_11_500,
        )
        assertEquals(listOf("7E8", "7E9"), msgs.map { it.ecu })
    }

    @Test
    fun `CAN multi frame VIN is reassembled`() {
        val lines = listOf("7E81014490201314731", "7E8214A433534343452", "7E82237323532333637")
        val msgs = ResponseParser.parse(lines, ObdProtocol.CAN_11_500)
        assertEquals(1, msgs.size)
        assertEquals(20, msgs[0].data.size)
        assertEquals("1G1JC5444R7252367", VinDecoder.decode(msgs))
    }

    @Test
    fun `CAN 29 bit header`() {
        val msgs = ResponseParser.parse(listOf("18DAF11003410D32"), ObdProtocol.CAN_29_500)
        assertEquals("18DAF110", msgs[0].ecu)
        assertEquals(hex("41 0D 32"), msgs[0].data.toList())
    }

    @Test
    fun `legacy KWP line drops header and checksum`() {
        val msgs = ResponseParser.parse(listOf("486B10410C1AF8A4"), ObdProtocol.KWP_FAST)
        assertEquals("10", msgs[0].ecu)
        assertEquals(hex("41 0C 1A F8"), msgs[0].data.toList())
    }

    @Test
    fun `noise lines are removed and errors detected`() {
        val lines = ElmText.splitLines("0100\rSEARCHING...\rUNABLE TO CONNECT\r\r", echo = "0100")
        assertEquals(listOf("UNABLE TO CONNECT"), lines)
        assertEquals("UNABLE TO CONNECT", ElmText.findError(lines))
        assertTrue(ResponseParser.parse(lines, ObdProtocol.CAN_11_500).isEmpty())
    }

    @Test
    fun `DTC decoding CAN`() {
        val msg = ResponseParser.parse(listOf("7E806430201330420"), ObdProtocol.CAN_11_500).single()
        assertEquals(listOf("P0133", "P0420"), DtcDecoder.decode(msg, isCan = true))
    }

    @Test
    fun `DTC pair systems`() {
        assertEquals("C0123", DtcDecoder.decodePair(0x41, 0x23))
        assertEquals("B1234", DtcDecoder.decodePair(0x92, 0x34))
        assertEquals("U0100", DtcDecoder.decodePair(0xC1, 0x00))
        assertNull(DtcDecoder.decodePair(0, 0))
    }

    @Test
    fun `PID formulas`() {
        assertEquals(1726.0, Pids[0x0C]!!.decode(Hex.decode("1AF8")), 0.001)
        assertEquals(83.0, Pids[0x05]!!.decode(Hex.decode("7B")), 0.001)
        assertEquals(12.5, Pids[0x42]!!.decode(Hex.decode("30D4")), 0.001)
    }

    @Test
    fun `monitor status`() {
        // MIL accesa, 2 errori, benzina, catalizzatore supportato e non completato
        val s = MonitorStatus.decode(Hex.decode("82070101"))
        assertTrue(s.milOn)
        assertEquals(2, s.dtcCount)
        assertFalse(s.compressionIgnition)
        assertFalse(s.monitors.first { it.name == "Catalizzatore" }.complete)
        assertTrue(s.monitors.first { it.name == "Mancate accensioni" }.complete)
    }
}
