package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.core.messages.command.TestUnitOfWork
import com.jimbroze.kbus.core.registry.OtherPrintEventHandler
import com.jimbroze.kbus.core.registry.PrintEventHandler
import com.jimbroze.kbus.core.registry.StorageEvent
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class EventDispatcherTest {
    @Test
    fun test_it_dispatches_event_to_all_handlers() = runTest {
        val results = mutableListOf<String>()
        val dispatcher = EventDispatcher({ emptyList() }, emptyList())

        dispatcher.dispatch(
            StorageEvent("string", results),
            listOf(PrintEventHandler(), OtherPrintEventHandler("string")),
        )

        assertEquals("string", results[0])
        assertEquals("string", results[1])
    }

    @Test
    fun test_it_dispatches_domain_event_immediately_by_default() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers = listOf(TestDomainEventHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatch(TestDomainEvent("immediate"), unitOfWork)

        assertEquals(1, results.size)
        assertEquals("immediate", results[0])
    }

    @Test
    fun test_it_schedules_domain_event_for_after_primary_work() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(TestDispatchAfterPrimaryWorkHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatch(TestDomainEvent("after-primary"), unitOfWork)

        assertEquals(0, results.size)
        assertEquals(1, unitOfWork.secondaryWork.size)
        unitOfWork.secondaryWork[0].invoke()
        assertEquals(1, results.size)
        assertEquals("after-primary", results[0])
    }

    @Test
    fun test_it_schedules_domain_event_for_after_commit() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers = listOf(TestDispatchAfterCommitHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatch(TestDomainEvent("after-commit"), unitOfWork)

        assertEquals(0, results.size)
        assertEquals(1, unitOfWork.postCommitWork.size)
        unitOfWork.postCommitWork[0].invoke()
        assertEquals(1, results.size)
        assertEquals("after-commit", results[0])
    }

    @Test
    fun test_it_handles_multiple_domain_event_handlers_with_different_dispatch_strategies() =
        runTest {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            @Suppress("UNCHECKED_CAST")
            val handlers =
                listOf(
                    TestDomainEventHandler(results) as EventHandler<DomainEvent>,
                    TestDispatchAfterPrimaryWorkHandler(results) as EventHandler<DomainEvent>,
                    TestDispatchAfterCommitHandler(results) as EventHandler<DomainEvent>,
                )

            val dispatcher = EventDispatcher({ handlers }, emptyList())

            dispatcher.dispatch(TestDomainEvent("mixed"), unitOfWork)

            assertEquals(1, results.size)
            assertEquals("mixed", results[0])

            assertEquals(1, unitOfWork.secondaryWork.size)
            unitOfWork.secondaryWork[0].invoke()
            assertEquals(2, results.size)
            assertEquals("mixed", results[1])

            assertEquals(1, unitOfWork.postCommitWork.size)
            unitOfWork.postCommitWork[0].invoke()
            assertEquals(3, results.size)
            assertEquals("mixed", results[2])
        }
}

class TestDomainEvent(val data: String) : DomainEvent()

class TestDomainEventHandler(private val results: MutableList<String>) :
    DomainEventHandler<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        results.add(message.data)
    }
}

class TestDispatchAfterPrimaryWorkHandler(private val results: MutableList<String>) :
    DispatchAfterPrimaryWork<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        results.add(message.data)
    }
}

class TestDispatchAfterCommitHandler(private val results: MutableList<String>) :
    DispatchAfterCommit<TestDomainEvent>() {
    override suspend fun handle(message: TestDomainEvent) {
        results.add(message.data)
    }
}
