package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.middleware.middleware.InvariantCatcherMiddleware
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

sealed class TwoPossibleInvariants(message: String) : InvalidInvariantException(message) {
    class OneInvariantException(message: String) : TwoPossibleInvariants(message)

    class TwoInvariantExceptions(message: String) : TwoPossibleInvariants(message)
}

class FailureReasonOne(cause: InvalidInvariantException) : InvalidInvariantFailureReason(cause)

class FailureReasonTwo(cause: InvalidInvariantException) : InvalidInvariantFailureReason(cause)

data class InvariantTestFailure(override val reason: InvalidInvariantFailureReason) :
    MessageFailure

open class InvalidInvariantsCommand(val exception: InvalidInvariantException) :
    Command<BusResult<Unit, InvariantTestFailure>>()

class InvalidInvariantsCatchingCommand(exception: InvalidInvariantException) :
    InvalidInvariantsCommand(exception),
    InvariantCatchingMessage<BusResult<Unit, InvariantTestFailure>> {
    override fun invariantFailure(
        failure: InvalidInvariantFailureReason
    ): BusResult<Unit, InvariantTestFailure> {
        return BusResult.failure(InvariantTestFailure(failure))
    }

    override fun handleException(
        exception: InvalidInvariantException
    ): InvalidInvariantFailureReason {
        return when (exception) {
            is TwoPossibleInvariants.OneInvariantException -> FailureReasonOne(exception)
            is TwoPossibleInvariants.TwoInvariantExceptions -> FailureReasonTwo(exception)
            else -> throw exception
        }
    }
}

class InvalidInvariantsCommandHandler :
    CommandHandler<InvalidInvariantsCommand, BusResult<Unit, InvariantTestFailure>>() {
    override suspend fun handle(
        message: InvalidInvariantsCommand
    ): BusResult<Unit, InvariantTestFailure> {
        throw message.exception
    }
}

class InvariantsTest {
    @Test
    fun invariant_catcher_only_processes_invariant_catching_Message() = runTest {
        val catcher = InvariantCatcherMiddleware()

        assertFailsWith<InvalidInvariantException>("Failure message") {
            catcher.handle(InvalidInvariantsCommand(InvalidInvariantException("Failure message"))) {
                InvalidInvariantsCommandHandler().handle(it)
            }
        }
    }

    @Test
    fun invariant_catcher_converts_invalid_invariant_exception_to_result_failure() = runTest {
        val catcher = InvariantCatcherMiddleware()

        val result =
            catcher.handle(
                InvalidInvariantsCatchingCommand(
                    TwoPossibleInvariants.OneInvariantException("Failure message one")
                )
            ) {
                InvalidInvariantsCommandHandler().handle(it)
            }

        assertIs<BusResult<Any?, MessageFailure>>(result)
        val failure = result.failureOrNull()
        assertIs<InvariantTestFailure>(failure)
        val failureReason = failure.reason
        assertIs<FailureReasonOne>(failureReason)
        assertEquals("Failure message one", failureReason.message)
    }
}
