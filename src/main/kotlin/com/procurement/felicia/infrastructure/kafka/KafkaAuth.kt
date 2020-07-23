package com.procurement.felicia.infrastructure.kafka

sealed class KafkaAuthCredentials {

    class SaslAuth(login: String, password: String) : KafkaAuthCredentials() {
        companion object {
            private val SASL_JAAS_TEMPLATE = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";"
        }

        val SASL_JAAS = String.format(SASL_JAAS_TEMPLATE, login, password)
    }

    object NONE : KafkaAuthCredentials()
}