# KBUS

A Kotlin Multiplatform CQRS message bus framework. Route Commands, Queries, and Events to their handlers with a
composable middleware pipeline, Unit of Work support, and optional KSP code generation for compile-time type-safe
handler resolution (zero reflection).

## Features

- **CQRS message routing** — Commands, Queries, and Events each with dedicated handler types
- **Kotlin Multiplatform** — JVM, JS, WASM, macOS, iOS, Linux, Windows
- **Coroutine-first** — All handlers are `suspend` functions
- **KSP code generation** — Compile-time handler resolution with zero reflection
- **Middleware pipeline** — Composable middleware chain wrapping handler execution
- **Unit of Work** — Transaction-aware command execution with domain and integration events
- **Domain modeling** — Built-in support for Entities, Aggregate Roots, and Value Objects
- **Result types** — Type-safe `BusResult<TValue, TMessageFailure>` with `Success` and `Failure` variants

## Installation

Add the dependencies to your `build.gradle.kts`:

```groovy
dependencies {
    implementation("com.jimbroze:kbus-core:<version>")

    // For KSP code generation (optional)
    implementation("com.jimbroze:kbus-annotations:<version>")
    ksp("com.jimbroze:kbus-generation:<version>")
}
```

## Quick Start

### Define Messages and Handlers

<!--- INCLUDE
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
-->

```kotlin
// A command that returns a String result
class CreateUser(val name: String, val email: String) :
    Command<BusResult<String, MessageFailure>>()

class CreateUserHandler :
    CommandHandler<CreateUser, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: CreateUser): BusResult<String, MessageFailure> {
        // Create the user...
        return BusResult.success("User ${message.name} created")
    }
}

// A query that returns a String result
class GetUser(val id: Int) :
    Query<BusResult<String, MessageFailure>>()

class GetUserHandler :
    QueryHandler<GetUser, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: GetUser): BusResult<String, MessageFailure> {
        // Look up the user...
        return BusResult.success("User #${message.id}")
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-messages-01.kt).

### Create the Bus and Dispatch Messages

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import com.jimbroze.kbus.example.fixtures.CreateUser
import com.jimbroze.kbus.example.fixtures.CreateUserHandler
import com.jimbroze.kbus.example.fixtures.GetUser
import com.jimbroze.kbus.example.fixtures.GetUserHandler
-->

```kotlin
suspend fun main() {
    // Register handlers
    val stores = HandlerFactoryStoreCollection()
    stores.commandStore.registerHandlers(
        CreateUser::class,
        listOf(CommandHandlerFactory(CreateUserHandler::class) { _: CommandDependencies -> CreateUserHandler() })
    )
    stores.queryStore.registerHandlers(
        GetUser::class,
        listOf(QueryHandlerFactory(GetUserHandler::class) { GetUserHandler() })
    )

    // Create the bus
    val bus = MessageBus(PersistingHandlerLocator(stores))

    // Execute a command
    val result = bus.execute(CreateUser("Alice", "alice@example.com"))
    if (result.isSuccess) {
        println(result.getOrNull()) // "User Alice created"
    }

    // Fetch a query
    val userResult = bus.fetch(GetUser(1))
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-bus-01.kt).

## Message Types

KBUS has three message types, each with a corresponding handler:

| Message            | Handler          | Cardinality                 | Returns                      | Purpose                                                  |
|--------------------|------------------|-----------------------------|------------------------------|----------------------------------------------------------|
| `Command<TResult>` | `CommandHandler` | One handler per command     | Yes (minimal data suggested) | State-modifying operations, executes within Unit of Work |
| `Query<TResult>`   | `QueryHandler`   | One handler per query       | Yes                          | Read-only operations                                     |
| `Event`            | `EventHandler`   | Multiple handlers per event | No                           | Notifications, side effects, eventual consistency        |

### Events

Events support multiple handlers and come in two flavors:

#### Domain Events

Domain events dispatch relative to the Unit of Work lifecycle. Publish them from a domain object by taking a
`DomainEventPublisher` as a constructor dependency. See the section on // TODO

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventPublisher
-->

```kotlin
class OrderShipped(val orderId: String) : DomainEvent()

class Order(private val domainEventPublisher: DomainEventPublisher) {

    suspend fun place(orderId: String) {
        // Place the order...

        domainEventPublisher.publish(OrderShipped(orderId))
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-domain-events-01.kt).

`CommandDependencies` (which contains `DomainEventPublisher`) is injected into command handlers automatically and routes
events through the Unit of Work.

Event handlers are constructed with dependencies too, but only from what an event dispatch can supply: an
`IntegrationEventPublisher` and nothing command-scoped. An event handler declaring a `NestedCommandExecutor` or a
`DomainEventPublisher` fails generation, because no command's invocation reached it to provide one.

### Event Dispatch & Error Strategy Matrix

The safety of an error strategy depends entirely on **when** the handler executes relative to the database transaction.

* **Pre-Commit:** You can safely throw exceptions to roll back the transaction.
* **Post-Commit:** You lose throwing privileges and must rely on logging or Dead Letter Queues (DLQ).

| Dispatch Timing                                                         | `FIRE_AND_FORGET`                                        | `FAIL_FAST`                                           | `CONTINUE_AND_AGGREGATE`                                          |
|:------------------------------------------------------------------------|:---------------------------------------------------------|:------------------------------------------------------|:------------------------------------------------------------------|
| **`DispatchImmediatelyInTransaction`**<br>*(Before main work finishes)* | ✅ **Safe**<br>Errors logged; transaction continues.     | ✅ **Standard**<br>Throws immediately; rolls back DB. | ✅ **Safe**<br>Collects all, throws at end; rolls back DB.        |
| **`DispatchAfterPrimaryWork`**<br>*(Before DB commit)*                  | ✅ **Safe**<br>Secondary work fails quietly; DB commits. | ✅ **Safe**<br>Throws before commit; rolls back DB.   | ✅ **Safe**<br>Collects all, throws before commit; rolls back DB. |
| **`DispatchAfterTransaction`**<br>*(After DB commit)*                   | ✅ **Standard**<br>Failures caught and sent to DLQ.      | ❌ **Dangerous**<br>Throws after transaction commits  | ❌ **Dangerous**<br>Throws after transaction commits              |

### Concurrency

All events can be dispatched sequentially or concurrently by applying `DispatchSequentially` or
`DispatchConcurrently` interfaces to the event. This applies regardless of dispatch timing or error strategy. That is,
while concurrent events will dispatch to multiple handlers at the same time, `FAIL_FAST` concurrent events will still
throw on the first failure; meaning all running handlers for that event will cancel.

### Event Defaults

By default, domain event handlers that extend `DomainEventHandler` directly are dispatched **asynchronously after the
transaction commits**. This default is intentional:

- **Asynchronous by default** — All events (both domain and integration) default to asynchronous, fire-and-forget
  dispatch. If work must be synchronous and transactional, it should ideally be modeled as an explicit Command, not an
  Event. This keeps coupled operations visible in the code rather than hiding them behind event handlers that appear
  decoupled but are actually tightly bound. Synchronous event dispatch should be avoided where possible outside of
  infrastructure concerns.
- **After transaction by default** — Domain events default to dispatching after a unit of work transaction commits
  because handlers typically trigger side effects (notifications, external calls) that should only happen once the
  primary work has been persisted. Dispatching before commit risks executing side effects for work that may still be
  rolled back.

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.example.fixtures.OrderShipped
-->

```kotlin
// Dispatched immediately when the event is raised (synchronous)
class NotifyWarehouse : DomainEventHandler<OrderShipped>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: OrderShipped) {
        /* ... */
    }
}

