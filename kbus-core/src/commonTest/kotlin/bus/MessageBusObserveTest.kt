package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private class ObservedEvent(val name: String) : IntegrationEvent()

private class PublishObservedEventCommand(val message: String) :
    Command<BusResult<Unit, MessageFailure>>()

private class PublishObservedEventCommandHandler :
    CommandHandler<PublishObservedEventCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(
        message: PublishObservedEventCommand
    ): BusResult<Unit, MessageFailure> {
        publish(ObservedEvent(message.message))
        return BusResult.success(Unit)
    }
}

private class RecordingObservedEventHandler(private val received: MutableList<String>) :
    IntegrationEventHandler<ObservedEvent> {
    override suspend fun handle(message: ObservedEvent) {
        received.add(message.name)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageBusObserveTest {
    private suspend fun realDelay(millis: Long) =
        withContext(Dispatchers.Default) { delay(millis.milliseconds) }

    private fun registerPublishingCommand(stores: HandlerFactoryStoreCollection) {
        stores.commandStore.registerHandlers(
            PublishObservedEventCommand::class,
            listOf(
                CommandHandlerFactory(PublishObservedEventCommandHandler::class) {
                    PublishObservedEventCommandHandler()
                }
            ),
        )
    }

    @Test
    fun observe_receivesAnEventPublishedThroughTheBus_exactlyOnce_alongsideItsHandler() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)
        val handled = mutableListOf<String>()
        stores.eventStore.registerHandlers(
            ObservedEvent::class,
            listOf(
                EventHandlerFactory(RecordingObservedEventHandler::class) {
                    RecordingObservedEventHandler(handled)
                }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            ObservedEvent::class,
            listOf(RecordingObservedEventHandler::class),
        )
        val bus = MessageBus(locator)

        val observed = mutableListOf<ObservedEvent>()
        val job = launch { bus.observe<ObservedEvent>().take(1).toList(observed) }
        yield()

        bus.execute(PublishObservedEventCommand("observed"))
        advanceUntilIdle()
        realDelay(100)
        job.join()

        assertEquals(listOf("observed"), observed.map { it.name })
        assertEquals(listOf("observed"), handled)
    }

    @Test
    fun observe_stillReceivesAnEventWithNoRegisteredHandlers() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)
        val bus = MessageBus(locator)

        val observed = mutableListOf<ObservedEvent>()
        val job = launch { bus.observe<ObservedEvent>().take(1).toList(observed) }
        yield()

        bus.execute(PublishObservedEventCommand("no-handlers"))
        advanceUntilIdle()
        job.join()

        assertEquals(listOf("no-handlers"), observed.map { it.name })
    }
}
