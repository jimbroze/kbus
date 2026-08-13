package com.jimbroze.kbus.generation.processors

import com.jimbroze.kbus.generation.provider.ContainerProcessorProvider
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
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
    fun `rejects a domain event handler that does not extend DomainEventHandler`() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.api.annotations.LoadMessageHandler
                import com.jimbroze.kbus.api.messages.event.EventHandler
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
    fun `rejects an event handler depending on what only a command's invocation holds`() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.api.annotations.LoadMessageHandler
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
    fun `generates compiling code for an accepted command handler`() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.api.annotations.LoadMessageHandler
                import com.jimbroze.kbus.api.messages.command.Command
                import com.jimbroze.kbus.api.messages.command.CommandHandler
                import com.jimbroze.kbus.api.result.BusResult
                import com.jimbroze.kbus.api.result.MessageFailure

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

    /**
     * A handler can name the typed commands its own module generates. That type does not exist when
     * the handler is first read, so a handler left deferred by it never reaches the generated
     * factory — silently, since nothing about it is an error.
     */
    @Test
    fun `accepts a handler asking for the typed commands its own module generates`() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.api.annotations.LoadMessageHandler
                import com.jimbroze.kbus.api.messages.command.Command
                import com.jimbroze.kbus.api.messages.command.CommandHandler
                import com.jimbroze.kbus.api.result.BusResult
                import com.jimbroze.kbus.api.result.MessageFailure
                import com.jimbroze.kbus.generated.DefaultCommands

                class PlaceOrder : Command<BusResult<String, MessageFailure>>()

                @LoadMessageHandler
                class PlaceOrderHandler :
                    CommandHandler<PlaceOrder, BusResult<String, MessageFailure>>() {
                    override suspend fun handle(message: PlaceOrder) = BusResult.success("ok")
                }

                class PlaceOrderForRegular : Command<BusResult<String, MessageFailure>>()

                @LoadMessageHandler
                class PlaceOrderForRegularHandler(private val commands: DefaultCommands) :
                    CommandHandler<PlaceOrderForRegular, BusResult<String, MessageFailure>>() {
                    override suspend fun handle(message: PlaceOrderForRegular) =
                        commands.placeOrder(PlaceOrder())
                }
                """
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertContains(
            result.sourcesGeneratedBySymbolProcessor
                .single { it.name == "DefaultHandlers.kt" }
                .readText(),
            "placeOrderForRegularHandler",
        )
    }

    @Test
    fun `rejects a bounded context identity that is only whitespace`() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.api.annotations.LoadMessageHandler
                import com.jimbroze.kbus.api.messages.command.Command
                import com.jimbroze.kbus.api.messages.command.CommandHandler
                import com.jimbroze.kbus.api.result.BusResult
                import com.jimbroze.kbus.api.result.MessageFailure

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

    @Test
    fun `rejects an event mapper that is not an object`() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.api.annotations.LoadEventMapper
                import com.jimbroze.kbus.api.messages.event.IntegrationEvent
                import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventMapper
                import com.jimbroze.kbus.domain.event.DomainEvent

                class OrderPlaced(val orderId: String) : DomainEvent()

                class OrderPlacedIntegration(val orderId: String) : IntegrationEvent()

                @LoadEventMapper
                class OrderPlacedMapper : IntegrationEventMapper<OrderPlaced> {
                    override fun fromDomainEvent(event: OrderPlaced) =
                        OrderPlacedIntegration(event.orderId)
                }
                """
            )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Only objects can be annotated with @LoadEventMapper")
    }

    @Test
    fun `rejects an annotated mapper that maps no domain event`() {
        val result =
            compile(
                """
                package com.example

                import com.jimbroze.kbus.api.annotations.LoadEventMapper

                @LoadEventMapper
                object OrderPlacedMapper
                """
            )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(
            result.messages,
            "Only IntegrationEventMapper implementations can be annotated with @LoadEventMapper",
        )
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
