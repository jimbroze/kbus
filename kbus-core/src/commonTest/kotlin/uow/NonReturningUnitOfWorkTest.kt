package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.core.fixtures.TestTransactionManager
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class NonReturningUnitOfWorkTest {
    @Test
    fun test_execute_delegates_to_underlying_unit_of_work() = runTest {
        val delegate = TestUnitOfWork<Unit>()
        delegate.setReturningWork {}
        val uow = NonReturningUnitOfWork(delegate)

        uow.execute()

        assertEquals(1, delegate.executedWork.size)
    }

    @Test
    fun test_setReturningWork_delegates_to_underlying_unit_of_work() = runTest {
        val delegate = TestUnitOfWork<Unit>()
        val uow = NonReturningUnitOfWork(delegate)
        var executed = false

        uow.setReturningWork { executed = true }
        delegate.primaryWork()

        assertEquals(true, executed)
    }

    @Test
    fun test_addSecondaryWork_delegates_to_underlying_unit_of_work() = runTest {
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
    fun test_addPostCommitWork_delegates_to_underlying_unit_of_work() = runTest {
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
    fun test_execute_succeeds_without_calling_setReturningWork() = runTest {
        val uow = NonReturningUnitOfWork()

        uow.execute()
    }

    @Test
    fun test_useTransaction_delegates_to_underlying_unit_of_work() {
        val delegate = TestUnitOfWork<Unit>()
        delegate.setReturningWork {}
        val uow = NonReturningUnitOfWork(delegate)
        val transactionManager = TestTransactionManager()

        uow.useTransaction(transactionManager)

        assertSame(transactionManager, delegate.activeTransactionManager)
    }
}
