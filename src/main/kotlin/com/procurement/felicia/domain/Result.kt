package com.procurement.felicia.domain

fun <T, E> T.asSuccess(): Result<T, E> = Result.success(this)
fun <T, E> E.asFailure(): Result<T, E> = Result.failure(this)

sealed class Result<out T, out E> {
    companion object {
        fun <T> success(value: T) = Success(value)
        fun <E> failure(error: E) = Failure(error)
    }

    abstract val isSuccess: Boolean

    abstract val get: T
    abstract val error: E

    inline fun orForwardFail(failure: (Failure<E>) -> Nothing): T = when (this) {
        is Success -> this.get
        is Failure -> failure(this)
    }

    inline fun orReturnFail(error: (E) -> Nothing): T = when (this) {
        is Success -> this.get
        else -> error(this.error)
    }

    val orNull: T?
        get() = when (this) {
            is Success -> get
            is Failure -> null
        }

    class Success<out T> internal constructor(value: T) : Result<T, Nothing>() {
        override val isSuccess: Boolean = true
        override val get: T = value
        override val error: Nothing
            get() = throw NoSuchElementException("The result does not contain an error.")
    }

    class Failure<out E> internal constructor(value: E) : Result<Nothing, E>() {
        override val isSuccess: Boolean = false
        override val get: Nothing
            get() = throw NoSuchElementException("The result does not contain a value.")
        override val error: E = value
    }
}

inline fun <T, R, E> Collection<T>.mapToResult(transform: (T) -> Result<R, E>): Result<List<R>, E> = mutableListOf<R>()
    .apply {
        for (element in this@mapToResult) {
            when (val result = transform(element)) {
                is Result.Success -> this.add(result.get)
                is Result.Failure -> return result
            }
        }
    }
    .asSuccess()