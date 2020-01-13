package com.procurement.felicia.infrastructure.web.controllers

import com.procurement.felicia.application.AuthorizedUser
import com.procurement.felicia.application.NonAuthorizedUser
import com.procurement.felicia.application.User
import io.ktor.http.Headers
import java.util.*

const val HEADER_LOGIN = "login"
const val HEADER_PASSWORD = "password"
const val HEADER_GROUP_ID = "group_id"

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