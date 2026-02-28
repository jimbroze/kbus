package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.contracts.common.ResultReturningMessage
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.KBusResult

// FIXME remove all this???

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