// Dispatched after the primary handler completes but before transaction commit (synchronous)
class UpdateInventory : DomainEventHandler<OrderShipped>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: OrderShipped) {
        /* ... */
    }
}

// Dispatched after the transaction has been committed (asynchronous)
class SendShipmentNotification : DomainEventHandler<OrderShipped>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: OrderShipped) {
        /* ... */
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-domain-events-02.kt).

#### Integration Events

Integration events are dispatched after the transaction commits, intended for cross-boundary communication. A
command's return **never awaits** integration handler execution — publishing hands the event off to the
[outbox](#transactional-outbox) or [direct publisher](#event-routing) and moves on. Once dispatch actually runs,
though, it awaits its own handlers before returning (concurrently or sequentially, per the event's `Concurrency`
setting) — this is what lets an [inboxed context](#per-context-inbox) ack only after they complete, rather than
before a single one has started.

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
-->

```kotlin
class UserRegistered(val userId: String) : IntegrationEvent()

class SyncToExternalCRM :
    IntegrationEventHandler<UserRegistered> {

    override suspend fun handle(message: UserRegistered) {
        // Sync to external system...
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-integration-events-01.kt).

#### Observing Integration Events

Integration events can be observed as Kotlin Flows directly from the bus. With the generated bus, only known events can
be observed — attempting to observe an unknown event is a compile error:

```kotlin
// With the generated bus — type-safe, one method per known event
val bus = CompileTimeLoadedMessageBus(loader, transactionManager, middleware)

scope.launch {
    bus.observeUserRegistered().collect { event ->
        println("User registered: ${event.userId}")
    }
}

// With the runtime bus — any IntegrationEvent can be observed
val runtimeBus = MessageBus(handlerLocator)

scope.launch {
    runtimeBus.observe(UserRegistered::class).collect { event ->
        println("User registered: ${event.userId}")
    }
}
```

Events are emitted to observers before handlers are invoked. Observers receive events regardless of handler success or
failure.

A handler publishes integration events by declaring an `IntegrationEventPublisher` constructor parameter. The publisher
it is given belongs to the invocation that reached it, so it is the one carrying that command's outbox and transaction:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.fixtures.RegisterUser
import com.jimbroze.kbus.example.fixtures.UserRegistered
-->

```kotlin
class RegisterUserHandler(private val integrationEventPublisher: IntegrationEventPublisher) :
    CommandHandler<RegisterUser, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: RegisterUser): BusResult<String, MessageFailure> {
        val userId = "generated-id"

        integrationEventPublisher.publish(listOf(UserRegistered(userId)))

        return BusResult.success(userId)
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-integration-events-02.kt).

#### Auto-Publishing Integration Events from Domain Events

The `AutoPublishIntegrationEvents` middleware publishes integration events automatically whenever a registered domain
event is dispatched — no explicit `publish` call needed. Register mappings with `autoPublish`, either as a lambda or as
an object implementing `IntegrationEventMapper`. A domain event may be registered multiple times to publish several
integration events.

A mapper names both a domain event and an integration event, so it belongs to the producing context's application
layer — the only layer that may see its own domain model and its published contracts at once. Keeping it out of the
integration event itself is what lets the contract be depended on by consumers without dragging the producer's domain
along with it.

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventMapper
import com.jimbroze.kbus.core.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.autoPublish
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.domain.event.DomainEvent
-->

```kotlin
class OrderPlaced(val orderId: String) : DomainEvent()

class OrderPlacedIntegration(val orderId: String) : IntegrationEvent()

object OrderPlacedMapper : IntegrationEventMapper<OrderPlaced> {
    override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
}

class OrderPlacedAnalytics(val orderId: String) : IntegrationEvent()

val busWithAutoPublish = MessageBus(
    handlerLocator = PersistingHandlerLocator(),
    middlewares = listOf(
        AutoPublishIntegrationEvents(
            autoPublish(OrderPlacedMapper),
            autoPublish<OrderPlaced> { OrderPlacedAnalytics(it.orderId) },
        ),
    ),
)
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-integration-events-03.kt).

Registering every mapping by hand doesn't scale in a generated bus. Annotate the mapper with `@LoadEventMapper` and
code generation collects it into a generated `generatedAutoPublishRegistrations` list — see
[Auto-Publish Registrations](#auto-publish-registrations) below.

## Result Types

All commands and queries return `BusResult<TValue, TMessageFailure>`:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.fixtures.MyCommand
import com.jimbroze.kbus.example.fixtures.resultExampleBus as bus
-->

```kotlin
suspend fun main() {
    val result: BusResult<String, MessageFailure> = bus.execute(MyCommand())

    when {
        result.isSuccess -> println("Value: ${result.getOrNull()}")
        result.isFailure -> println("Error: ${result.failureOrNull()?.reason?.message}")
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-results-01.kt).

Create results with companion functions:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.example.fixtures.GenericMessageFailure
-->

```kotlin
val success = BusResult.success("value")
val failure = BusResult.failure(GenericMessageFailure(GenericFailure("Something went wrong")))
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-results-02.kt).

Transform a result without unpacking it. `mapFailure` is what a handler forwarding another message's result needs:
each message declares its own failure type, so passing one straight through does not type-check.

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.example.fixtures.GenericMessageFailure
import com.jimbroze.kbus.example.fixtures.MyCommand
import com.jimbroze.kbus.example.fixtures.resultExampleBus as bus
-->

```kotlin
suspend fun main() {
    val result = bus.execute(MyCommand())

    println(result.mapSuccess { it.length }.getOrNull())
    println(result.mapFailure { GenericMessageFailure(GenericFailure("could not do the thing")) })
    println(result.collapse({ "Value: $it" }, { "Error: ${it.reason.message}" }))
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-results-03.kt).

## Middleware

Middleware wraps handler execution in a composable pipeline. Each middleware can run logic before and after the next
handler in the chain. Every `handle` call also receives a `MiddlewareInvocationContext`, a per-invocation context object
passed to all middleware in the chain. It currently exposes `integrationEventPublisher`, an
`IntegrationEventPublisher` wired to the bus's real dispatch path — middleware can use it to publish integration events
directly, independent of any command handler's own publishing.

### Middleware Scope

Every middleware declares a `scope`, which decides whether it re-runs for a command executed from inside another
command's invocation:

- `MiddlewareScope.EntryPointOnly` — runs only for the command that entered the bus.
- `MiddlewareScope.EveryCommand` — also runs for each nested command.

There is no default, because only a middleware's author knows whether re-entering it is safe. It has no bearing on
event dispatch, which is its own entry point and always runs the full chain.

### Writing Custom Middleware

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareInvocationContext
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareScope
import kotlin.time.TimeSource
-->

```kotlin
class TimingMiddleware : Middleware {
    override val scope = MiddlewareScope.EveryCommand

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val mark = TimeSource.Monotonic.markNow()
        try {
            return nextMiddleware(message)
        } finally {
            val duration = mark.elapsedNow()
            println("${message::class.simpleName} took $duration")
        }
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-middleware-01.kt).

### Using Middleware

Pass middleware when creating the bus:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.middleware.LoggingMiddleware
import com.jimbroze.kbus.example.fixtures.DebugLevel
import com.jimbroze.kbus.example.fixtures.InfoLevel
import com.jimbroze.kbus.example.fixtures.ErrorLevel
import com.jimbroze.kbus.example.fixtures.logger
-->

```kotlin
val stores = HandlerFactoryStoreCollection()

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    middlewares = listOf(
        LoggingMiddleware(logger, DebugLevel, InfoLevel, ErrorLevel),
    )
)
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-middleware-02.kt).

