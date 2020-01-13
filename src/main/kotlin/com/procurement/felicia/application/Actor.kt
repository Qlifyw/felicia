package com.procurement.felicia.application

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.security.MessageDigest
import java.time.Duration
import java.util.*


/**
 * - Get message from topic that user don't observe
 *
 *      ** TODO **
 *      * https://medium.com/@werneckpaiva/how-to-seek-kafka-consumer-offsets-by-timestamp-de351ba35c61
 *
 */


fun startListen(topics: Set<String>, user: User, clients: MutableMap<UUID, Client>, host: String) {
    val action = ackAction(clients, user)
    when (action) {
        is NewClient -> {
            val authCredentials = when (user) {
                is AuthorizedUser -> KafkaAuthCredentials.SaslAuth(user.login, user.password)
                is NonAuthorizedUser -> KafkaAuthCredentials.NONE()
            }
            createKafkaConsumer(host, authCredentials, user.groupId)
                .apply {
                    println("AAAA   ${Thread.currentThread().name}")
                    val assignments = topics.asSequence()
                        .flatMap { topic -> this.partitionsFor(topic).asSequence() }
                        .map { it.topic() to it.partition() }
                        .map { TopicPartition(it.first, it.second) }
                        .toList()

                    this.assign(assignments)

                    val commitData = assignments.map { topicPartition ->
                        topicPartition to OffsetAndMetadata(this.position(topicPartition))
                    }.toMap()
                    this.commitSync(commitData)
                }
                .also { consumer ->
                    val client = Client(topics, consumer)
                    clients.put(user.uid, client)
                }
        }

        is Reassign  -> {
            val oldTopics = action.client.topics
            val updatedTopics = (topics + oldTopics).toSet()
            val consumer = action.client.source
            val assignments = updatedTopics.asSequence()
                .flatMap { topic -> consumer.partitionsFor(topic).asSequence() }
                .map { it.topic() to it.partition() }
                .map { TopicPartition(it.first, it.second) }
                .toList()
            consumer.assign(assignments)
        }
    }
}

fun consume(
    consumer: KafkaConsumer<String, String>,
    content: String,
    targetTopic: String,
    shutdown: CompletableDeferred<Boolean>
): Deferred<String> =
    GlobalScope.async<String> {
        while (!shutdown.isCompleted) {
            val records = consumer.poll(Duration.ofMillis(500))
            if (!records.isEmpty) {
                println("polled: ${records.count()}")
                records.forEach { record ->
                    println("${record.value()} | ${this}")
                    if (record.value().contains(content)) {
                        println("record offset: ${record.offset()}")

                        // TODO save partition local. Don't make expensive i/o call
                        val otherPartitions = targetTopic
                            .let { topic -> consumer.partitionsFor(topic).asSequence() }
                            .filter { it.partition() != record.partition() }
                            .map { it.topic() to it.partition() }
                            .map { TopicPartition(it.first, it.second) }
                            .toList()

                        val recordCommitData = mapOf(
                            TopicPartition(
                                targetTopic,
                                record.partition()
                            ) to OffsetAndMetadata(record.offset())
                        )
                        consumer.commitSync(recordCommitData)
                        consumer.seek(TopicPartition(targetTopic, record.partition()), record.offset() + 1)

                        val offsetsData = consumer.offsetsForTimes(otherPartitions.map { it to record.timestamp() }.toMap())
                        val commitData = offsetsData
                            .filter { it.value != null }
                            .map { offsetData -> offsetData.key to OffsetAndMetadata(offsetData.value.offset() - 1) }
                            .toMap()

                        consumer.commitSync(commitData)
                        commitData.forEach {
                            consumer.seek(it.key, it.value.offset() + 1)
                        }

                        return@async record.value()
                    }
                }
                consumer.commitSync()
            }
        }
        throw RuntimeException()
    }

data class Client(
    val topics: Set<String>,
    val source: KafkaConsumer<String, String>
)

sealed class Action
class NewClient : Action()
data class Reassign(val client: Client) : Action()

fun ackAction(clients: MutableMap<UUID, Client>, user: User): Action {
    val client = clients[user.uid]
    return if (client == null) NewClient() else Reassign(client)
}

fun String.asSHA256(): String {
    val bytes = this.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("", { str, it -> str + "%02x".format(it) })
}