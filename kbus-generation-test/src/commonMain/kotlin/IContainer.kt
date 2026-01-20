package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.middleware.middleware.BusLocker
import com.jimbroze.kbus.core.uow.CommandDependencies
import kotlinx.datetime.Clock

interface IContainer {
    val messageBus: MessageBus

    val busLocker: BusLocker

    fun containsInstant(
        commandDependencies: CommandDependencies
    ): RequiresCommandDepsContainsInstant

    val containsString: ContainsString

    fun clockFactory(commandDependencies: CommandDependencies): RequiresCommandDepsContainsClock

    val clock: Clock

    val typeAliasString: TypeAliasString

    val containsFunctions: ContainsFunctions
}

interface IHandlers {
    fun testGeneratorCommandHandler(
        commandDependencies: CommandDependencies
    ): TestGeneratorCommandHandler

    fun testDuplicateGeneratorCommandHandler(
        commandDependencies: CommandDependencies
    ): TestDuplicateGeneratorCommandHandler

    fun testGeneratorQueryHandler(): TestGeneratorQueryHandler
}
