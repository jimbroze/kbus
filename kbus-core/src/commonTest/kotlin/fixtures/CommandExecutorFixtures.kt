package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.contracts.uow.TransactionConfig
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependenciesFactory
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.uow.UnitOfWork

class TestCommandDependenciesFactory : CommandDependenciesFactory {
    var unitOfWork: UnitOfWork<*>? = null
    var commandDependencies: CommandDependencies? = null

    override fun create(invocation: CommandInvocation<*>): CommandDependencies {
        if (this.unitOfWork !== null) {
            error("Unit of work has already been set")
        }

        val commandDependencies = CommandDependencies(TestDomainEventPublisher())

        this.unitOfWork = invocation.unitOfWork
        this.commandDependencies = commandDependencies

        return commandDependencies
    }
}

fun <TResult> testCommandDependencies() =
    TestCommandDependenciesFactory().create(testInvocation<TResult>())

class DispatchingCommand : Command<BusResult<Unit, MessageFailure>>()

class DispatchingCommandHandler :
    CommandHandler<DispatchingCommand, BusResult<Unit, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? = null

    override suspend fun handle(message: DispatchingCommand): BusResult<Unit, MessageFailure> {
        publish(TestIntegrationEvent("test-event"))
        return BusResult.success(Unit)
    }
}

class TransactionCommand(val message: String) : Command<BusResult<String, MessageFailure>>()

class TransactionCommandHandler(transactionManager: TransactionManager? = null) :
    CommandHandler<TransactionCommand, BusResult<String, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? =
        TransactionConfig(transactionManagerOverride = transactionManager)

    override suspend fun handle(message: TransactionCommand): BusResult<String, MessageFailure> {
        return BusResult.success(message.message)
    }
}
