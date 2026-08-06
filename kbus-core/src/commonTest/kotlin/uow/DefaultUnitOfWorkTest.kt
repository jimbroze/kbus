package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.core.fixtures.NonExecutingTransactionManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DefaultUnitOfWorkTest {
    @Test
    fun `runs the primary work it was given`() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()
        var executed = false

        unitOfWork.setReturningWork { executed = true }

        unitOfWork.execute()

        assertTrue(executed)
    }

    @Test
    fun `returns the result of the primary work`() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()
        unitOfWork.setReturningWork { "Noice one" }
        unitOfWork.addSecondaryWork { "Failed!" }
        unitOfWork.addPostCommitWork { "Also Failed!" }

        val result = unitOfWork.execute()

        assertEquals("Noice one", result)
    }

    @Test
    fun `runs primary then secondary then post-commit work in that order`() = runTest {
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
    fun `runs primary and secondary work inside the transaction`() = runTest {
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
    fun `has no active transaction manager until one is requested`() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()

        assertNull(unitOfWork.activeTransactionManager)

        unitOfWork.setReturningWork { "result" }
        unitOfWork.execute()

        assertNull(unitOfWork.activeTransactionManager)
    }

    @Test
    fun `exposes the transaction manager that was requested of it`() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()
        val transactionManager = NonExecutingTransactionManager()

        unitOfWork.useTransaction(transactionManager)

        assertSame(transactionManager, unitOfWork.activeTransactionManager)
    }

    @Test
    fun `runs post-commit work outside the transaction`() = runTest {
        val unitOfWork = DefaultUnitOfWork<Any?>()
        val executedWork = mutableListOf<String>()
        unitOfWork.useTransaction(NonExecutingTransactionManager())

        unitOfWork.addPostCommitWork { executedWork.add("postCommit") }

        unitOfWork.execute()

        assertContentEquals(listOf("postCommit"), executedWork)
    }
}
