package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.core.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

sealed class TwoPossibleInvariants(message: String) : InvalidInvariantException(message) {
    class OneInvariantException(message: String) : TwoPossibleInvariants(message)
    class TwoInvariantExceptions(message: String) : TwoPossibleInvariants(message)
}

class FailureOne(cause: InvalidInvariantException) : InvalidInvariantFailure(cause)
class FailureTwo(cause: InvalidInvariantException) : InvalidInvariantFailure(cause)

open class InvalidInvariantsCommand(
    val exception: InvalidInvariantException,
) : Command()

class InvalidInvariantsCatchingCommand(
    exception: InvalidInvariantException,
) : InvalidInvariantsCommand(exception), InvariantCatchingMessage {
    override fun handleException(exception: InvalidInvariantException): FailureReason {
        return when (exception) {
            is TwoPossibleInvariants.OneInvariantException -> FailureOne(exception)
            is TwoPossibleInvariants.TwoInvariantExceptions -> FailureTwo(exception)
            else -> throw exception
        }
    }
}

class MultipleInvalidInvariantsCatchingCommand(
    exception: InvalidInvariantException,
) : InvalidInvariantsCommand(exception), InvariantCatchingMessage {
    override fun handleException(exception: InvalidInvariantException): FailureReason {
        if (exception !is TwoPossibleInvariants) throw exception
        return when (exception) {
            is TwoPossibleInvariants.OneInvariantException -> FailureOne(exception)
            is TwoPossibleInvariants.TwoInvariantExceptions -> FailureTwo(exception)
        }
    }
}

class InvalidInvariantsCommandHandler : CommandHandler<InvalidInvariantsCommand, Unit, FailureReason> {
    override suspend fun handle(message: InvalidInvariantsCommand): BusResult<Unit, FailureReason> {
        throw message.exception
    }
}

class InvariantsTest {
    @Test
    fun invariant_catcher_only_processes_invariant_catching_message() = runTest {
        val catcher = InvalidInvariantCatcher()

        assertFailsWith<InvalidInvariantException>("Failure message"){
            catcher.handle(
                InvalidInvariantsCommand(
                    InvalidInvariantException("Failure message")
                )
            ) {
                InvalidInvariantsCommandHandler().handle(it)
            }
        }
    }

    @Test
    fun invariant_catcher_converts_invalid_invariant_exception_to_result_failure() = runTest {
        val catcher = InvalidInvariantCatcher()

        val result = catcher.handle(
            InvalidInvariantsCatchingCommand(
                TwoPossibleInvariants.OneInvariantException("Failure message one")
            )
        ) {
            InvalidInvariantsCommandHandler().handle(it)
        }

        assertIs<BusResult<Any?, FailureReason>>(result)
        val failureReason = result.failureReasonOrNull()

        assertIs<InvalidInvariantFailure>(failureReason)
        assertEquals("Failure message one", failureReason.message)
    }

    @Test
    fun invariant_catcher_converts_multiple_invalid_invariant_exception_to_result_failures() = runTest {
        val catcher = InvalidInvariantCatcher()

        val result = catcher.handle(
            MultipleInvalidInvariantsCatchingCommand(
                MultipleInvalidInvariantsException(errors = listOf(
                    TwoPossibleInvariants.OneInvariantException("Failure message"),
                    TwoPossibleInvariants.TwoInvariantExceptions("Other failure message"),
                ))
            )
        ) {
            InvalidInvariantsCommandHandler().handle(it)
        }

        assertIs<BusResult<Any?, MultipleFailureReasons>>(result)
        val failureReasons = result.failureReasonOrNull()!!.reasons
        assertEquals(2, failureReasons.size)
        assertEquals("Failure message", failureReasons[0].message)
        assertEquals("Other failure message", failureReasons[1].message)
    }
}
