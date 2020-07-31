package com.procurement.felicia.infrastructure.kafka

import com.procurement.felicia.domain.Result
import com.procurement.felicia.domain.errors.ConsumerConflict
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition

fun KafkaConsumer<*, *>.prepareAssignments(topic: String): List<TopicPartition> =
    this.partitionsFor(topic)
        .map { TopicPartition(it.topic(), it.partition()) }

fun KafkaConsumer<*, *>.getOffsets(assignments: List<TopicPartition>): Map<TopicPartition, OffsetAndMetadata> =
    assignments
        .associate { topicPartition -> topicPartition to OffsetAndMetadata(
            this.position(topicPartition)
        )
        }

fun KafkaConsumer<*, String>.shiftOffsets(breakpoint: ConsumerRecord<*, String>) {
    val consumer = this
    val recordTopicPartition = TopicPartition(
        breakpoint.topic(),
        breakpoint.partition()
    )
    val recordCommitData = mapOf(
        recordTopicPartition to OffsetAndMetadata(breakpoint.offset())
    )
    consumer.commitSync(recordCommitData)
    consumer.seek(recordTopicPartition, breakpoint.offset() + 1)

    breakpoint.topic()
        .let { topic -> consumer.partitionsFor(topic).asSequence() }
        .filter { partitionInfo -> partitionInfo.partition() != breakpoint.partition() }
        .map { partitionInfo ->
            TopicPartition(
                partitionInfo.topic(),
                partitionInfo.partition()
            )
        }

        .associate { topicPartition -> topicPartition to breakpoint.timestamp() }
        .let { consumer.offsetsForTimes(it) }
        .filter { (_, offsetData) -> offsetData != null }
        .map { offsetData -> offsetData.key to OffsetAndMetadata(
            offsetData.value.offset() - 1
        )
        }
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

fun KafkaConsumer<*, *>.tryCommit(offsets: Map<TopicPartition, OffsetAndMetadata>): Result<Unit, ConsumerConflict> = try {
    Result.success(this.commitSync(offsets))
} catch (exception: CommitFailedException) {
    Result.failure(ConsumerConflict())
}