### Built-in Middleware

- **`LoggingMiddleware`** — Logs message dispatch, completion, and errors at configurable log levels
- **`LockingMiddleware`** — Prevents concurrent message handling with a configurable timeout
- **`AutoPublishIntegrationEvents`** — Publishes the integration event an `IntegrationEventMapper` maps a registered
  domain event to

## Unit of Work

Commands execute within a Unit of Work that manages three phases:

1. **Primary work** — The command handler executes, along with any command it nests
2. **Secondary work** — Domain event handlers run (within the same transaction)
3. **Post-commit work** — Integration event handlers run (after transaction commit)

To opt into transactional execution, pass a `TransactionManager` to the bus to apply it globally:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.example.fixtures.myTransactionManager
-->

```kotlin
val stores = HandlerFactoryStoreCollection()

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    transactionManager = myTransactionManager,
)
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-unit-of-work-01.kt).

Every bus has a transaction manager. A bus that wants no transactions keeps the default
`EmptyTransactionManager()` rather than passing none, so a command handler declaring `executeInTransaction` — which
they do by default — always has one to run in.

Command handlers execute within a transaction by default. No additional configuration is needed:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.fixtures.TransferFunds
-->

```kotlin
class TransferFundsHandler : CommandHandler<TransferFunds, BusResult<Unit, MessageFailure>>() {

    override suspend fun handle(message: TransferFunds): BusResult<Unit, MessageFailure> {
        // This runs inside a transaction (default behavior)
        return BusResult.success(Unit)
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-unit-of-work-02.kt).

You can provide a `TransactionManager` override to individual command handlers via `TransactionConfig`:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.contracts.uow.TransactionConfig
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.example.fixtures.TransferFunds
-->

```kotlin
class TransferFundsHandler(
    transactionManager: TransactionManager
) : CommandHandler<TransferFunds, BusResult<Unit, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? =
        TransactionConfig(transactionManagerOverride = transactionManager)

    override suspend fun handle(message: TransferFunds): BusResult<Unit, MessageFailure> {
        // This runs inside a transaction with a custom TransactionManager
        return BusResult.success(Unit)
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-unit-of-work-03.kt).

To opt out of transaction execution, set `executeInTransaction` to `null`:

```kotlin
class MyCommandHandler : CommandHandler<MyCommand, BusResult<Unit, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? = null

    override suspend fun handle(message: MyCommand): BusResult<Unit, MessageFailure> {
        // This runs without a transaction
        return BusResult.success(Unit)
    }
}
```

### Executing a Command From a Handler

A command handler receives a `NestedCommandExecutor` on its `CommandDependencies`, for running a sibling command as
part of the same piece of work:

```kotlin
class PlaceOrderHandler(
    private val commandExecutor: NestedCommandExecutor,
) : CommandHandler<PlaceOrder, BusResult<Unit, MessageFailure>>() {

    override suspend fun handle(message: PlaceOrder): BusResult<Unit, MessageFailure> {
        val reservation = commandExecutor.execute(ReserveStock(message.sku))
        if (reservation is BusResult.Failure) return reservation

        return BusResult.success(Unit)
    }
}
```

The nested command runs inside the outer command's Unit of Work: one transaction, its domain events in the outer
command's secondary phase, its integration events on the outer command's publisher. A throwing nested handler rolls
the whole thing back; a nested `Failure` is returned to the caller, which decides what to do with it.

Only commands the same bounded context owns are reachable this way — anything else throws `MissingHandlerException`.
Crossing a context boundary means going through the bus, which opens its own Unit of Work and therefore commits
independently. Which side of that line a command falls on is not a setting; it is which path you called.

Because a nested handler cannot open a transaction of its own, one that declares `executeInTransaction` the outer
transaction cannot satisfy — a transaction where none is running, or a different `transactionManagerOverride` from the
running one — throws `NestedTransactionMismatchException` rather than silently running outside what it asked for.

Queries have no nested equivalent: a query has no Unit of Work, so there is nothing to share. Use the bus.

#### Typed Nested Execution

With code generation, each bounded context also gets a typed view of its own commands: an interface named after the
context (`OrdersCommands`) with one function per command that module can see. Each Gradle module generates its own in
its own package, so import the one you mean. Declare it as a constructor parameter instead of `NestedCommandExecutor`
and the call site names the command:

```kotlin
import com.jimbroze.kbus.generated.ordersDomain.OrdersCommands

