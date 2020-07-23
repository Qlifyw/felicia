package com.procurement.felicia.infrastructure.kafka

import com.procurement.felicia.application.network.model.Socket
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

fun createKafkaConsumer(hosts: List<Socket>, authCredentials: KafkaAuthCredentials, groupId: String): KafkaConsumer<String, String> {

    val props = Properties()
    props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = hosts.map { it.toString() }
    props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
    props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
    props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"

    when (authCredentials) {
        is KafkaAuthCredentials.SaslAuth -> {
            props[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SASL_PLAINTEXT"
            props[SaslConfigs.SASL_MECHANISM] = "PLAIN"
            props[SaslConfigs.SASL_JAAS_CONFIG] = authCredentials.SASL_JAAS
        }
    }
    return KafkaConsumer(props)
}
