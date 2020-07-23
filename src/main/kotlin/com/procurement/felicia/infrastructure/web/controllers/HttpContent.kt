package com.procurement.felicia.infrastructure.web.controllers

import com.procurement.felicia.application.api.Response
import com.procurement.felicia.domain.model.AuthorizedUser
import com.procurement.felicia.domain.model.NonAuthorizedUser
import com.procurement.felicia.domain.model.User
import com.procurement.felicia.infrastructure.web.http.HEADER_GROUP_ID
import com.procurement.felicia.infrastructure.web.http.HEADER_LOGIN
import com.procurement.felicia.infrastructure.web.http.HEADER_PASSWORD
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import java.util.*

fun getUser(headers: Headers): User {
    val login = headers[HEADER_LOGIN]
    val password = headers[HEADER_PASSWORD]
    val groupId = headers[HEADER_GROUP_ID] ?: UUID.randomUUID().toString()

    if (login == null || password == null)
        return NonAuthorizedUser(groupId)
    else
        return AuthorizedUser(login, password, groupId)
}

data class IdentSession(val uid: UUID)

fun parameterNotFound(parameter: String) =
    Response(
        status = HttpStatusCode.BadRequest,
        message = "Could not find paramenter '${parameter}' in headers to connect"
    )