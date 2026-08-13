package com.jimbroze.kbus.api.result

import com.jimbroze.kbus.api.common.MessageHandler
import com.jimbroze.kbus.api.common.ResultReturningMessage

typealias KBusResult = BusResult<*, *>

sealed class BusResult<out TValue : Any?, out TMessageFailure : MessageFailure> {
    internal data class Success<out TValue>(internal val value: TValue) :
        BusResult<TValue, Nothing>() {
        override fun toString(): String = "Success($value)"
    }

    internal data class Failure<out TMessageFailure : MessageFailure>(
        internal val messageFailure: TMessageFailure
    ) : BusResult<Nothing, TMessageFailure>() {
        override fun toString(): String = "Failure: ${messageFailure.reason.message}"
    }

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): TValue? = if (this is Success) value else null

    fun failureOrNull(): TMessageFailure? = if (this is Failure) messageFailure else null

    fun <TNewValue : Any?> mapSuccess(
        transform: (TValue) -> TNewValue
    ): BusResult<TNewValue, TMessageFailure> =
        when (this) {
            is Success -> Success(transform(value))
            is Failure -> this
        }

    /**
     * Restates a failure in the terms of the message being answered, so a handler forwarding
     * another message's result keeps its own declared failure type.
     */
    fun <TNewFailure : MessageFailure> mapFailure(
        transform: (TMessageFailure) -> TNewFailure
    ): BusResult<TValue, TNewFailure> =
        when (this) {
            is Success -> this
            is Failure -> Failure(transform(messageFailure))
        }

    fun <TOut> collapse(onSuccess: (TValue) -> TOut, onFailure: (TMessageFailure) -> TOut): TOut =
        when (this) {
            is Success -> onSuccess(value)
            is Failure -> onFailure(messageFailure)
        }

    companion object {
        fun <TValue : Any?> success(value: TValue): BusResult<TValue, Nothing> = Success(value)

        fun <TMessageFailure : MessageFailure> failure(
            failureReason: TMessageFailure
        ): BusResult<Nothing, TMessageFailure> = Failure(failureReason)
    }
}

interface FailureReason {
    val message: String
}

class MultipleFailureReasons(
    val reasons: List<FailureReason>,
    override val message: String = "There were multiple failures: ${reasons.joinToString(", ")}",
) : FailureReason {
    override fun toString(): String = message
}

class GenericFailure(override val message: String) : FailureReason

interface MessageFailure {
    val reason: FailureReason
}

interface ResultReturningMessageHandler<
    TMessage : ResultReturningMessage<TResult>,
    TResult : KBusResult,
> : MessageHandler<TMessage> {
    override suspend fun handle(message: TMessage): TResult
}
