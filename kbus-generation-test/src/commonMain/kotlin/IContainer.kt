package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.BusLocker
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.MessageBus
import kotlinx.datetime.Clock

interface IContainer {
    val messageBus: MessageBus

    val busLocker: BusLocker

    fun containsInstant(commandDependencies: CommandDependencies): ContainsInstant

    val containsString: ContainsString

    fun clockFactory(commandDependencies: CommandDependencies): ClockFactory

    val clock: Clock

    val typeAliasString: TypeAliasString

    val stringCombinator: StringCombinator
}
