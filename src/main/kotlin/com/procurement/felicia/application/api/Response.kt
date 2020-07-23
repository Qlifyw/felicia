package com.procurement.felicia.application.api

import io.ktor.http.HttpStatusCode

data class Response(
    val status: HttpStatusCode,
    val message: String
)