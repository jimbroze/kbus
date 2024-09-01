package com.jimbroze.kbus.core

sealed class BusResult<out TValue, out TFailure : FailureReason> {
    internal class Success<TValue, TFailure : FailureReason>(internal val value: TValue) : BusResult<TValue, TFailure>() {
        override fun toString(): String = "Success($value)"
    }

    internal class Failure<TValue, TFailure : FailureReason>(internal val failureReasons: List<TFailure>) : BusResult<TValue, TFailure>() {
        override fun toString(): String = "Failure(${failureReasons.joinToString(", ")})"
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): TValue? =
        when (this) {
            is Success -> value
            else -> null
        }

    fun exceptions(): List<TFailure> =
        when (this) {
            is Failure -> failureReasons
            else -> emptyList()
        }

    companion object {
        fun <TValue, TFailure : FailureReason> success(value: TValue): BusResult<TValue, TFailure>
            = Success(value)
        fun <TValue, TFailure : FailureReason> failure(exceptions: List<TFailure>): BusResult<TValue, TFailure> =
            Failure(exceptions)
    }
}

abstract class FailureReason(val message: String? = null) {
    override fun toString(): String = message ?: ""
}

class GenericFailure(message: String?) : FailureReason(message)

interface ResultReturningHandler<
    TMessage : Message,
    TReturn : Any?,
    TFailure : FailureReason,
> : MessageHandler<TMessage> {
    override suspend fun handle(message: TMessage): BusResult<TReturn, TFailure>

    fun success(returnValue: TReturn): BusResult<TReturn, TFailure> = BusResult.success(returnValue)
    fun success(): BusResult<Unit, TFailure> = BusResult.success(Unit)
    fun failure(exceptions: List<TFailure>): BusResult<TReturn, TFailure> = BusResult.failure(exceptions)
    fun failure(exception: TFailure): BusResult<TReturn, TFailure> = BusResult.failure(listOf(exception))
    fun failure(message: String): BusResult<TReturn, GenericFailure> = BusResult.failure(listOf(GenericFailure(message)))
}

// Example of a specific service result

sealed class UserServiceException(message: String?) : FailureReason(message) {
    class UserNotFound(message: String?) : UserServiceException(message)
    class InvalidCredentials(message: String?) : UserServiceException(message)
    class DatabaseError(message: String?) : UserServiceException(message)
    // Add more specific exceptions as needed
}
