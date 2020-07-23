package com.procurement.felicia.application.network.model

import com.procurement.felicia.domain.DataValidationError
import com.procurement.felicia.domain.Result
import com.procurement.felicia.domain.Result.Companion.failure
import com.procurement.felicia.domain.Result.Companion.success
import com.procurement.felicia.domain.errors.DataFormatMismatch
import com.procurement.felicia.domain.errors.DataMismatchToPattern
import com.procurement.felicia.domain.splitBy
import com.procurement.felicia.infrastructure.web.http.DELIMITER_COLON
import com.procurement.felicia.infrastructure.web.http.HEADER_HOSTS

const val SOCKET_FORMAT = ".*:[0-9]{1,4}"

class Socket private constructor(val address: String, val port: Int) {
    companion object {
        fun tryCreate(host: String): Result<Socket, DataValidationError> {
            val splitted: Set<String> = host.splitBy(DELIMITER_COLON)
            if (splitted.size != 2)
                return failure(DataMismatchToPattern(name = HEADER_HOSTS, pattern = SOCKET_FORMAT, actualValue = host))

            val address     : String = splitted.elementAt(0)
            val unparsedPort: String = splitted.elementAt(1)

            val port = unparsedPort.tryParsePositiveInt()
                .orForwardFail { error -> return error }

            return success(Socket(address, port))
        }

        private fun String.tryParsePositiveInt(): Result<Int, DataFormatMismatch> = try {
            val parsedInt = this.toInt()
            if (parsedInt > 0)
                success(this.toInt())
            else
                failure(DataFormatMismatch(name = HEADER_HOSTS, expectedFormat = "Positive Integer", actualValue = this))
        } catch (exception: NumberFormatException) {
            failure(DataFormatMismatch(name = HEADER_HOSTS, expectedFormat = "Positive Integer", actualValue = this))
        }
    }

    override fun toString(): String = "$address:$port"
}


