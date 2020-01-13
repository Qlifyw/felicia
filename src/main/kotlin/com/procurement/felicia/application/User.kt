package com.procurement.felicia.application

import java.util.*

sealed class User {
    abstract val uid: UUID
    abstract val groupId: String
}

class AuthorizedUser private constructor(
    val login: String,
    val password: String,
    override val uid: UUID,
    override val groupId: String
) : User() {
    companion object {
        operator fun invoke(
            login: String,
            password: String,
            groupId: String
        ) = AuthorizedUser(
            login,
            password,
            UUID.randomUUID(),
            groupId
        )
    }
}

class NonAuthorizedUser private constructor(
    override val uid: UUID,
    override val groupId: String
) : User() {
    companion object {
        operator fun invoke(
            groupId: String
        ): NonAuthorizedUser {
            return NonAuthorizedUser(
                UUID.randomUUID(),
                groupId
            )
        }
    }
}
