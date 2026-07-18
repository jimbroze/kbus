package com.jimbroze.kbus.core.uow

internal class NonReturningUnitOfWork
internal constructor(
    private val delegate: UnitOfWork<Unit> = DefaultUnitOfWork<Unit>().apply { setReturningWork {} }
) : UnitOfWork<Unit> by delegate
