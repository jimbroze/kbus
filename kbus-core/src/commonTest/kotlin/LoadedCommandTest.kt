package com.jimbroze.kbus.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class UnloadedCommand(val messageData: String) : Command()

class UnloadedCommandHandler(val clock: Clock) : CommandHandler<UnloadedCommand, Any, FailureReason> {
    override suspend fun handle(message: UnloadedCommand): BusResult<Any, FailureReason> {
        return success(message.messageData)
    }
}

// Simulated generated code

class UnloadedCommandLoaded(messageData: String) {
    val command = UnloadedCommand(messageData)

    suspend fun handle(handler: UnloadedCommandHandler) = handler.handle(command)
}

interface GeneratedDependencies {
    fun getClock(): Clock
}
class CompileTimeGeneratedLoader(private val dependencies: GeneratedDependencies) {
    fun getUnloadedCommandHandler(): UnloadedCommandHandler {
        return UnloadedCommandHandler(this.dependencies.getClock())
    }
    fun getReturnCommandHandler(): ReturnCommandHandler {
        return ReturnCommandHandler()
    }
}

class CompileTimeLoadedMessageBus(
    middleware: List<Middleware>,
    private val loader: CompileTimeGeneratedLoader,
) : MessageBus(middleware) {
    suspend fun execute(loadedCommand: UnloadedCommandLoaded): BusResult<Any, FailureReason> {
        val handler: UnloadedCommandHandler = this.loader.getUnloadedCommandHandler()
        return this.execute(loadedCommand.command, handler)
    }
}

class LoadedCommandTest {
    @Test
    fun test_execute_executes_a_command() = runTest {
        class Dependencies : GeneratedDependencies {
            override fun getClock(): Clock = Clock.System

        }

        val bus = CompileTimeLoadedMessageBus(
            emptyList(),
            CompileTimeGeneratedLoader(Dependencies())
        )

        val result = bus.execute(UnloadedCommandLoaded("Test the load"))

        assertEquals("Test the load", result.getOrNull())
    }
}
