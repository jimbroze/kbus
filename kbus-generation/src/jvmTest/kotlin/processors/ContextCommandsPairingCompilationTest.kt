package com.jimbroze.kbus.generation.processors

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * The shape the generated bus emits, compiled for real: a handler creator asking for one context's
 * commands and a context that owns another's must not type-check. This is what makes a bus function
 * unable to name a context and a factory belonging to two different contexts, so it is checked
 * against the compiler rather than against emitted text.
 */
@OptIn(ExperimentalCompilerApi::class)
class ContextCommandsPairingCompilationTest {
    private val twoContexts =
        """
        package com.example

        import com.jimbroze.kbus.contracts.messages.command.Command
        import com.jimbroze.kbus.contracts.messages.command.CommandHandler
        import com.jimbroze.kbus.contracts.result.BusResult
        import com.jimbroze.kbus.contracts.result.MessageFailure
        import com.jimbroze.kbus.core.messages.command.CommandDependencies
        import com.jimbroze.kbus.core.messages.command.ContextCommands
        import com.jimbroze.kbus.core.messages.command.NestedCommandExecutor
        import com.jimbroze.kbus.core.module.CommandOwningContext
        import com.jimbroze.kbus.core.module.OwningContext

        class PlaceOrder : Command<BusResult<String, MessageFailure>>()

        class PlaceOrderHandler(private val commands: OrdersCommandExecutor) :
            CommandHandler<PlaceOrder, BusResult<String, MessageFailure>>() {
            override suspend fun handle(message: PlaceOrder) = BusResult.success("ok")
        }

        class OrdersCommandExecutor(private val nested: NestedCommandExecutor) : ContextCommands {
            override suspend fun <TCommand : Command<TResult>, TResult : BusResult<*, *>> execute(
                command: TCommand,
            ): TResult = nested.execute(command)
        }

        class InventoryCommandExecutor(private val nested: NestedCommandExecutor) : ContextCommands {
            override suspend fun <TCommand : Command<TResult>, TResult : BusResult<*, *>> execute(
                command: TCommand,
            ): TResult = nested.execute(command)
        }

        class OrdersContext(registeredContext: OwningContext) :
            OwningContext by registeredContext, CommandOwningContext<OrdersCommandExecutor> {
            override fun typedCommands(
                nestedCommandExecutor: NestedCommandExecutor,
            ): OrdersCommandExecutor = OrdersCommandExecutor(nestedCommandExecutor)
        }

        class InventoryContext(registeredContext: OwningContext) :
            OwningContext by registeredContext, CommandOwningContext<InventoryCommandExecutor> {
            override fun typedCommands(
                nestedCommandExecutor: NestedCommandExecutor,
            ): InventoryCommandExecutor = InventoryCommandExecutor(nestedCommandExecutor)
        }

        val ordersHandlerCreator =
            { _: CommandDependencies, ordersCommands: OrdersCommandExecutor ->
                PlaceOrderHandler(ordersCommands)
            }
        """

    @Test
    fun `accepts a handler creator paired with the context owning its commands`() {
        val result =
            compile(
                """
                $twoContexts

                suspend fun dispatch(
                    executor: com.jimbroze.kbus.core.messages.command.CommandExecutor,
                    orders: OrdersContext,
                ) = executor.execute(PlaceOrder(), orders, ordersHandlerCreator)
                """
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `rejects a handler creator paired with a context owning other commands`() {
        val result =
            compile(
                """
                $twoContexts

                suspend fun dispatch(
                    executor: com.jimbroze.kbus.core.messages.command.CommandExecutor,
                    inventory: InventoryContext,
                ) = executor.execute(PlaceOrder(), inventory, ordersHandlerCreator)
                """
            )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "OrdersCommandExecutor")
    }

    private fun compile(@Language("kotlin") source: String): JvmCompilationResult =
        KotlinCompilation()
            .apply {
                sources = listOf(SourceFile.kotlin("Contexts.kt", source.trimIndent()))
                inheritClassPath = true
                messageOutputStream = System.out
            }
            .compile()
}
