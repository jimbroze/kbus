package com.jimbroze.kbus.core.uow

internal class NonReturningUnitOfWork internal constructor(private val delegate: UnitOfWork<Unit>) :
    UnitOfWork<Unit> by delegate
