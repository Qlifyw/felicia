package com.procurement.felicia.application

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

fun createKafkaConsumer(host: String, authCredentials: KafkaAuthCredentials): KafkaConsumer<String, String> {

    val props = Properties()
    props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = host
    props[ConsumerConfig.GROUP_ID_CONFIG] = UUID.randomUUID().toString()
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

fun createKafkaProducer(host: String): KafkaProducer<String, String> {
    val props = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    }
    return KafkaProducer(props)
}

sealed class KafkaAuthCredentials {

    class SaslAuth(login: String, password: String) : KafkaAuthCredentials() {
        companion object {
            private val SASL_JAAS_TEMPLATE = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";"
        }

        val SASL_JAAS = String.format(SASL_JAAS_TEMPLATE, login, password)
    }

    class NONE : KafkaAuthCredentials()
}
