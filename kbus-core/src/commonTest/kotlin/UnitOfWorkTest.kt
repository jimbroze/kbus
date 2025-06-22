package com.jimbroze.kbus.core

import com.jimbroze.kbus.core.domain.DomainEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class UnitOfWorkTest {
    @Test
    fun test_it_executes_primary_work_without_setting_transaction_manager() = runTest {
        val unitOfWork = UnitOfWorkImpl()
        var executed = false

        unitOfWork.setReturningWork { executed = true }

        unitOfWork.execute()

        assertTrue(executed)
    }

    @Test
    fun test_execute_returns_result_of_primary_work() = runTest {
        val unitOfWork = UnitOfWorkImpl()
        unitOfWork.setReturningWork { "Noice one" }
        unitOfWork.addSecondaryWork { "Failed!" }
        unitOfWork.addPostCommitWork { "Also Failed!" }

        val result = unitOfWork.execute()

        assertEquals("Noice one", result)
    }

    @Test
    fun test_it_executes_primary_then_secondary_then_post_commit() = runTest {
        val unitOfWork = UnitOfWorkImpl()
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
        val unitOfWork = UnitOfWorkImpl()
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
        val unitOfWork = UnitOfWorkImpl()
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

            val unitOfWork = UnitOfWorkImpl()
            val publisher = UnitOfWorkDomainEventPublisher(testDispatcher, unitOfWork)
            val testEvent = object : DomainEvent() {}

            publisher.dispatch(testEvent)

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
        val factory =
            object : UnitOfWorkFactory {
                override fun create(): UnitOfWork = UnitOfWorkImpl()
            }
        val uow = factory.create()
        assertIs<UnitOfWorkImpl>(uow)
    }
}