class PlaceOrderForRegularCustomerHandler(private val ordersCommands: OrdersCommands) :
    CommandHandler<PlaceOrderForRegularCustomer, BusResult<Order, MessageFailure>>() {

    override suspend fun handle(
        message: PlaceOrderForRegularCustomer,
    ): BusResult<Order, MessageFailure> =
        ordersCommands.placeOrder(PlaceOrder(message.customerId, message.items, "stored-card"))
}
```

The interface covers the commands its module declares plus those it learns from the `@KbusIndex` metadata of the
modules it depends on — a command in a module it cannot reference is not typed-callable, because it is not
referenceable either. It extends `NestedCommandExecutor`, so the untyped `execute` stays available for anything the
interface does not cover, with the same one-context limit.

A handler can be given the interface its own module generates, not only one from a module it depends on. The
interfaces are written before any handler is read, so a handler naming the type its own build is about to produce
still resolves.

#### Sending a Command Across a Boundary

Nested execution stops at the context boundary, and the generated bus is assembled downstream of every context, so a
module cannot simply take it. What it can take is a `CommandGateway<TCommand, TResult>` — the one command it is
entitled to send, as a constructor parameter:

```kotlin
class InventoryStockReservations(
    private val reserveStock: CommandGateway<ReserveStock, ReserveStockResult>
) : StockReservations {
    override suspend fun reserve(productId: String, quantity: Int): Boolean =
        reserveStock.execute(ReserveStock(productId, quantity)).getOrNull() != null
}
```

The generator writes one implementation per command that has a handler — `ReserveStockGateway`, taking the bus — so
being handed a gateway is a compile-time claim that something can handle what it sends. Wire it where the bus is
built. The command goes through the bus like any other: its own Unit of Work, committing independently of whatever
called it.

Which context a handler's commands come from is a type fact, not a convention the generator upholds. A context is
typed by the commands it owns, and supplies them itself when a command runs against it, so a handler built for one
context cannot be run against another — the two contexts no longer share a type, and the mismatch is a compile error
rather than a missing handler at dispatch.

## Bus Lifecycle

A bus with background work — an outbox, an inbox, and/or any `LifecycleAwareMiddleware` (e.g. `LockingMiddleware`) —
must be explicitly started before it dispatches messages:

```kotlin
val bus = MessageBus(/* ... */)
bus.start()
```

`start()` runs each `LifecycleAwareMiddleware`'s `onStart`, then launches its poller and any per-context inbox pumps
(consumers before producers, so a pre-existing backlog is already draining when the poller's first tick lands). It's
idempotent — calling it again is a no-op — and a bus with none of these needs no `start()` call at all; `execute`/
`fetch` work immediately, at zero ceremony.

Calling `execute`/`fetch` on a bus that *does* have background work, before `start()`, throws
`IllegalStateException` rather than silently running with that work never having started (e.g. an outbox that never
polls).

`stop()` is `suspend`: within one `gracePeriod` budget (default 10 seconds) it calls each middleware's suspending
`onStop()` and then lets in-flight dispatch finish; it then cancels the bus's root job and waits, for at most another
`gracePeriod`, for that cancellation to complete — so shutdown is deterministic in tests and at application exit, and
bounded even if a handler never completes.

```kotlin
bus.stop() // or bus.stop(gracePeriod = 30.seconds)
```

The grace period matters for two detached, non-durable paths that would otherwise be lost outright on a cancelled
shutdown: a post-commit domain handler with the default `FireAndForget` strategy, and a `FireAndForget` integration
event's routing. An [inboxed context](#per-context-inbox) doesn't need it — it already dispatches inline on its own
pump coroutine, so a cancelled pump just leaves its envelope unacked for the next `start()` to pick up. The drain waits
for quiescence rather than for one snapshot of what was in flight when `stop()` was called, so a handler that itself
launches further detached work during the grace period — a fire-and-forget handler publishing a further fire-and-forget
event — is waited for too. Only the bus's own dispatch scope is covered: a handler that launches onto a scope the bus
doesn't own is still cancelled mid-flight, and the grace period bounds the whole drain.

`onStop()` is `suspend`, so a middleware with non-durable work in flight can await it there rather than have the scope
from `onStart` cancelled from under it. The `onStop` calls and the dispatch drain share one budget, sequentially, so a
middleware that suspends for the whole grace period starves the ones after it — the alternative, a budget per
middleware, makes worst-case shutdown scale with how many there are. The wait for cancellation is bounded too, because
cancellation is cooperative and there is no hard kill: a coroutine that never reaches a suspension point, or that
suspends inside `NonCancellable`, is leaked rather than allowed to hang the application's exit.

Restart is unsupported — cancelling the root job is terminal, so a `stop()`ped bus cannot be `start()`ed again.
`stop()` before `start()` is a no-op.

## Event Routing

Between publish and dispatch sits a third stage: routing. Every integration publish path — the imperative
`publish()`, `AutoPublishIntegrationEvents`, query middleware, and integration event handlers that publish further
events — hands its events to an `EventRouter`, whether or not an outbox is configured. The router emits each event to
`observe()` collectors once per routing attempt, before fan-out, then attempts delivery to every `EventDestination`
that applies to the event. The local-dispatch destination is derived from a `BoundedContext` — the declaration a user
constructs and registers handlers on. A bus holds one per `BoundedContextId`:

```kotlin
val bus = MessageBus(
    contexts = listOf(
        BoundedContext(BoundedContextId("orders"), ordersLocator),
        BoundedContext(BoundedContextId("inventory"), inventoryLocator),
    ),
)
```

Each context's `appliesTo` is read from its own locator when the bus is built, so an integration handler registered in
one context never fires for another context's event, and one registered after the bus was constructed is not subscribed
to at all. A bus takes either a `contexts` list or a single `handlerLocator`, never both: the locator form gives a
single implicit `default` context over that locator — the behaviour of a non-modular application.

**Commands and queries resolve by owner lookup across `contexts`**, not through the bus's own handler locator directly:
while the bus is being built it asks each context's locator what it handles
(`HandlerLocator.handledCommandTypes`/`handledQueryTypes`) and indexes the result. Exactly one context must claim a
given command or query. Two or more throws `AmbiguousHandlerException` **from the bus constructor**, so a single-owner
conflict surfaces against the wiring at startup rather than against whichever dispatch first happens to hit it; a
message no context claims throws `MissingHandlerException` from `execute`/`fetch`. This means that once you pass an
explicit `contexts` list, every command and query handler must be registered on one of those contexts' locators.
Constructing a bus with two contexts sharing the same `BoundedContextId`
also throws, at construction time. Domain events are unaffected — they still resolve through their own context's
handler locator.

Because ownership is indexed when the bus is built, a command or query handler registered on a locator *after* that
point is not routable — register everything before constructing the bus.

Three consequences worth knowing:

- **Dispatch middleware runs once per subscribing context**, so a locking middleware acquires once per context.
  Destinations are routed to concurrently, so those acquisitions overlap rather than queueing behind one another.
- **An event no context subscribes to is silently accepted and acknowledged.** Nothing dispatches, so the dispatch
  middleware chain does not run for it either, and with an outbox the entry is marked published rather than retried
  forever.
- **`observe()` is a bus-level diagnostic tap, not a subscription mechanism.** It fires at the router, before fan-out,
  independent of which contexts subscribe — do not use it to consume another context's events.

External transports and [per-context inboxes](#per-context-inbox) are further `EventDestination`s, which is what the
router seam exists to support.

Observation is at-least-once, not exactly-once: the router re-emits each time an event is routed, and a failed
destination is re-routed by the outbox poller, so observers see a retried event again. Exactly-once observation would
require deduping on `EventEnvelope.id` against a durable store — the same id-keyed machinery a consuming inbox needs.

A destination that throws is not acknowledged: with an outbox configured, that leaves the entry unpublished for the
poller to retry, the same as any other delivery failure. Routing has no dependency on the outbox — it is what every
publish path always goes through, and the outbox composes with it rather than replacing it. For a destination fronted
by a [per-context inbox](#per-context-inbox), the thing that can throw (and un-ack the entry) is the *save* to the
inbox's store, not the handlers — dispatch to handlers happens later, off the inbox's own pump.

## Event Ordering

**Domain events are ordered. Integration events are not.** This is a deliberate split, not an implementation gap.

Domain events run inside a command, in one process, with no retries in play, so ordering is both cheap and meaningful:

- **By phase** — all immediate work, then all after-primary-work, then all post-commit work.
- **By publication order within a phase** — publish A then B, and A's handlers for that phase finish before B's start.
- **Not across the handlers of a single event** — those run concurrently unless the event declares `DispatchSequentially`.

Integration events cross a context boundary, are retried, and may be consumed by several processes. kbus therefore
makes **no ordering guarantee** on them, and the API deliberately offers no way to ask for one:

- Publishing splits a batch by error strategy, so fire-and-forget events race the rest.
- Routing fans out to subscribing contexts concurrently.
- Delivery within a fetched batch is concurrent, capped by `maxConcurrentDeliveries` (default 16) on `OutboxConfig`
  and `InboxTuning`.
- Retries reorder by construction: a failed envelope is redelivered after later ones already succeeded.

Setting `maxConcurrentDeliveries = 1` restores strict in-order delivery *within a single fetched batch, in a single
process*. That is genuinely all it buys — nothing constrains the order of two batches, and a second process polling the
same store interleaves with this one freely. Treat it as a throughput/isolation knob rather than an ordering feature:
its real cost is that one slow or failing envelope holds up every envelope behind it in the batch.

**If a consumer needs ordering, put a sequence number or version on the event** and let the handler detect and reject
stale arrivals. That works across processes and survives retries, neither of which a delivery-side guarantee can offer.
Ordering-sensitive work that genuinely must be sequenced usually belongs in a command or a domain event, where the
ordering above is real.

## Transactional Outbox

Integration events published during a command (both the imperative `publish()` and the `AutoPublishIntegrationEvents`
middleware) normally publish inside the command's transaction and dispatch to handlers immediately. This leaves two
gaps: a **phantom event** (published, then the transaction rolls back) and a **lost event** (committed, then the process
crashes before handlers run). A transactional outbox closes both gaps by persisting events to a durable store inside the
transaction, then delivering them separately.

The outbox hangs off `CommandInvocation`, the bus's per-command scope — a peer of `TransactionManager` on the bus
constructor, **not middleware**. The Unit of Work itself has no knowledge of the outbox; the outbox instead receives the
command's Unit of Work at construction time and self-registers into its generic secondary-work/post-commit-work phases.
Opt in by passing an `OutboxConfig`:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.infrastructure.outbox.InMemoryOutboxStore
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
-->

```kotlin
val stores = HandlerFactoryStoreCollection()

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    outbox = OutboxConfig(store = InMemoryOutboxStore()),
).apply { start() }
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-transactional-outbox-01.kt).

