package com.procurement.felicia.application

import com.procurement.felicia.application.network.model.Socket
import com.procurement.felicia.domain.KafkaError
import com.procurement.felicia.domain.Option
import com.procurement.felicia.domain.Result
import com.procurement.felicia.domain.Result.Companion.success
import com.procurement.felicia.domain.asSuccess
import com.procurement.felicia.domain.errors.ConsumerConflict
import com.procurement.felicia.domain.model.Action
import com.procurement.felicia.domain.model.AuthorizedUser
import com.procurement.felicia.domain.model.Client
import com.procurement.felicia.domain.model.NewClient
import com.procurement.felicia.domain.model.NonAuthorizedUser
import com.procurement.felicia.domain.model.Reassign
import com.procurement.felicia.domain.model.User
import com.procurement.felicia.infrastructure.kafka.KafkaAuthCredentials
import com.procurement.felicia.infrastructure.kafka.MessageConsumerFactory
import com.procurement.felicia.infrastructure.kafka.getOffsets
import com.procurement.felicia.infrastructure.kafka.prepareAssignments
import com.procurement.felicia.infrastructure.kafka.seekToStartPoint
import com.procurement.felicia.infrastructure.kafka.shiftOffsets
import com.procurement.felicia.infrastructure.kafka.tryCommit
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

fun startListen(action: Action, topics: Set<String>, user: User, hosts: List<Socket>): Result<Client, KafkaError> {

    val authCredentials = getAuthCredentials(user)
    val consumerFactory = MessageConsumerFactory(hosts, authCredentials, user.groupId)
    val client = when (action) {
        is NewClient -> assign(topics, consumerFactory)
        is Reassign  -> reassign(topics, consumerFactory, action.client)
    }
        .orForwardFail { fail -> return fail }

    return success(client)
}

private fun assign(topics: Iterable<String>, consumerFactory: MessageConsumerFactory): Result<Client, ConsumerConflict> {
    val checkin = topics
        .associate { topic ->
            val consumer = consumerFactory.create()
            checkin(topic, consumer).orForwardFail { error -> return error }
        }

    return success(Client(checkin))
}

private fun reassign(topics: Iterable<String>, consumerFactory: MessageConsumerFactory, client: Client): Result<Client, ConsumerConflict> {
    val oldTopics = client.topics
    val updatedTopics = (topics + oldTopics).toSet()

    val newSubscriptions = updatedTopics
        .associate { topic ->
            val consumer = consumerFactory.create()
            checkin(topic, consumer).orForwardFail { error -> return error }
        }

    return client
        .apply { addSubscriptions(newSubscriptions) }
        .asSuccess()
}

private fun checkin(topic: String, consumer: KafkaConsumer<String, String>): Result<Pair<String, KafkaConsumer<String, String>>, ConsumerConflict> {

    val assignments = consumer.prepareAssignments(topic)
    consumer.assign(assignments)
    val offsets = consumer.getOffsets(assignments)
    consumer.tryCommit(offsets)
        .orForwardFail { error -> return error }

    return success(topic to consumer)
}

private fun getAuthCredentials(user: User) =
    when (user) {
        is AuthorizedUser    -> KafkaAuthCredentials.SaslAuth(user.login, user.password)
        is NonAuthorizedUser -> KafkaAuthCredentials.NONE
    }

suspend fun consume(timeout: Duration, consumer: KafkaConsumer<String, String>, content: String, topic: String): Option<String> =
    withTimeout(timeout.toMillis()) {
        val assignments = consumer.prepareAssignments(topic)
        val offsets = consumer.getOffsets(assignments)

        while (isActive) {
            val records = consumer.poll(Duration.ofMillis(500))
            if (!records.isEmpty) {
                val searchResult = records.findFor(content)
                when (searchResult) {
                    is Option.Some -> {
                        consumer.shiftOffsets(searchResult.value)
                        return@withTimeout Option.Some(searchResult.value.value())
                    }
                    is Option.None -> Unit
                }
            }
        }
        consumer.seekToStartPoint(offsets)
        Option.None
    }

fun ConsumerRecords<*, String>.findFor(content: String): Option<ConsumerRecord<*, String>> {
    this.forEach { record ->
        if (record.value().contains(content)) {
            return Option.Some(record)
        }
    }
    return Option.None
}
