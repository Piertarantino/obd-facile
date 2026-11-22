package com.obdfacile.obd

import com.obdfacile.obd.dtc.DtcKind
import com.obdfacile.obd.elm.ElmDevice
import com.obdfacile.obd.elm.ObdProtocol
import com.obdfacile.obd.pid.Pids
import com.obdfacile.obd.transport.ObdTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finto ELM327: risponde da una tabella comando -> risposta. */
private class FakeElm(private val responses: Map<String, String>) : ObdTransport {
    private val queue = Channel<ByteArray>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()
    override val description = "finto"
    override var isConnected = false
    override suspend fun connect() { isConnected = true }
    override suspend fun write(data: ByteArray) {
        val cmd = String(data, Charsets.US_ASCII).trim()
        if (cmd.isEmpty()) return
        sent += cmd
        queue.trySend("${responses[cmd] ?: "?"}\r\r>".toByteArray(Charsets.US_ASCII))
    }
    override suspend fun read(timeoutMs: Long) = withTimeoutOrNull(timeoutMs) { queue.receive() }
    override fun clearInput() { while (queue.tryReceive().isSuccess) Unit }
    override suspend fun disconnect() { isConnected = false }
}

class ObdClientTest {
    private val car = mapOf(
        "ATZ" to "\r\rELM327 v1.5",
        "ATE0" to "ATE0\rOK",
        "ATL0" to "OK", "ATS0" to "OK", "ATH1" to "OK", "ATSP0" to "OK", "ATAT1" to "OK",
        "ATI" to "ELM327 v1.5",
        "ATDPN" to "A6",
        "ATRV" to "12.6V",
        "0100" to "SEARCHING...\r7E8064100BE3EB813",
        "0120" to "7E806412080000000",
        "010C1" to "7E804410C1AF8",
        "0101" to "7E806410182070101",
        "03" to "7E806430201330420",
        "07" to "7E8024700",
        "0A" to "7E8034A0000",
        "04" to "7E80144",
        "0902" to "7E81014490201314731\r7E8214A433534343452\r7E82237323532333637",
    )

    @Test
    fun `full session`() = runBlocking {
        val fake = FakeElm(car)
        val client = ObdClient(ElmDevice(fake))

        val info = client.connect()
        assertEquals(ObdProtocol.CAN_11_500, info.protocol)
        assertEquals(listOf("7E8"), info.ecus)
        assertEquals("ELM327 v1.5", info.elmVersion)

        val supported = client.supportedPids()
        assertTrue(0x0C in supported)
        assertTrue(0x21 in supported)

        // una sola centralina -> richiesta veloce con suffisso "1"
        val rpm = client.readPid(Pids[0x0C]!!)
        assertEquals(1726.0, rpm!!.value, 0.001)
        assertTrue("010C1" in fake.sent)

        val report = client.readAllDtcs()
        assertEquals(listOf("P0133", "P0420"), report.stored.map { it.code })
        assertTrue(report.pending.isEmpty())
        assertTrue(report.permanent.isEmpty())
        assertEquals(DtcKind.STORED, report.stored[0].kind)

        assertTrue(client.clearDtcs().acknowledged)
        assertEquals("1G1JC5444R7252367", client.readVin())
    }

    @Test(expected = NoVehicleResponseException::class)
    fun `quadro spento`() = runBlocking<Unit> {
        val client = ObdClient(ElmDevice(FakeElm(car + ("0100" to "SEARCHING...\rUNABLE TO CONNECT"))))
        client.connect()
    }
}
