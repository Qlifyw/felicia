package com.procurement.felicia.domain

sealed class Error {
    abstract val description: String
}

abstract class DataValidationError(
    override val description: String,
    val name: String
) : Error()

abstract class KafkaError(
    override val description: String
) : Error()