package com.procurement.felicia.application

import java.util.*

sealed class User {
    abstract val uid: UUID
}

class AuthorizedUser private constructor(
    val login: String,
    val password: String,
    override val uid: UUID
) : User() {
    companion object {
        operator fun invoke(
            login: String,
            password: String
        ) = AuthorizedUser(
            login,
            password,
            UUID.randomUUID()
        )
    }
}

class NonAuthorizedUser private constructor(override val uid: UUID) : User() {
    companion object {
        operator fun invoke(): NonAuthorizedUser {
            return NonAuthorizedUser(UUID.randomUUID())
        }
    }
}
