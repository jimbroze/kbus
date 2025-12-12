package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.CommandDependencies

abstract class AbstractGeneratedDIContainer : IContainer {
    override fun clockFactory(commandDependencies: CommandDependencies): ClockFactory =
        ClockFactory(this.clock, commandDependencies.domainEventPublisher)
}
