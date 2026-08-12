package com.jimbroze.kbus.core.uow

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class EmptyTransactionManagerTest {
    @Test
    fun `runs the block it is given directly`() = runTest {
        var executed = false

        EmptyTransactionManager().execute { executed = true }

        assertTrue(executed)
    }
}
