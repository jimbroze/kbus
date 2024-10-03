package com.jimbroze.kbus.core

sealed class BusResult<out TValue, out TFailure : FailureReason> {
    internal class Success<TValue, TFailure : FailureReason>(internal val value: TValue) :
        BusResult<TValue, TFailure>() {
        override fun toString(): String = "Success($value)"
    }

    internal class Failure<TValue, TFailure : FailureReason>(internal val failureReason: TFailure) :
        BusResult<TValue, TFailure>() {
        override fun toString(): String = "Failure(${failureReason})"
    }

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): TValue? =
        when (this) {
            is Success -> value
            else -> null
        }

    fun failureReasonOrNull(): TFailure? =
        when (this) {
            is Failure -> failureReason
            else -> null
        }

    companion object {
        fun <TValue, TFailure : FailureReason> success(value: TValue): BusResult<TValue, TFailure> =
            Success(value)

        fun <TValue, TFailure : FailureReason> failure(
            failureReason: TFailure
        ): BusResult<TValue, TFailure> = Failure(failureReason)
    }
}

abstract class FailureReason(open val message: String? = null) {
    override fun toString(): String = message ?: ""
}

class MultipleFailureReasons(
    val reasons: List<FailureReason>,
    message: String? = "There were multiple failures",
) : FailureReason(message) {
    override fun toString(): String = message ?: reasons.joinToString(", ")
}

class GenericFailure(message: String?) : FailureReason(message)

interface ResultReturningHandler<TMessage : Message, TReturn : Any?, TFailure : FailureReason> :
    MessageHandler<TMessage> {
    override suspend fun handle(message: TMessage): BusResult<TReturn, TFailure>

    fun success(returnValue: TReturn): BusResult<TReturn, TFailure> = BusResult.success(returnValue)

    fun success(): BusResult<Unit, TFailure> = BusResult.success(Unit)

    fun failure(
        exceptions: List<TFailure>,
        message: String?,
    ): BusResult<TReturn, MultipleFailureReasons> =
        BusResult.failure(MultipleFailureReasons(exceptions, message))

    fun failure(exceptions: List<TFailure>): BusResult<TReturn, MultipleFailureReasons> =
        BusResult.failure(MultipleFailureReasons(exceptions))

    fun failure(exception: TFailure): BusResult<TReturn, TFailure> = BusResult.failure(exception)

    fun failure(message: String): BusResult<TReturn, GenericFailure> =
        BusResult.failure(GenericFailure(message))
}
