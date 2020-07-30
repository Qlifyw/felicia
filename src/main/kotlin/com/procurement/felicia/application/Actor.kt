package com.procurement.felicia.application

import com.procurement.felicia.application.network.model.Socket
import com.procurement.felicia.domain.KafkaError
import com.procurement.felicia.domain.Option
import com.procurement.felicia.domain.Result
import com.procurement.felicia.domain.Result.Companion.failure
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
import com.procurement.felicia.infrastructure.kafka.createKafkaConsumer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
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

class MessageConsumerFactory(
    val hosts: List<Socket>,
    val authCredentials: KafkaAuthCredentials,
    val groupId: String) {
    fun create(): KafkaConsumer<String, String> = createKafkaConsumer(hosts, authCredentials, groupId)
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

fun KafkaConsumer<*, *>.prepareAssignments(topic: String): List<TopicPartition> =
    this.partitionsFor(topic)
        .map { TopicPartition(it.topic(), it.partition()) }

fun KafkaConsumer<*, *>.getOffsets(assignments: List<TopicPartition>): Map<TopicPartition, OffsetAndMetadata> =
    assignments
        .associate { topicPartition -> topicPartition to OffsetAndMetadata(this.position(topicPartition)) }

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

fun KafkaConsumer<*, String>.shiftOffsets(breakpoint: ConsumerRecord<*, String>) {
    val consumer = this
    val recordTopicPartition = TopicPartition(breakpoint.topic(), breakpoint.partition())
    val recordCommitData = mapOf(
        recordTopicPartition to OffsetAndMetadata(breakpoint.offset())
    )
    consumer.commitSync(recordCommitData)
    consumer.seek(recordTopicPartition, breakpoint.offset() + 1)

    breakpoint.topic()
        .let { topic -> consumer.partitionsFor(topic).asSequence() }
        .filter { partitionInfo -> partitionInfo.partition() != breakpoint.partition() }
        .map { partitionInfo -> TopicPartition(partitionInfo.topic(), partitionInfo.partition()) }

        .associate { topicPartition -> topicPartition to breakpoint.timestamp() }
        .let { consumer.offsetsForTimes(it) }
        .filter { (_, offsetData) -> offsetData != null }
        .map { offsetData -> offsetData.key to OffsetAndMetadata(offsetData.value.offset() - 1) }
        .toMap()
        .apply { consumer.commitSync(this) }
        .forEach { (topicData, offsetData) -> consumer.seek(topicData, offsetData.offset() + 1) }
}


fun KafkaConsumer<*, String>.seekToStartPoint(offsets: Map<TopicPartition, OffsetAndMetadata>) {
    val consumer = this
    offsets.forEach { partition, offset ->
        consumer.seek(partition, offset)
    }
}

private fun KafkaConsumer<*, *>.tryCommit(offsets: Map<TopicPartition, OffsetAndMetadata>): Result<Unit, ConsumerConflict> = try {
    success(this.commitSync(offsets))
} catch (exception: CommitFailedException) {
    failure(ConsumerConflict())
}
