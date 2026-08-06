package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.core.fixtures.TestTransactionManager
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class NonReturningUnitOfWorkTest {
    @Test
    fun `runs the primary work through the unit of work it wraps`() = runTest {
        val delegate = TestUnitOfWork<Unit>()
        delegate.setReturningWork {}
        val uow = NonReturningUnitOfWork(delegate)

        uow.execute()

        assertEquals(1, delegate.executedWork.size)
    }

    @Test
    fun `sets returning work on the unit of work it wraps`() = runTest {
        val delegate = TestUnitOfWork<Unit>()
        val uow = NonReturningUnitOfWork(delegate)
        var executed = false

        uow.setReturningWork { executed = true }
        delegate.primaryWork()

        assertEquals(true, executed)
    }

    @Test
    fun `adds secondary work to the unit of work it wraps`() = runTest {
        val delegate = TestUnitOfWork<Unit>()
        delegate.setReturningWork {}
        val uow = NonReturningUnitOfWork(delegate)
        var executed = false

        uow.addSecondaryWork { executed = true }

        assertEquals(1, delegate.secondaryWork.size)
        delegate.secondaryWork.first().invoke()
        assertEquals(true, executed)
    }

    @Test
    fun `adds post-commit work to the unit of work it wraps`() = runTest {
        val delegate = TestUnitOfWork<Unit>()
        delegate.setReturningWork {}
        val uow = NonReturningUnitOfWork(delegate)
        var executed = false

        uow.addPostCommitWork { executed = true }

        assertEquals(1, delegate.postCommitWork.size)
        delegate.postCommitWork.first().invoke()
        assertEquals(true, executed)
    }

    @Test
    fun `completes without any returning work being set`() = runTest {
        val uow = NonReturningUnitOfWork()

        uow.execute()
    }

    @Test
    fun `requests a transaction from the unit of work it wraps`() {
        val delegate = TestUnitOfWork<Unit>()
        delegate.setReturningWork {}
        val uow = NonReturningUnitOfWork(delegate)
        val transactionManager = TestTransactionManager()

        uow.useTransaction(transactionManager)

        assertSame(transactionManager, delegate.activeTransactionManager)
    }
}