An outbox is background work, so the bus must be [started](#bus-lifecycle) before it dispatches messages — `start()` is
what launches the poller.

`InMemoryOutboxStore` is a reference implementation for tests and examples. For production, implement `OutboxStore`
against a durable table. The outbox defers the actual store write until an internal flush, self-registered as the
*first* secondary-work item on the command's Unit of Work when the outbox is constructed — so `save` runs inside the
command's `TransactionManager.execute` block, and your implementation **must join the ambient transaction** (the same
by-convention contract as `TransactionManager` itself) — otherwise a rolled-back command would leave a phantom event
saved to the outbox. This deferral is also what makes command-chain middleware publishes rollback-safe even though
middleware runs *before* the transaction opens: a middleware-published event sits in memory only until the flush runs,
so if the handler subsequently fails, the flush never happens and nothing is ever saved.

**Publish and dispatch are decoupled.** "Publish" is the durable save inside the transaction; "dispatch" is the async
delivery to handlers afterward. A command's return value **never awaits** integration handler execution, whether or not
an outbox is configured.

**When an outbox is configured, *every* integration publish routes through it** — not just command-scoped ones. Query
middleware, `AutoPublishIntegrationEvents` outside a command, and integration event handlers that publish further events
all get a durable, at-least-once-delivered publish too. Command-scoped publishes get the transactional save-then-flush
behaviour described above; everything else gets "save immediately, then drain opportunistically" — there's no
transaction to defer the save to, so the save happens up front and delivery follows the same
fire-and-forget/poller-backstop pattern. The trade-off: non-command saves aren't atomic with whatever surrounding work
triggered them, but events are never silently lost, and the same retry/DLQ policy applies uniformly across every publish
path. Integration event handlers can themselves publish further events by declaring an `IntegrationEventPublisher`
constructor parameter, exactly as command and domain event handlers do — the publisher a handler is constructed with
is the one belonging to whatever reached it, so these publishes are outbox-durable too.

A publish call's failure semantics differ slightly by path: on the transactional (command-scoped) path, `publish`
only fails if the *buffering* itself fails (essentially never); on every other path, `publish` fails if the *store save*
fails. Either way, delivery failures never propagate to the caller — they're the poller's problem.

**Delivery is at-least-once.** A bus-owned background poller is the delivery guarantee, repeatedly fetching unpublished
entries and delivering them — this is what survives a crash between commit and delivery. The poller is started by [
`bus.start()`](#bus-lifecycle), not by construction. After a command's transaction commits (and any post-commit-phase
handlers finish), a fire-and-forget drain of the events just captured also runs — self-registered as post-commit work by
the outbox itself — purely as a latency optimisation; it is never awaited, and if it fails or is skipped the poller
retries the entry on its next tick. Because the drain and the poller can overlap, and because multiple bus instances may
run against the same store, a handler may occasionally run more than once — design handlers to be idempotent.
`OutboxStore.fetchUnpublished` is the hook for narrowing that window (e.g. hiding very recently-saved or already-claimed
entries).

