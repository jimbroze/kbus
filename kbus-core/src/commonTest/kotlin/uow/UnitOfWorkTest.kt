package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.NonExecutingTransactionManager
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestDomainEventDispatcher
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private class OutboxUowTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class UnitOfWorkTest {
    @Test
    fun test_it_executes_primary_work_without_setting_transaction_manager() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()
        var executed = false

        unitOfWork.setReturningWork { executed = true }

        unitOfWork.execute()

        assertTrue(executed)
    }

    @Test
    fun test_execute_returns_result_of_primary_work() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()
        unitOfWork.setReturningWork { "Noice one" }
        unitOfWork.addSecondaryWork { "Failed!" }
        unitOfWork.addPostCommitWork { "Also Failed!" }

        val result = unitOfWork.execute()

        assertEquals("Noice one", result)
    }

    @Test
    fun test_it_executes_primary_then_secondary_then_post_commit() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()
        val executionOrder = mutableListOf<String>()

        unitOfWork.addPostCommitWork { executionOrder.add("postCommit1") }
        unitOfWork.addSecondaryWork { executionOrder.add("secondary1") }
        unitOfWork.addPostCommitWork { executionOrder.add("postCommit2") }
        unitOfWork.setReturningWork {
            executionOrder.add("primary")
            "result"
        }
        unitOfWork.addSecondaryWork { executionOrder.add("secondary2") }

        unitOfWork.execute()

        assertContentEquals(
            listOf("primary", "secondary1", "secondary2", "postCommit1", "postCommit2"),
            executionOrder,
        )
    }

    @Test
    fun test_execute_runs_primary_and_secondary_work_in_transaction() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()
        val executedWork = mutableListOf<String>()
        unitOfWork.useTransaction(NonExecutingTransactionManager())

        unitOfWork.setReturningWork { executedWork.add("primary") }
        unitOfWork.addSecondaryWork { executedWork.add("secondary1") }
        unitOfWork.addSecondaryWork { executedWork.add("secondary2") }

        unitOfWork.execute()

        assertEquals(0, executedWork.size)
    }

    @Test
    fun test_execute_runs_post_commit_work_outside_transaction() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()
        val executedWork = mutableListOf<String>()
        unitOfWork.useTransaction(NonExecutingTransactionManager())

        unitOfWork.addPostCommitWork { executedWork.add("postCommit") }

        unitOfWork.execute()

        assertContentEquals(listOf("postCommit"), executedWork)
    }

    @Test
    fun test_UnitOfWorkDomainEventPublisher_publishes_to_base_dispatcher_with_unit_of_work() =
        runTest {
            val testDispatcher = TestDomainEventDispatcher()

            val unitOfWork = DefaultUnitOfWork<Any?>()
            val publisher = UnitOfWorkDomainEventPublisher(testDispatcher, unitOfWork)
            val testEvent = object : DomainEvent() {}

            publisher.publish(testEvent)

            assertContentEquals(
                listOf(Pair(testEvent, unitOfWork)),
                testDispatcher.dispatchedEvents,
            )
        }

    @Test
    fun test_EmptyTransactionManager_executes_block_directly() = runTest {
        val transactionManager = EmptyTransactionManager()
        var executed = false

        transactionManager.execute { executed = true }

        assertTrue(executed)
    }

    @Test
    fun test_DefaultUnitOfWorkFactory_creates_new_UnitOfWork_instance() {
        val factory = DefaultUnitOfWorkFactory()
        val uow = factory.create<Any?>()
        assertIs<DefaultUnitOfWork<Any?>>(uow)
    }

    @Test
    fun test_execute_drains_the_outbox_after_post_commit_work_completes() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this, true)
        val unitOfWork = DefaultUnitOfWork<Any?>(outbox)
        val executionOrder = mutableListOf<String>()

        unitOfWork.setReturningWork { executionOrder.add("primary") }
        unitOfWork.addPostCommitWork {
            executionOrder.add("postCommit")
            outbox.publish(listOf(OutboxUowTestEvent("from-post-commit")))
        }

        unitOfWork.execute()
        advanceUntilIdle()

        assertContentEquals(listOf("primary", "postCommit"), executionOrder)
        assertEquals(
            listOf("from-post-commit"),
            realPublisher.publishedEvents.flatten().map { (it as OutboxUowTestEvent).name },
        )
    }

    @Test
    fun test_execute_does_not_drain_the_outbox_when_the_transaction_throws() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this, true)
        val unitOfWork = DefaultUnitOfWork<Any?>(outbox)

        unitOfWork.setReturningWork {
            outbox.publish(listOf(OutboxUowTestEvent("captured")))
            error("primary work failed")
        }

        assertFailsWith<IllegalStateException> { unitOfWork.execute() }
        advanceUntilIdle()

        assertTrue(realPublisher.publishedEvents.isEmpty())
    }

    @Test
    fun test_execute_exposes_the_outbox_as_the_transactionOutbox() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, true)
        val unitOfWork = DefaultUnitOfWork<Any?>(outbox)

        assertEquals(outbox, unitOfWork.transactionOutbox)
    }

    @Test
    fun test_execute_has_a_null_transactionOutbox_without_an_outbox() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()

        assertEquals(null, unitOfWork.transactionOutbox)
    }
}
