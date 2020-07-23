package com.procurement.felicia.application.network

import com.procurement.felicia.application.network.model.Socket
import com.procurement.felicia.domain.errors.DataFormatMismatch
import com.procurement.felicia.domain.errors.DataMismatchToPattern
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SocketTest {

    companion object {
        private const val PORT       = 37772
        private const val IP_ADDRESS = "192.168.1.1"
        private const val HOSTNAME   = "some.domain.org"
    }

    @ParameterizedTest(name = "Valid data: {0}:{1}")
    @CsvSource(
        "$IP_ADDRESS,$PORT",
        "$HOSTNAME,$PORT"
    )
    fun success_ip_port(address: String, port: Int) {
        val socket = Socket.tryCreate("$address:$port").get

        assertEquals(socket.address, address)
        assertEquals(socket.port, port)
    }

    @ParameterizedTest(name = "Valid port: {0}:{1}")
    @CsvSource(
        "$IP_ADDRESS, 143.32",
        "$IP_ADDRESS, string",
        "$HOSTNAME,   &&$$##",
        "$HOSTNAME,   -2342"
    )
    fun invalid_port(address: String, port: String) {
        val result = Socket.tryCreate("$address:$port")

        assertFalse(result.isSuccess)
        assertTrue(result.error is DataFormatMismatch)
    }

    @ParameterizedTest(name = "Invalid data: {0}")
    @ValueSource(
        strings = [
            "SOME_UNKNOWN_STRING",
            "TOO:MANY:DELIMITERS",
            "INVALID,DELIMITER"
        ]
    )
    fun invalid_host(host: String) {
        val result = Socket.tryCreate(host)

        assertFalse(result.isSuccess)
        assertTrue(result.error is DataMismatchToPattern)
    }
}