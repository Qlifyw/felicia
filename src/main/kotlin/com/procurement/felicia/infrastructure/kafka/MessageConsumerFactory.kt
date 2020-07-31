package com.procurement.felicia.infrastructure.kafka

import com.procurement.felicia.application.network.model.Socket
import org.apache.kafka.clients.consumer.KafkaConsumer

class MessageConsumerFactory(
    val hosts: List<Socket>,
    val authCredentials: KafkaAuthCredentials,
    val groupId: String
) {
    fun create(): KafkaConsumer<String, String> = createKafkaConsumer(hosts, authCredentials, groupId)
}