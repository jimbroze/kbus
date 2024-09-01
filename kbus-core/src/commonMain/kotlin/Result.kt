package com.jimbroze.kbus.core

sealed class BusResult<out TValue, out TFailure : ResultFailure>() {
    internal class Success<TValue, TFailure : ResultFailure>(internal val value: TValue) : BusResult<TValue, TFailure>() {
        override fun toString(): String = "Success($value)"
    }

    internal class Failure<TValue, TFailure : ResultFailure>(internal val exception: TFailure) : BusResult<TValue, TFailure>() {
        override fun toString(): String = "Failure($exception)"
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): TValue? =
        when (this) {
            is Success -> value
            else -> null
        }

    fun exceptionOrNull(): TFailure? =
        when (this) {
            is Failure -> exception
            else -> null
        }

    companion object {
        fun <TValue, TFailure : ResultFailure> success(value: TValue): BusResult<TValue, TFailure>
            = Success(value)
        fun <TValue, TFailure : ResultFailure> failure(exception: TFailure): BusResult<TValue, TFailure> =
            Failure(exception)
    }
}

// TODO change to allow multiple errors
abstract class ResultFailure(val message: String? = null)

class GenericFailure(message: String?) : ResultFailure(message)

interface ResultReturningHandler<
    TMessage : Message,
    TReturn : Any?,
    TFailure : ResultFailure,
> : MessageHandler<TMessage> {
    override suspend fun handle(message: TMessage): BusResult<TReturn, TFailure>

    fun success(returnValue: TReturn): BusResult<TReturn, TFailure> = BusResult.success(returnValue)
    fun success(): BusResult<Unit, TFailure> = BusResult.success(Unit)
    fun failure(exception: TFailure): BusResult<TReturn, TFailure> = BusResult.failure(exception)
    fun failure(message: String): BusResult<TReturn, GenericFailure> = BusResult.failure(GenericFailure(message))
}

// Example of a specific service result

sealed class UserServiceException(message: String?) : ResultFailure(message) {
    class UserNotFound(message: String?) : UserServiceException(message)
    class InvalidCredentials(message: String?) : UserServiceException(message)
    class DatabaseError(message: String?) : UserServiceException(message)
    // Add more specific exceptions as needed
}
