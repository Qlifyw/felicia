package com.procurement.felicia.infrastructure.web

import com.procurement.felicia.application.api.Response
import com.procurement.felicia.application.consume
import com.procurement.felicia.application.network.model.Socket
import com.procurement.felicia.application.startListen
import com.procurement.felicia.domain.Option.None
import com.procurement.felicia.domain.Option.Some
import com.procurement.felicia.domain.mapToResult
import com.procurement.felicia.domain.model.Client
import com.procurement.felicia.domain.model.defineAction
import com.procurement.felicia.domain.splitBy
import com.procurement.felicia.infrastructure.web.controllers.IdentSession
import com.procurement.felicia.infrastructure.web.controllers.getUser
import com.procurement.felicia.infrastructure.web.controllers.parameterNotFound
import com.procurement.felicia.infrastructure.web.http.CLIENT_NOT_FOUND
import com.procurement.felicia.infrastructure.web.http.DELIMITER_COMMA
import com.procurement.felicia.infrastructure.web.http.HEADER_HOSTS
import com.procurement.felicia.infrastructure.web.http.HEADER_TIMEOUT
import com.procurement.felicia.infrastructure.web.http.HEADER_TOPIC
import com.procurement.felicia.infrastructure.web.http.HEADER_TOPICS
import com.procurement.felicia.infrastructure.web.http.QUERY_PATTERN
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import java.time.Duration
import java.util.*

suspend fun subscribe(call: ApplicationCall, clients: MutableMap<UUID, Client>): Response {
    val kafkaHostJoined = call.request.headers[HEADER_HOSTS]
        ?: return parameterNotFound(HEADER_HOSTS)

    val kafkaHosts = kafkaHostJoined.splitBy(DELIMITER_COMMA)
        .mapToResult { host -> Socket.tryCreate(host) }
        .orReturnFail { error -> return Response(HttpStatusCode.BadRequest, error.description) }

    val topicsNode = call.request.headers[HEADER_TOPICS]
        ?: return parameterNotFound(HEADER_TOPICS)

    val user = getUser(call.request.headers)
    val action = defineAction(clients, user)

    val topics = topicsNode.splitBy(DELIMITER_COMMA)
    val client = startListen(action = action, topics = topics, user = user, hosts = kafkaHosts)
        .orForwardFail { error -> return Response(HttpStatusCode.Conflict, error.error.description) }

    clients[user.uid] = client

    call.sessions.set(IdentSession(user.uid))

    return Response(HttpStatusCode.OK, "")
}

suspend fun find(call: ApplicationCall, clients: MutableMap<UUID, Client>): Response {
    val session: IdentSession? = call.sessions.get<IdentSession>()
    println("session: ${session}")

    val client = clients[session?.uid]
        ?: return Response(status = HttpStatusCode.BadRequest, message = CLIENT_NOT_FOUND)

    val content = call.request.queryParameters[QUERY_PATTERN]
        ?: return parameterNotFound(QUERY_PATTERN)

    val topic = call.request.headers[HEADER_TOPIC]
        ?: return parameterNotFound(HEADER_TOPIC)

    val timeout = call.request.headers[HEADER_TIMEOUT]?.toLong()
        ?: return parameterNotFound(HEADER_TIMEOUT)

    if (!client.topics.contains(topic))
        return Response(
            message = "Cannot find topic '${topic}' in client's subscriptions.",
            status = HttpStatusCode.BadRequest
        )

    val message = consume(Duration.ofSeconds(timeout), client.getConsumer(topic), content, topic)

    return when (message) {
        is Some ->
            Response(status = HttpStatusCode.OK, message = message.value)

        is None ->
            Response(status = HttpStatusCode.NoContent, message = "")

    }
}
