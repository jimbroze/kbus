package com.jimbroze.kbus.core.result

import com.jimbroze.kbus.core.common.MessageHandler
import com.jimbroze.kbus.core.common.ResultReturningMessage

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
