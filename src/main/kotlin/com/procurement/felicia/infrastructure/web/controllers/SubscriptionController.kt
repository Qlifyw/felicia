package com.procurement.felicia.infrastructure.web.controllers

import com.procurement.felicia.domain.model.Client
import com.procurement.felicia.infrastructure.web.find
import com.procurement.felicia.infrastructure.web.subscribe
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.content.TextContent
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.utils.io.errors.IOException
import java.util.*

private val clients = mutableMapOf<UUID, Client>()

fun Application.kafka() {
    install(Sessions) {
        cookie<IdentSession>("Identification")
    }

    routing {
        install(StatusPages) {
            exception<IOException> { cause ->
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        post("/kafka/subscribe") {
            val response = subscribe(call, clients)
            return@post call.respond(response.status, response.message)
        }

        post("/kafka/find") {
            val response = find(call, clients)
            return@post call.respond(TextContent(status = response.status, text = response.message,contentType = ContentType.Application.Json))
        }
    }
}
