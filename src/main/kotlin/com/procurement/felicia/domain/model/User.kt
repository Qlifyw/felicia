package com.procurement.felicia.domain.model

import java.util.*

sealed class User {
    abstract val groupId: String
    val uid: UUID = UUID.randomUUID()
}

class AuthorizedUser private constructor(
    val login: String,
    val password: String,
    override val groupId: String
) : User() {
    companion object {
        operator fun invoke(login: String, password: String, groupId: String) =
            AuthorizedUser(login = login, password = password, groupId = groupId)
    }
}

class NonAuthorizedUser private constructor(
    override val groupId: String
) : User() {
    companion object {
        operator fun invoke(groupId: String) = NonAuthorizedUser(groupId)
    }
}
