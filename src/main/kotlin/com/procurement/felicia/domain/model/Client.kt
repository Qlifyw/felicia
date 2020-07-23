package com.procurement.felicia.domain.model

import org.apache.kafka.clients.consumer.KafkaConsumer
import java.util.*

class Client(subscriptions: Map<String, KafkaConsumer<String, String>>) {
    private val subscriptions: MutableMap<String, KafkaConsumer<String, String>> = mutableMapOf()

    init {
        this.subscriptions.putAll(subscriptions)
    }

    val topics: Set<String>
        get() = subscriptions.keys

    fun getConsumer(topic: String): KafkaConsumer<String, String> = subscriptions.getValue(topic)

    fun addSubscriptions(subscriptions: Map<String, KafkaConsumer<String, String>>) {
        this.subscriptions.putAll(subscriptions)
    }
}

fun defineAction(clients: MutableMap<UUID, Client>, user: User): Action {
    val client = clients[user.uid]
    return if (client == null)
        NewClient()
    else
        Reassign(client)
}