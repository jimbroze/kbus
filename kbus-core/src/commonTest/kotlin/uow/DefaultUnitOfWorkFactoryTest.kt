package com.jimbroze.kbus.core.uow

import kotlin.test.Test
import kotlin.test.assertIs

class DefaultUnitOfWorkFactoryTest {
    @Test
    fun `builds a default unit of work`() {
        assertIs<DefaultUnitOfWork<Any?>>(DefaultUnitOfWorkFactory().create<Any?>())
    }
}
