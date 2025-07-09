package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.core.BusResult
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.MessageFailure
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

sealed class TwoPossibleInvariants(message: String) : InvalidInvariantException(message) {
    class OneInvariantException(message: String) : TwoPossibleInvariants(message)

    class TwoInvariantExceptions(message: String) : TwoPossibleInvariants(message)
}

class FailureReasonOne(cause: InvalidInvariantException) : InvalidInvariantFailureReason(cause)

class FailureReasonTwo(cause: InvalidInvariantException) : InvalidInvariantFailureReason(cause)

open class InvalidInvariantsCommand(val exception: InvalidInvariantException) :
    Command<Unit, MessageFailure>()

class InvalidInvariantsCatchingCommand(exception: InvalidInvariantException) :
    InvalidInvariantsCommand(exception), InvariantCatchingMessage<Unit, MessageFailure> {
    override fun invariantFailure(
        failure: InvalidInvariantFailureReason
    ): BusResult<Unit, MessageFailure> {
        TODO("Not yet implemented")
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

class MultipleInvalidInvariantsCatchingCommand(exception: InvalidInvariantException) :
    InvalidInvariantsCommand(exception), InvariantCatchingMessage<Unit, MessageFailure> {
    override fun invariantFailure(
        failure: InvalidInvariantFailureReason
    ): BusResult<Unit, MessageFailure> {
        TODO("Not yet implemented")
    }

    override fun handleException(
        exception: InvalidInvariantException
    ): InvalidInvariantFailureReason {
        if (exception !is TwoPossibleInvariants) throw exception
        return when (exception) {
            is TwoPossibleInvariants.OneInvariantException -> FailureReasonOne(exception)
            is TwoPossibleInvariants.TwoInvariantExceptions -> FailureReasonTwo(exception)
        }
    }
}

class InvalidInvariantsCommandHandler :
    CommandHandler<InvalidInvariantsCommand, Unit, MessageFailure>() {
    override suspend fun handle(
        message: InvalidInvariantsCommand
    ): BusResult<Unit, MessageFailure> {
        throw message.exception
    }
}

class InvariantsTest {
    @Test
    fun invariant_catcher_only_processes_invariant_catching_Message() = runTest {
        val catcher = InvalidInvariantCatcher()

        assertFailsWith<InvalidInvariantException>("Failure message") {
            catcher.handle(InvalidInvariantsCommand(InvalidInvariantException("Failure message"))) {
                InvalidInvariantsCommandHandler().handle(it)
            }
        }
    }

    //    @Test
    //    fun invariant_catcher_converts_invalid_invariant_exception_to_result_failure() = runTest {
    //        val catcher = InvalidInvariantCatcher()
    //
    //        val result =
    //            catcher.handle(
    //                InvalidInvariantsCatchingCommand(
    //                    TwoPossibleInvariants.OneInvariantException("Failure message one")
    //                )
    //            ) {
    //                InvalidInvariantsCommandHandler().handle(it)
    //            }
    //
    //        assertIs<BusResult<Any?, MessageFailure>>(result)
    //        val failureReason = result.failureOrNull()
    //
    //        assertIs<InvalidInvariantFailureReason>(failureReason)
    //        assertEquals("Failure message one", failureReason.message)
    //    }

    //    @Test
    //    fun invariant_catcher_converts_multiple_invalid_invariant_exception_to_result_failures() =
    //        runTest {
    //            val catcher = InvalidInvariantCatcher()
    //
    //            val result =
    //                catcher.handle(
    //                    MultipleInvalidInvariantsCatchingCommand(
    //                        MultipleInvalidInvariantsException(
    //                            errors =
    //                                listOf(
    //                                    TwoPossibleInvariants.OneInvariantException("Failure
    // message"),
    //                                    TwoPossibleInvariants.TwoInvariantExceptions(
    //                                        "Other failure message"
    //                                    ),
    //                                )
    //                        )
    //                    )
    //                ) {
    //                    InvalidInvariantsCommandHandler().handle(it)
    //                }
    //
    //            assertIs<BusResult<Any?, MultipleFailureReasons>>(result)
    //            val failureReasons = result.failureOrNull()!!.reasons
    //            assertEquals(2, failureReasons.size)
    //            assertEquals("Failure message", failureReasons[0].message)
    //            assertEquals("Other failure message", failureReasons[1].message)
    //        }
}
