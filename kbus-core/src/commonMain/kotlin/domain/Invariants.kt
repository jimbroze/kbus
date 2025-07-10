package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.core.FailureReason
import com.jimbroze.kbus.core.KBusResult
import com.jimbroze.kbus.core.Message
import com.jimbroze.kbus.core.Middleware
import com.jimbroze.kbus.core.MiddlewareHandler
import com.jimbroze.kbus.core.ResultReturningMessage

open class InvalidInvariantException(override val message: String) : Throwable(message)

class MultipleInvalidInvariantsException(
    message: String? = null,
    val errors: List<InvalidInvariantException> = emptyList(),
) : InvalidInvariantException(message ?: errors.joinToString(", "))

open class InvalidInvariantFailureReason(override val message: String) : FailureReason {
    constructor(cause: InvalidInvariantException) : this(cause.message)
}

abstract class HasInvariants {
    protected fun assert(invariant: Boolean, message: String) {
        if (!invariant) throw InvalidInvariantException(message)
    }

    protected fun assert(invariant: Boolean, exception: InvalidInvariantException) {
        if (!invariant) throw exception
    }
}

interface InvariantCatchingMessage<TResult : KBusResult> : ResultReturningMessage<TResult> {
    fun invariantFailure(failure: InvalidInvariantFailureReason): TResult

    fun handleException(exception: InvalidInvariantException): InvalidInvariantFailureReason
}

class InvalidInvariantCatcher : Middleware {
    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        if (message !is InvariantCatchingMessage<*>) return nextMiddleware(message)

        @Suppress("UNCHECKED_CAST")
        return try {
            nextMiddleware(message)
        } catch (e: InvalidInvariantException) {
            message.invariantFailure(message.handleException(e))
        }
            as TResult
    }
}
