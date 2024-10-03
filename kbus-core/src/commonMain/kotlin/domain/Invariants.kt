package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.core.BusResult
import com.jimbroze.kbus.core.FailureReason
import com.jimbroze.kbus.core.Message
import com.jimbroze.kbus.core.Middleware
import com.jimbroze.kbus.core.MiddlewareHandler
import com.jimbroze.kbus.core.MultipleFailureReasons

open class InvalidInvariantException(override val message: String) : Throwable(message)

class MultipleInvalidInvariantsException(
    message: String? = null,
    val errors: List<InvalidInvariantException> = emptyList(),
) : InvalidInvariantException(message ?: errors.joinToString(", "))

open class InvalidInvariantFailure(override val message: String) : FailureReason(message) {
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

interface InvariantCatchingMessage {
    fun handleException(exception: InvalidInvariantException): FailureReason
}

class InvalidInvariantCatcher : Middleware {
    override suspend fun <TMessage : Message> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>,
    ): Any? {
        if (message !is InvariantCatchingMessage) return nextMiddleware(message)

        return try {
            nextMiddleware(message)
        } catch (e: MultipleInvalidInvariantsException) {
            val failures = e.errors.map { message.handleException(it) }
            BusResult.failure<Any?, MultipleFailureReasons>(MultipleFailureReasons(failures))
        } catch (e: InvalidInvariantException) {
            BusResult.failure<Any?, FailureReason>(message.handleException(e))
        }
    }
}
