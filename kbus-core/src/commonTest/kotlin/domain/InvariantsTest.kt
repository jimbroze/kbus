package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.core.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class InvalidInvariantExample(message: String) : InvalidInvariantException(message)

open class InvalidInvariantsCommand(
    val exception: InvalidInvariantException,
) : Command()

class InvalidInvariantsCatchingCommand(
    exception: InvalidInvariantException,
) : InvalidInvariantsCommand(exception), InvariantCatchingMessage

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

        assertFailsWith<InvalidInvariantException>("Failure message"){
            catcher.handle(
                InvalidInvariantsCatchingCommand(
                    InvalidInvariantException("Failure message")
                )
            ) {
                InvalidInvariantsCommandHandler().handle(it)
            }
        }
    }

    @Test
    fun invariant_catcher_converts_invalid_invariant_exception_subclass_to_result_failure() = runTest {
        val catcher = InvalidInvariantCatcher()

        assertFailsWith<InvalidInvariantException>("Failure message"){
            catcher.handle(
                InvalidInvariantsCatchingCommand(
                    InvalidInvariantExample("Failure message")
                )
            ) {
                InvalidInvariantsCommandHandler().handle(it)
            }
        }
    }
}
