package com.jimbroze.kbus.core

import com.jimbroze.kbus.core.BusResult.Companion.success
import kotlinx.datetime.Clock

class UnloadedCommand(val messageData: String) : Command<BusResult<Any, MessageFailure>>()

class UnloadedCommandHandler(val clock: Clock) :
    CommandHandler<UnloadedCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: UnloadedCommand): BusResult<Any, MessageFailure> {
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

// class CompileTimeLoadedMessageBus(
//    handlerLocator: HandlerLocator = PersistingHandlerLocator(),
//    private val loader: CompileTimeGeneratedLoader,
//    middleware: List<Middleware> = emptyList(),
// ) : MessageBus(handlerLocator, middleware) {
//    suspend fun execute(loadedCommand: UnloadedCommandLoaded): BusResult<Any, MessageFailure> {
//        val handler: UnloadedCommandHandler = this.loader.getUnloadedCommandHandler()
//        return this.execute(loadedCommand.command, handler)
//    }
// }
//
// class LoadedCommandTest {
//    @Test
//    fun test_execute_executes_a_command() = runTest {
//        class Dependencies : GeneratedDependencies {
//            override fun getClock(): Clock = Clock.System
//        }
//
//        val bus = CompileTimeLoadedMessageBus(loader = CompileTimeGeneratedLoader(Dependencies()))
//
//        val result = bus.execute(UnloadedCommandLoaded("Test the load"))
//
//        assertEquals("Test the load", result.getOrNull())
//    }
// }
