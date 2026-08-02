package com.jimbroze.kbus.generation.processors

import com.jimbroze.kbus.generation.provider.ContainerProcessorProvider
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * The processor run over the source a user would have written, and the real compiler run over what
 * it generated. A rejection is only worth having if it reaches the author, so these check the
 * message as well as the failure.
 *
 * An accepted *event* handler cannot be checked this way: its generated `.loaded` property is
 * annotated for every platform the framework targets, which a single-platform compilation rejects.
 * The example modules cover that, through a real multiplatform build.
 */
@OptIn(ExperimentalCompilerApi::class)
class ProcessorCompilationTest {
    @Test
    fun aDomainEventsHandlerMustExtendDomainEventHandler() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
                import com.jimbroze.kbus.contracts.messages.event.EventHandler
                import com.jimbroze.kbus.domain.event.DomainEvent

                class OrderPlaced : DomainEvent()

                @LoadMessageHandler
                class NotifyWarehouse : EventHandler<OrderPlaced> {
                    override suspend fun handle(message: OrderPlaced) = Unit
                }
                """
            )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "com.example.NotifyWarehouse")
        assertContains(result.messages, "must extend DomainEventHandler")
    }

    @Test
    fun anEventHandlerCannotDependOnWhatOnlyACommandsInvocationHolds() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
                import com.jimbroze.kbus.core.messages.command.NestedCommandExecutor
                import com.jimbroze.kbus.domain.event.DomainEvent
                import com.jimbroze.kbus.domain.event.DomainEventHandler

                class OrderPlaced : DomainEvent()

                @LoadMessageHandler
                class ReserveStockOnOrder(private val commandExecutor: NestedCommandExecutor) :
                    DomainEventHandler<OrderPlaced>() {
                    override suspend fun handle(message: OrderPlaced) = Unit
                }
                """
            )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "com.example.ReserveStockOnOrder")
        assertContains(result.messages, "only a command's own invocation can supply")
    }

    @Test
    fun anAcceptedCommandHandlersGeneratedCodeCompiles() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
                import com.jimbroze.kbus.contracts.messages.command.Command
                import com.jimbroze.kbus.contracts.messages.command.CommandHandler
                import com.jimbroze.kbus.contracts.result.BusResult
                import com.jimbroze.kbus.contracts.result.MessageFailure

                class PlaceOrder : Command<BusResult<String, MessageFailure>>()

                @LoadMessageHandler
                class PlaceOrderHandler :
                    CommandHandler<PlaceOrder, BusResult<String, MessageFailure>>() {
                    override suspend fun handle(message: PlaceOrder) = BusResult.success("ok")
                }
                """
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun aBoundedContextIdentityOfBlankWhitespaceIsRejected() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
                import com.jimbroze.kbus.contracts.messages.command.Command
                import com.jimbroze.kbus.contracts.messages.command.CommandHandler
                import com.jimbroze.kbus.contracts.result.BusResult
                import com.jimbroze.kbus.contracts.result.MessageFailure

                class PlaceOrder : Command<BusResult<String, MessageFailure>>()

                @LoadMessageHandler
                class PlaceOrderHandler :
                    CommandHandler<PlaceOrder, BusResult<String, MessageFailure>>() {
                    override suspend fun handle(message: PlaceOrder) = BusResult.success("ok")
                }
                """,
                boundedContextIdentity = "   ",
            )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "names no bounded context")
    }

    private fun compile(
        @Language("kotlin") source: String,
        boundedContextIdentity: String? = null,
    ): JvmCompilationResult =
        KotlinCompilation()
            .apply {
                sources = listOf(SourceFile.kotlin("Handlers.kt", source.trimIndent()))
                inheritClassPath = true
                messageOutputStream = System.out
                configureKsp {
                    symbolProcessorProviders.add(ContainerProcessorProvider())
                    processorOptions["kbus.indexPackage"] = "com.example.indexes"
                    boundedContextIdentity?.let {
                        processorOptions["kbus.boundedContextIdentity"] = it
                    }
                }
            }
            .compile()
}
