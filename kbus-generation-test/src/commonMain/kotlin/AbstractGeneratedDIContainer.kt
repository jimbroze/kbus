package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.CommandDependencies

abstract class AbstractGeneratedDIContainer : IContainer {
    override fun clockFactory(commandDependencies: CommandDependencies): ClockFactory =
        ClockFactory(this.clock, commandDependencies.domainEventPublisher)

    override fun clockFactoryHolder(): ContainsInstant =
        ContainsInstant(this.clockFactory(), commandDependencies.domainEventPublisher)
}

// first see if commanddeps are needed
// If dependency is commandDeps or subdependency of commandDeps, add a boolean to Dep data class?
//
// Do we ever need to add deps that don's use commandDeps?