For a context with no inbox, an event's `errorStrategy` doubles as the outbox's acknowledgement semantics. Dispatch
itself always awaits every handler before the poller (or drain) marks the entry published — `FireAndForget` and
`FailFast` no longer differ on *when* the mark happens, only on what a handler's exception does to it: `FireAndForget`
marks the entry published regardless of the outcome (a failing handler is swallowed and never retried), while
`FailFast` leaves it unpublished if any handler threw, so the poller retries it. Either way, a handler failure no
longer fails the command itself — the command has already returned by the time delivery happens.
An outbox without an inbox retries the *whole* fan-out on any destination failure — see
[Per-Context Inbox](#per-context-inbox) for the context-isolated alternative, where the outbox instead acks once every
destination has *accepted* the event (for an inboxed context: persisted to its own store), and `errorStrategy` becomes
that context's own inbox acknowledgement semantics.

A few edge cases worth knowing:

- Commands with `executeInTransaction = null` still route their events through the outbox, just without the atomicity of
  the save being part of a real transaction.
- A detached (`FireAndForget`, post-commit) domain event handler that calls `publish()` itself still publishes through
  the outbox, via its already-flushed path (saved immediately, no atomicity with the original command) — it does not
  bypass the outbox, but delivery for that event now depends on the poller rather than the command's own drain.
- Events published by `POST_COMMIT`-phase domain event handlers are saved to the outbox non-transactionally (the
  transaction has already committed), but are still captured and drained like any other event.

## Per-Context Inbox

With multiple [bounded contexts](#event-routing), a throwing handler in one context makes the router's whole-envelope
delivery fail, so a configured outbox leaves the entry unpublished — and its poller re-routes that entry to **every**
context on retry, not just the failing one. Healthy contexts re-dispatch on every cycle until the sick one recovers.
A per-context inbox fixes this: opt a context in, and its `deliver` becomes *save durably and return* — the router acks
that context immediately, and a separate, per-context background pump handles dispatch (and retries) from its own
durable store. A failing context now only retries itself.

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.infrastructure.outbox.InMemoryOutboxStore
import com.jimbroze.kbus.core.infrastructure.inbox.InMemoryInboxStore
import com.jimbroze.kbus.core.boundedcontext.BoundedContext
import com.jimbroze.kbus.core.boundedcontext.BoundedContextId
import com.jimbroze.kbus.core.boundedcontext.inbox.BoundedContextInbox
import com.jimbroze.kbus.core.boundedcontext.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
-->

```kotlin
val stores = HandlerFactoryStoreCollection()
val ordersLocator = PersistingHandlerLocator(stores)
val inventoryLocator = PersistingHandlerLocator(stores)

val bus = MessageBus(
    contexts = listOf(
        BoundedContext(
            BoundedContextId("orders"),
            ordersLocator,
            inbox = BoundedContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
        ),
        BoundedContext(
            BoundedContextId("inventory"),
            inventoryLocator,
            inbox = BoundedContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
        ),
    ),
    outbox = OutboxConfig(store = InMemoryOutboxStore()),
).apply { start() }
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-per-context-inbox-01.kt).

Each context declaring a `BoundedContextInbox` supplies its **own** `InboxStore` instance — structural isolation, not a shared
table with a context column, so one context's pump physically cannot see another context's rows. A context that
declares none keeps synchronous, un-inboxed dispatch; the two can be mixed on the same bus. Declaring the inbox on the
context rather than in a bus-level map keyed by `BoundedContextId` means naming a context that does not exist is not
expressible, and the store cannot drift apart from the context it belongs to.

A generated bus builds its contexts for you, so the inbox arrives in that context's `BoundedContextConfig` — under the same
compile-time-checked name used to subscribe its event handlers:

```kotlin
CompileTimeLoadedMessageBus(
    dependencies, transactionManager, middleware,
    orders = BoundedContextConfig(
        inbox = BoundedContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
    ),
)
```

**Dedupe is what actually kills the amplification.** The router still routes a whole envelope to every context on
every attempt, exactly as without an inbox — nothing about routing changes. What changes is that an inboxed context's
`deliver` collapses to `InboxStore.save`, which is idempotent per `EventEnvelope.id`: a re-routed envelope that's still
pending, or already consumed, is silently dropped. So a healthy context acks instantly and dedupes a retried envelope
on its next arrival, while a sick context's failure is now a *handler* failure inside its own pump — invisible to the
router — and never causes the healthy contexts to redo anything.

`InboxStore` diverges from `OutboxStore` in two ways implementers must not miss (copying an existing outbox store is
not enough): `save` must reject an id that is still *pending*, not only one already *consumed* — a fetched-but-unacked
envelope is otherwise re-savable, which double-dispatches — and `markConsumed` must **not** forget the id, the way
`OutboxStore.markPublished` is allowed to. A consumed id is a dedupe tombstone that must survive at least as long as
the producing outbox might still retry, or a late redelivery slips back in as a duplicate.

Two separate things determine what happens to a failing handler at the inbox — easy to conflate, so worth naming
separately:

- **`errorStrategy`** (on the event) decides whether a handler's exception ever reaches the inbox at all.
- **`ackPolicy`** (on the context's own `BoundedContextInbox`) decides whether that inbox accepts a producer's `FireAndForget`
  "don't care", or requires stronger guarantees than the producer declared. It is a required parameter — neither answer
  is a safe default to pick on a consumer's behalf.

Integration-event dispatch — at an inbox or anywhere else — always awaits its handlers before returning, regardless of
`errorStrategy` or `concurrency`. There is no window where the inbox can ack before a handler has even started; the
tombstone is only ever written after dispatch returns. What `errorStrategy` still controls is what happens to a
handler's *exception*:

| `errorStrategy` | Effect at the inbox |
|---|---|
| `FailFast` | A throwing handler leaves the envelope pending in *this context's* store, retried every poll interval until every handler completes. No other context is affected. |
| `ContinueAndAggregate` | All handlers run; any failure leaves the envelope pending, so a retry re-runs the ones that already succeeded too. |
| `FireAndForget` | Every handler still runs to completion, but an exception is swallowed rather than surfaced — the envelope is acked and tombstoned regardless, so a failing handler is never retried. |

`FireAndForget`'s "never retried" row is a legitimate choice for events a producer truly doesn't care about, but a
consumer can refuse it via its context's `ackPolicy`:

```kotlin
BoundedContextInbox(InMemoryInboxStore(), InboxAckPolicy.RequireHandlerSuccess)
```

| `ackPolicy` | Effect |
|---|---|
| `HonourEventStrategy` | Ack exactly as the table above. |
| `RequireHandlerSuccess` | A `FireAndForget` event is dispatched as if it were `ContinueAndAggregate`: a handler failure now leaves the envelope pending and is retried. `FailFast` and `ContinueAndAggregate` events are unaffected — they already retry on failure. |

`ackPolicy` is per context, not per event: it applies uniformly to every event flowing through that context's inbox,
without the producer having to know or care which contexts consume its events with stronger guarantees.

As with the outbox, handlers must still be idempotent — the inbox dedupes *transport* redelivery (the same envelope
id arriving twice), not *handler* re-execution: a crash between fetching and acking redispatches the same envelope on
restart, and a retry (from either `errorStrategy` or `ackPolicy`) re-runs a handler that already succeeded once.

A few things this stage deliberately leaves for later, since none of them require a breaking change to add:

- No dead-letter queue — a poison message retries forever, and if poison entries ever exceed the batch size, the
  oldest-first fetch stops advancing and the context wedges.
- `pollInterval`, `batchSize`, `maxConcurrentDeliveries` and whether dispatch is opportunistic stay bus-wide on
  `InboxTuning`, not per-context.
- Tombstone retention has no contract-level pruning hook; an implementation that prunes too aggressively re-opens the
  duplicate window it was closing.
- No ordering guarantee — see [Event Ordering](#event-ordering); this is a design decision rather than a gap, but a
  consumer that wants in-batch ordering has only the blunt `maxConcurrentDeliveries = 1`.
- Middleware dispatched from an inboxed context runs on the inbox's own pump coroutine, not the caller's — a
  `LockingMiddleware`'s re-entrancy token (carried in the coroutine context) does not propagate from whatever
  triggered the original publish.

## KSP Code Generation

For compile-time type-safe handler resolution with zero reflection, use the KSP code generation module. This requires
adding no annotations or coupling to anything outside your message handlers (which are already coupled to Kbus).

### Setup

```groovy
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.devtools.ksp)
}

