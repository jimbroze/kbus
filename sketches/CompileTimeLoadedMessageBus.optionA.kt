// SKETCH ONLY — Option A, the generated side. Lives outside every source set, so it is never
// compiled. Trimmed to three contexts and two commands; the real generator emits ~20 functions.
//
// Compare with the real output at:
//   kbus-example/build/generated/ksp/metadata/commonMain/kotlin/
//       com/jimbroze/kbus/generated/CompileTimeLoadedMessageBus.kt

package com.jimbroze.kbus.generated

public class CompileTimeLoadedMessageBus private constructor(
  private val defaultHandlerFactory: DefaultHandlerFactory,
  private val inventoryHandlerFactory: InventoryHandlerFactory,
  private val ordersHandlerFactory: OrdersHandlerFactory,
  transactionManager: TransactionManager,
  middleware: List<Middleware>,
  appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
  outbox: OutboxConfig? = null,
  inboxTuning: InboxTuning? = null,
  default: ContextConfig = ContextConfig(),
  inventory: ContextConfig = ContextConfig(),
  orders: ContextConfig = ContextConfig(),
) : BaseMessageBus<CompileTimeLoadedMessageBus.Contexts>(
  // Captures constructor arguments only — never `this`, which is not initialised yet.
  buildContexts = { runtimeFactory ->
    Contexts(
      defaultHandlerFactory, inventoryHandlerFactory, ordersHandlerFactory,
      runtimeFactory, default, inventory, orders,
    )
  },
  transactionManager = transactionManager,
  middlewares = middleware,
  appScope = appScope,
  outbox = outbox,
  inboxTuning = inboxTuning,
) {
  // GONE, compared with today's generated bus:
  //   private val contexts: Contexts                      — now `protected val contexts` on the base
  //   private val defaultOwningContext: OwningContext     — owningContextFor(BoundedContextId.DEFAULT)
  //   private val inventoryOwningContext: OwningContext   — owningContextFor(BoundedContextId("inventory"))
  //   private val ordersOwningContext: OwningContext      — owningContextFor(BoundedContextId("orders"))
  //   the second private constructor that existed only to build Contexts before delegating

  public constructor(
    loader: AllDependencies,
    transactionManager: TransactionManager,
    middleware: List<Middleware>,
    appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    outbox: OutboxConfig? = null,
    inboxTuning: InboxTuning? = null,
    default: ContextConfig = ContextConfig(),
    inventory: ContextConfig = ContextConfig(),
    orders: ContextConfig = ContextConfig(),
  ) : this(
    DefaultHandlerFactory(loader), InventoryHandlerFactory(loader), OrdersHandlerFactory(loader),
    transactionManager, middleware, appScope, outbox, inboxTuning, default, inventory, orders,
  )

  public suspend fun execute(command: PlaceOrder): BusResult<Order, MessageFailure> {
    val handlerCreator = { commandDependencies: CommandDependencies ->
      ordersHandlerFactory.placeOrderHandler(commandDependencies)
    }
    // Was: commandExecutor.execute(command, ordersOwningContext, handlerCreator)
    return commandExecutor.execute(command, contexts.orders, handlerCreator)
  }

  public suspend fun execute(command: ReserveStock): BusResult<Unit, MessageFailure> {
    val handlerCreator = { commandDependencies: CommandDependencies ->
      inventoryHandlerFactory.reserveStockHandler(commandDependencies)
    }
    return commandExecutor.execute(command, contexts.inventory, handlerCreator)
  }

  /**
   * Each context is built here and nowhere else. Building one registers it on the bus — the
   * factory records what it hands back — so there is no `all` list to keep in step, and no way to
   * declare a context the bus does not know about.
   */
  public class Contexts internal constructor(
    defaultHandlerFactory: DefaultHandlerFactory,
    inventoryHandlerFactory: InventoryHandlerFactory,
    ordersHandlerFactory: OrdersHandlerFactory,
    runtimeFactory: ContextRuntimeFactory,
    default: ContextConfig,
    inventory: ContextConfig,
    orders: ContextConfig,
  ) {
    public val default: OwningContext = runtimeFactory.runtimeFor(
      BoundedContext(
        BoundedContextId.DEFAULT,
        GenerationHandlerLocator(defaultHandlerFactory),
        default.inbox,
        default.subscriptions,
      )
    )

    public val inventory: OwningContext = runtimeFactory.runtimeFor(
      BoundedContext(
        BoundedContextId("inventory"),
        GenerationHandlerLocator(inventoryHandlerFactory),
        inventory.inbox,
        inventory.subscriptions,
      )
    )

    public val orders: OwningContext = runtimeFactory.runtimeFor(
      BoundedContext(
        BoundedContextId("orders"),
        GenerationHandlerLocator(ordersHandlerFactory),
        orders.inbox,
        orders.subscriptions,
      )
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Generator changes this implies, in kbus-generation (not sketched):
//
//   BusGenerator
//     - buildOwningContextProperties() deleted; owningContextPropertyName() deleted
//     - buildHandlerFunction() emits `contexts.orders` instead of `ordersOwningContext`
//     - superclass becomes BaseMessageBus<Bus.Contexts>; addSuperclassConstructorParameter emits
//       the buildContexts lambda instead of `contexts = contexts.all`
//     - the Contexts-building private constructor disappears; the handler-factory params move onto
//       the remaining private constructor
//
//   buildContextsClass()
//     - takes a ContextRuntimeFactory parameter
//     - per-context: one public `OwningContext` property replacing the private locator + config
//       pair, and buildContextListProperty() (`internal val all`) is deleted
//
// Open question the sketch does not answer: `class Bus : BaseMessageBus<Bus.Contexts>` — a nested
// class named in its own outer class's supertype argument. Should be legal (nested, not inner),
// but compile it before building anything on top.
