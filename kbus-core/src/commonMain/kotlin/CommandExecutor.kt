package com.jimbroze.kbus.core

class CommandExecutor(
    private val transactionManager: TransactionManager?,
    private val middlewares: List<Middleware>,
    private val busAccess: BusAccess,
    private val commandDependenciesFactory: CommandDependenciesFactory,
    private val unitOfWorkFactory: UnitOfWorkFactory = DefaultUnitOfWorkFactory(),
) {
    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> execute(
        command: TCommand,
        createHandler: (CommandDependencies) -> CommandHandler<TCommand, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        val unitOfWork = unitOfWorkFactory.create()
        val handler = createHandler(commandDependenciesFactory.create(unitOfWork))

        handler.setBus(busAccess)

        val finalHandler: suspend (TCommand) -> Any? = { message: TCommand ->
            executeInUnitOfWork(message, handler, unitOfWork)
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        return execute(command) as BusResult<TReturn, TFailure>
    }

    private suspend fun <TCommand : Command> executeInUnitOfWork(
        message: TCommand,
        handler: CommandHandler<TCommand, *, *>,
        unitOfWork: UnitOfWork,
    ): Any? {
        unitOfWork.setReturningWork { handler.handle(message) }

        if (handler is ExecuteInTransaction<*>) {
            unitOfWork.useTransaction(
                handler.transactionManager
                    ?: transactionManager
                    ?: error("Transaction Manager has not been set")
            )
        }

        return unitOfWork.execute()
    }
}

interface CommandDependenciesFactory {
    fun create(unitOfWork: UnitOfWork): CommandDependencies
}

class DefaultCommandDependenciesFactory(private val domainEventDispatcher: DomainEventDispatcher?) :
    CommandDependenciesFactory {
    override fun create(unitOfWork: UnitOfWork): CommandDependencies {
        return CommandDependencies(
            UnitOfWorkDomainEventPublisher(domainEventDispatcher, unitOfWork)
        )
    }
}