dependencies {
    implementation("com.jimbroze:kbus-core:<version>")
    implementation("com.jimbroze:kbus-annotations:<version>")
    add("kspCommonMainMetadata", "com.jimbroze:kbus-generation:<version>")
}

// Include generated sources
kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
```

### Annotate Handlers

Mark handler classes with `@LoadMessageHandler`. Constructor parameters become automatically resolved dependencies:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.fixtures.OrderRepository
import com.jimbroze.kbus.example.fixtures.PaymentService
import com.jimbroze.kbus.example.fixtures.PlaceOrder
-->

```kotlin
@LoadMessageHandler
class PlaceOrderHandler(
    private val orderRepository: OrderRepository,
    private val paymentService: PaymentService,
) : CommandHandler<PlaceOrder, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: PlaceOrder): BusResult<String, MessageFailure> {
        val orderId = orderRepository.save(message.items)
        paymentService.charge(orderId)
        return BusResult.success(orderId)
    }
}
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-generation-01.kt).

### Generated Code

The KSP processor generates:

- **`AllDependencies`** — Interface listing all required dependencies (implement this to provide them)
- **`<Context>Handlers`** — One interface per bounded context, with factory methods for that context's handlers
  (`DefaultHandlers` for handlers whose module declares no identity, `OrdersHandlers` for `orders`, and so on)
- **`<Context>HandlerFactory`** — One factory per bounded context, creating that context's handlers with their
  dependencies resolved. A context can build no handler but its own, so a command it does not own is unresolvable
  there even when another context on the same bus owns it
