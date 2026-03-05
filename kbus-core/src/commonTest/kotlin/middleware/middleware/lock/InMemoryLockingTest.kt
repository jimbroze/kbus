package com.jimbroze.kbus.core.middleware.middleware.lock

import com.jimbroze.kbus.core.middleware.middleware.lock.inmemory.InMemoryAtomicSignallingLock
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

class InMemoryLockingTest : LockingTestBase() {
    override fun createAtomicLock(scope: CoroutineScope): SignallingLock =
        InMemoryAtomicSignallingLock(scope = scope)

    @Test
    fun verifies_concrete_lock_instance_type() = runTest {
        val lock = createAtomicLock(backgroundScope)
        assertNotNull(lock)
    }
}
