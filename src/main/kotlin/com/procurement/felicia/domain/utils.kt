package com.procurement.felicia.domain

fun String.splitBy(delimiter: String) = this.split(delimiter)
    .map { it.trim() }
    .toSet()

sealed class Option<out T> {
    data class Some<T>(val value: T) : Option<T>()
    object None : Option<Nothing>()
}