- **`<Context>Commands`** — One interface per bounded context giving typed nested execution of that context's
  commands (see [Typed Nested Execution](#typed-nested-execution)), with the root generating the implementation
- **`<Context>Context`** — One class per bounded context, pairing the context with its own handler factory and the
  commands it owns. A bus function reaches its factory through it, so the context a command runs against and the
  factory that built its handler cannot be two different contexts
- **`CompileTimeLoadedMessageBus`** — A type-safe bus with strongly-typed `execute`, `fetch`, and `observe` methods for
  each message type. It takes the same optional `appScope`, `outbox` and `inbox` arguments as `MessageBus`
- **`AutoLoader`** — Auto-loading support for runtime handler registration
- **`generatedAutoPublishRegistrations`** — `List<AutoPublishRegistration<*>>` collected from every
  `@LoadEventMapper` mapper (only generated when at least one exists)

### Using the Generated Bus

<!--- CLEAR -->

```kotlin
// Implement the generated AutoLoader abstract class (or AllDependencies interface)
class MyDependencies : AutoLoader() {
    override val orderRepository: OrderRepository = OrderRepositoryImpl()
    override val paymentService: PaymentService = PaymentServiceImpl()
}

// Create the type-safe bus
val bus = CompileTimeLoadedMessageBus(
    loader = MyDependencies(),
    transactionManager = myTransactionManager,
    middleware = listOf(LoggingMiddleware(logger)),
)

// Strongly-typed dispatch — compile error if message type is wrong
val result = bus.execute(PlaceOrder(items))
```

### Auto-Publish Registrations

`@LoadEventMapper` collects an `IntegrationEventMapper` (see
[Auto-Publishing Integration Events](#auto-publishing-integration-events-from-domain-events)) into the generated
`generatedAutoPublishRegistrations` list, so the mapping doesn't need to be registered by hand:

<!--- CLEAR -->

```kotlin
@LoadEventMapper
object OrderPlacedMapper : IntegrationEventMapper<OrderPlaced> {
    override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
}

val bus = CompileTimeLoadedMessageBus(
    loader = MyDependencies(),
    transactionManager = myTransactionManager,
    middleware = listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
)
```

The annotated declaration must be an `object` implementing `IntegrationEventMapper`: the generated list is a top-level
value with nothing to resolve a mapper from, so it has to be referenceable by name alone. Anything else is reported
against the declaration.

### Submodules

For multi-module projects, submodules can export their handler metadata for the main module to consume. You must provide
a package name for the indexes. This prevents trying to load indexes from a dependent library that uses Kbus.

```groovy
// In the submodule's build.gradle.kts
ksp {
    arg("kbus.subModuleName", project.name)
    arg("kbus.indexPackage", "com.example.myApp.indexes")
}

// In the top-level module's build.gradle.kts
ksp {
    arg("kbus.indexPackage", "com.example.myApp.indexes")
}
```

Submodules generate a `DependencyIndex` with `@KbusIndex` metadata instead of full bus code. The main module picks up
these indexes automatically, including any `@LoadEventMapper` opt-ins, which are folded into the main module's
`generatedAutoPublishRegistrations`.

An index also names the typed command interfaces its module generated, against the bounded context each covers. That
is how a downstream module knows which interfaces its generated executor must satisfy — it reads the type from
metadata rather than discovering it by where it was written.

A submodule's generated code goes in a package of its own, `com.jimbroze.kbus.generated.<subModuleName>`, so the
several Gradle modules of one bounded context can each generate the same class names without colliding. Import the
one you want:

```kotlin
import com.jimbroze.kbus.generated.billingDomain.OrdersCommands
```

Index classes are the exception: they all share `kbus.indexPackage` and so carry the module in their name.

### Bounded Context identity

A bounded context usually spans several Gradle modules (`billing-domain`, `billing-application`,
`billing-infrastructure` are three submodules but one context). `kbus.boundedContextIdentity` names the context, and is
orthogonal to `kbus.subModuleName`:

```groovy
ksp {
    arg("kbus.subModuleName", project.name)
    arg("kbus.boundedContextIdentity", "billing")
    arg("kbus.indexPackage", "com.example.myApp.indexes")
}
```

Identity is stamped by the producing module's KSP run and recorded on each handler in its index — it is never inferred
by the consumer. The generated bus builds one `BoundedContext` per distinct identity and takes a `BoundedContextConfig`
parameter for each, named after the identity, plus `default` for handlers from modules that declared no identity.

Those contexts live in a generated nested `Contexts` class, one property per identity, which the bus hands to
`BaseMessageBus` as a factory taking a `ContextBuilder`. Registering a context on that builder is what produces the
context the bus runs, so a generated `execute` reaches its command's context by name — `boundedContexts.billing` —
rather than by looking an id up at runtime, and a declared context can never be one the bus is unaware of. Each
property has that context's own type, which is also where its handler factory lives, so naming the wrong context does
not compile. Contexts are not otherwise reachable: nothing outside the bus can subscribe to a context once the bus
holding it exists.

Because the identity becomes a Kotlin name, two identities that differ only in their separators — `order-fulfilment`,
`order_fulfilment`, `order.fulfilment`, `orderFulfilment` — are rejected at generation time, naming both identities and
the handlers that declared them. `default` is rejected for the same reason: it is the name of the context that owns
every handler declaring no identity, so a module claiming it would be folded into that catch-all instead of isolated
from it. Setting the build arg to blank whitespace is rejected too — remove it instead to leave a module's handlers in
the default context.

Each context gets a `BoundedContextConfig` parameter named after its identity:

```kotlin
val bus = MyBus(
    dependencies, transactionManager, middleware,
    billing = BoundedContextConfig(
        integrationSubscriptions = listOf(integrationSubscription(InvoiceIssued::class, SyncLedgerHandler::class.loaded)),
    ),
    default = BoundedContextConfig(
        integrationSubscriptions = listOf(integrationSubscription(AuditRecorded::class, ArchiveAuditHandler::class.loaded)),
    ),
)
```

Domain handlers go in their own parameter, because the two kinds carry different guarantees — a domain handler runs
inside the command's transaction, an integration handler after it commits:

```kotlin
billing = BoundedContextConfig(
    domainSubscriptions = listOf(domainSubscription(InvoiceIssued::class, SyncLedgerHandler::class.loaded)),
)
```

Naming a context that does not exist does not compile, because `billing` is a parameter name rather than a key. A
subscription is an ordinary value, so a context's subscriptions can live in their own file and be imported here:

```kotlin
val billingSubscriptions: List<IntegrationEventSubscription<*>> =
    listOf(integrationSubscription(InvoiceIssued::class, SyncLedgerHandler::class.loaded))
```

There is deliberately no bus-wide `integrationEventRegistrar` or `domainEventRegistrar`: with several contexts, "which
context?" has no answer for either. A command's domain events dispatch only to its owning context's domain
handlers — a domain handler registered on `billing` never fires for a command owned by another context.

### When handlers may be registered

**Command and query handlers must be registered before the bus is constructed.** A command has exactly one owning
context, and the bus is what resolves that owner, so it must be able to settle ownership while it is being built — that
is what lets two contexts claiming the same command be reported against your wiring rather than against some later
dispatch in production. Register them on a context's `HandlerLocator` before passing the context to the bus.

**Event handlers must be subscribed before the bus is constructed too** — as a `BoundedContextConfig` parameter of a
generated bus, or a constructor argument of a hand-written `BoundedContext`:

```kotlin
BoundedContext(
    BoundedContextId("billing"),
    locator,
    integrationSubscriptions = listOf(integrationSubscription(InvoiceIssued::class, SyncLedgerHandler::class.loaded)),
)
```

Nothing enforces this at runtime, because nothing needs to: a context's subscriptions are constructor arguments, so a
constructed context has nothing left to add to. There is no `seal()` and no `HandlerRegistrationSealedException` — a
bus that never hands back a context cannot be subscribed into.

`domainSubscription` accepts only `DomainEventHandler` subclasses, not bare `EventHandler` implementations: domain
dispatch reads a handler's `dispatchTiming` and hands it a publisher to publish integration events with, so a handler
without those is rejected where it is written rather than when the event is first published. The processor applies the
same rule, so a `@LoadMessageHandler` handler of a `DomainEvent` that does not extend `DomainEventHandler` fails
generation.

`integrationSubscription` and `domainSubscription` take either bare handler classes or, from
`com.jimbroze.kbus.core.registry.generation`, `LoadedEventHandler` tokens obtained via a generated `.loaded`
property. Only the token form is checked at compile time: `.loaded` exists only for handlers the processor generated a
factory for, so a typo or a missing `@LoadMessageHandler` fails the build rather than the first dispatch. Prefer it
whenever you are using code generation. The bare-class form takes no generation dependency, for hand-written wiring.

## Domain Modeling

KBUS includes base types for domain-driven design:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.domain.AggregateRoot
import com.jimbroze.kbus.domain.Entity
import com.jimbroze.kbus.domain.Identifier
import com.jimbroze.kbus.domain.ValueObject
-->

```kotlin
// Value Object — equals() and hashCode() required
class Money(val amount: Double, val currency: String) : ValueObject<Money>() {
    override fun equals(other: Any?) =
        other is Money && amount == other.amount && currency == other.currency

    override fun hashCode() = 31 * amount.hashCode() + currency.hashCode()
}

// Entity
class OrderId(private val value: String) : Identifier {
    override fun equals(other: Any?) = other is OrderId && value == other.value
    override fun hashCode() = value.hashCode()
}

class Order(override val id: OrderId, val items: List<String>) : Entity<Order>()

// Aggregate Root
class CartId(private val value: String) : Identifier {
    override fun equals(other: Any?) = other is CartId && value == other.value
    override fun hashCode() = value.hashCode()
}

class ShoppingCart(override val id: CartId) : AggregateRoot<ShoppingCart>()
```

> You can get the full code [here](examples/docs-samples/src/commonTest/kotlin/samples/example-domain-modeling-01.kt).

## Supported Platforms

| Platform | Targets                     |
|----------|-----------------------------|
| JVM      | Java 17+                    |
| JS       | Node, Browser               |
| WASM     | Node, Browser               |
| macOS    | x64, ARM64                  |
| iOS      | x64, ARM64, Simulator ARM64 |
| Linux    | x64, ARM64                  |
| Windows  | x64                         |

## License

See [LICENSE](LICENSE) for details.
