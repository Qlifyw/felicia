package com.procurement.felicia.infrastructure.web.controllers

import com.procurement.felicia.application.AuthorizedUser
import com.procurement.felicia.application.NonAuthorizedUser
import com.procurement.felicia.application.User
import io.ktor.http.Headers
import java.util.*

const val HEADER_LOGIN = "login"
const val HEADER_PASSWORD = "password"

fun getUser(headers: Headers): User {
    val login = headers[HEADER_LOGIN]
    val password = headers[HEADER_PASSWORD]
    if (login == null || password == null)
        return NonAuthorizedUser()
    else
        return AuthorizedUser(login, password)
}

data class IdentSession(val uid: UUID)