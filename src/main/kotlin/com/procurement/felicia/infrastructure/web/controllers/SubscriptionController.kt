package com.procurement.felicia.infrastructure.web.controllers

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.procurement.felicia.application.Client
import com.procurement.felicia.application.consume
import com.procurement.felicia.application.startListen
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.content.TextContent
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.sessions.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.io.errors.IOException
import java.util.*

private val objectMapper = jacksonObjectMapper()
private val clients = mutableMapOf<UUID, Client>()

fun main() {
    embeddedServer(Netty, 8080) {

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
                // TODO https://github.com/michel-kraemer/actson
                val payloadNode = objectMapper.readTree(call.receiveText())

                // TODO return 4xx if error

                val kafkaHost = call.request.headers["host"] ?: return@post call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Could not find paramenter 'host' in headers to connect"
                )

                val user = getUser(call.request.headers)

                val topicsNode = payloadNode.get("topics") as ArrayNode
                val topics = topicsNode.map { it.asText() }.toSet()

                println("BBBBB ${Thread.currentThread().name}")

                startListen(topics = topics, user = user, clients = clients, host = kafkaHost)
                println("subscribe")

                println("cookies ${user.uid}")
                call.sessions.set(IdentSession(user.uid))

                call.respond(message = "", status = HttpStatusCode.OK)
            }

            post("/kafka/find") {

                val content = call.request.headers["pattern"] ?: return@post call.respond(
                        status = HttpStatusCode.BadRequest,
                        message = "Could not find paramenter 'pattern' in headers to connect"
                )
                val topic = call.request.headers["topic"] ?: return@post call.respond(
                        status = HttpStatusCode.BadRequest,
                        message = "Could not find paramenter 'topic' in headers to connect"
                )

                val session: IdentSession = call.sessions.get<IdentSession>()!!
                println("session: ${session}")

                val client = clients[session.uid] ?: return@post call.respond(
                    message = "Client not found",
                    status = HttpStatusCode.BadRequest
                )
                if (!client.topics.contains(topic)) return@post call.respond(
                    message = "Cannot find topic '${topic}' in client's subscriptions.",
                    status = HttpStatusCode.BadRequest
                )

                val deffer = consume(client.source, content, topic, CompletableDeferred())
                val message = deffer.await()
                call.respond(
                    TextContent(
                        text = message,
                        status = HttpStatusCode.OK,
                        contentType = ContentType.Application.Json
                    )
                )
            }
        }
    }.start(wait = true)
}

