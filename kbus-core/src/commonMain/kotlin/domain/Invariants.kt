package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.core.*

open class InvalidInvariantException(override val message: String) : RuntimeException(message)

abstract class HasInvariants {
    protected fun assert(invariant: Boolean, message: String) {
        if (!invariant) throw InvalidInvariantException(message)
    }
    protected fun assert(invariant: Boolean, exception: InvalidInvariantException) {
        if (!invariant) throw exception
    }
}

interface InvariantCatchingMessage

class InvalidInvariantCatcher : Middleware {
    override suspend fun <TMessage : Message> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>
    ): Any? {
        if (message !is InvariantCatchingMessage) return nextMiddleware(message)

        return try {
            nextMiddleware(message)
        } catch (e: InvalidInvariantException) {
//            throw ResultFailure(e.message)
            throw e //FIXME what should we do with InvalidInvariantExceptions?
        }
    }
}
