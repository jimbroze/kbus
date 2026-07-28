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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-messages-01.kt).

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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-bus-01.kt).

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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-domain-events-01.kt).

`CommandDependencies` (which contains `DomainEventPublisher`) is injected into command handlers automatically and routes
events through the Unit of Work.

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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-domain-events-02.kt).

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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-integration-events-01.kt).

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

Command handlers can publish integration events:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.fixtures.RegisterUser
import com.jimbroze.kbus.example.fixtures.UserRegistered
-->

```kotlin
class RegisterUserHandler :
    CommandHandler<RegisterUser, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: RegisterUser): BusResult<String, MessageFailure> {
        val userId = "generated-id"

        publish(UserRegistered(userId))

        return BusResult.success(userId)
    }
}
```

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-integration-events-02.kt).

#### Auto-Publishing Integration Events from Domain Events

The `AutoPublishIntegrationEvents` middleware publishes integration events automatically whenever a registered domain
event is dispatched — no explicit `publish` call needed. Register mappings with `autoPublish`, either as a lambda or by
implementing `AutoPublishesFrom` on the integration event's companion object to declare the domain event it is derived
from. A domain event may be registered multiple times to publish several integration events.

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.messages.event.publish.AutoPublishesFrom
import com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.middleware.autoPublish
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.domain.event.DomainEvent
-->

```kotlin
class OrderPlaced(val orderId: String) : DomainEvent()

class OrderPlacedIntegration(val orderId: String) : IntegrationEvent() {
    companion object : AutoPublishesFrom<OrderPlaced> {
        override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
    }
}

class OrderPlacedAnalytics(val orderId: String) : IntegrationEvent()

val busWithAutoPublish = MessageBus(
    handlerLocator = PersistingHandlerLocator(),
    middlewares = listOf(
        AutoPublishIntegrationEvents(
            autoPublish(OrderPlacedIntegration),
            autoPublish<OrderPlaced> { OrderPlacedAnalytics(it.orderId) },
        ),
    ),
)
```

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-integration-events-03.kt).

Registering every mapping by hand doesn't scale in a generated bus. Annotate the integration event with `@LoadEvent`
and code generation collects every `AutoPublishesFrom` companion it finds into a generated
`generatedAutoPublishRegistrations` list — see [Auto-Publish Registrations](#auto-publish-registrations) below.

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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-results-01.kt).

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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-results-02.kt).

## Middleware

Middleware wraps handler execution in a composable pipeline. Each middleware can run logic before and after the next
handler in the chain. Every `handle` call also receives a `MiddlewareInvocationContext`, a per-invocation context object
passed to all middleware in the chain. It currently exposes `integrationEventPublisher`, an
`IntegrationEventPublisher` wired to the bus's real dispatch path — middleware can use it to publish integration events
directly, independent of any command handler's own publishing.

### Writing Custom Middleware

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import kotlin.time.TimeSource
-->

```kotlin
class TimingMiddleware : Middleware {
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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-middleware-01.kt).

### Using Middleware

Pass middleware when creating the bus:

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.middleware.middleware.LoggingMiddleware
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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-middleware-02.kt).

### Built-in Middleware

- **`LoggingMiddleware`** — Logs message dispatch, completion, and errors at configurable log levels
- **`LockingMiddleware`** — Prevents concurrent message handling with a configurable timeout
- **`AutoPublishIntegrationEvents`** — Publishes the integration event mapped from a registered domain event via
  `AutoPublishesFrom`

## Unit of Work

Commands execute within a Unit of Work that manages three phases:

1. **Primary work** — The command handler executes
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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-unit-of-work-01.kt).

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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-unit-of-work-02.kt).

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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-unit-of-work-03.kt).

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
that applies to the event. The local-dispatch destination is a `BoundedContext` — a module runtime that owns a slice of
handlers and dispatches to them. A bus holds one per identity:

```kotlin
val bus = MessageBus(
    handlerLocator,
    contexts = listOf(
        BoundedContext(BoundedContextId("orders"), ordersLocator),
        BoundedContext(BoundedContextId("inventory"), inventoryLocator),
    ),
)
```

Each context's `appliesTo` is derived, lazily, from its own locator, so an integration handler registered in one context
never fires for another context's event, and a handler registered after the bus was constructed is subscribed to from
that moment on. Passing no `contexts` gives a bus a single implicit `default` context over its whole handler locator —
the behaviour of a non-modular application. Context locators are used only for integration-event lookup; commands,
queries and domain events still resolve through the bus's own handler locator.

Three consequences worth knowing:

- **Dispatch middleware runs once per subscribing context**, so a locking middleware acquires once per context
  (sequentially — destinations are routed in order).
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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-transactional-outbox-01.kt).

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
path. Integration event handlers can themselves publish further events by extending
`CanPublishIntegrationEvent`, the same mixin command handlers and domain event handlers use — the dispatcher wires each
handler's publisher before dispatch, so these publishes are outbox-durable too.

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
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.module.inbox.InboxConfig
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
-->

```kotlin
val stores = HandlerFactoryStoreCollection()
val ordersLocator = PersistingHandlerLocator(stores)
val inventoryLocator = PersistingHandlerLocator(stores)

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    contexts = listOf(
        BoundedContext(BoundedContextId("orders"), ordersLocator),
        BoundedContext(BoundedContextId("inventory"), inventoryLocator),
    ),
    outbox = OutboxConfig(store = InMemoryOutboxStore()),
    inbox = InboxConfig(
        stores = mapOf(
            BoundedContextId("orders") to InMemoryInboxStore(),
            BoundedContextId("inventory") to InMemoryInboxStore(),
        ),
        ackPolicy = InboxAckPolicy.HonourEventStrategy,
    ),
).apply { start() }
```

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-per-context-inbox-01.kt).

Each context in `InboxConfig.stores` gets its **own** `InboxStore` instance — structural isolation, not a shared table
with a context column, so one context's pump physically cannot see another context's rows. A context absent from
`stores` keeps today's synchronous, un-inboxed dispatch; the two can be mixed on the same bus.

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
- **`ackPolicy`** (on `InboxConfig`, per bus) decides whether the inbox accepts a producer's `FireAndForget` "don't
  care", or requires stronger guarantees than the producer declared. It is a required parameter — neither answer is a
  safe default to pick on a consumer's behalf.

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
consumer can refuse it via `InboxConfig`'s `ackPolicy`:

```kotlin
InboxConfig(
    stores = mapOf(BoundedContextId("orders") to InMemoryInboxStore()),
    ackPolicy = InboxAckPolicy.RequireHandlerSuccess,
)
```

| `ackPolicy` | Effect |
|---|---|
| `HonourEventStrategy` | Ack exactly as the table above. |
| `RequireHandlerSuccess` | A `FireAndForget` event is dispatched as if it were `ContinueAndAggregate`: a handler failure now leaves the envelope pending and is retried. `FailFast` and `ContinueAndAggregate` events are unaffected — they already retry on failure. |

`ackPolicy` is per bus, not per event: it applies uniformly to every event flowing through that context's inbox,
without the producer having to know or care which contexts consume its events with stronger guarantees.

As with the outbox, handlers must still be idempotent — the inbox dedupes *transport* redelivery (the same envelope
id arriving twice), not *handler* re-execution: a crash between fetching and acking redispatches the same envelope on
restart, and a retry (from either `errorStrategy` or `ackPolicy`) re-runs a handler that already succeeded once.

A few things this stage deliberately leaves for later, since none of them require a breaking change to add:

- No dead-letter queue — a poison message retries forever, and if poison entries ever exceed the batch size, the
  oldest-first fetch stops advancing and the context wedges.
- `pollInterval`, `batchSize`, and whether dispatch is opportunistic are bus-wide, not per-context.
- Tombstone retention has no contract-level pruning hook; an implementation that prunes too aggressively re-opens the
  duplicate window it was closing.
- No ordering guarantee across retries — a failed envelope is retried after later ones were already delivered.
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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-generation-01.kt).

### Generated Code

The KSP processor generates:

- **`AllDependencies`** — Interface listing all required dependencies (implement this to provide them)
- **`AllHandlers`** — Interface with factory methods for every handler
- **`HandlerFactory`** — Factory that creates handlers with their dependencies resolved
- **`CompileTimeLoadedMessageBus`** — A type-safe bus with strongly-typed `execute`, `fetch`, and `observe` methods for
  each message type. It takes the same optional `appScope`, `outbox` and `inbox` arguments as `MessageBus`
- **`AutoLoader`** — Auto-loading support for runtime handler registration
- **`generatedAutoPublishRegistrations`** — `List<AutoPublishRegistration<*>>` collected from every `@LoadEvent`
  integration event whose companion implements `AutoPublishesFrom` (only generated when at least one exists)

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

`@LoadEvent` makes an event known to code generation. On its own it generates nothing — but if the annotated integration
event's companion implements `AutoPublishesFrom` (see
[Auto-Publishing Integration Events](#auto-publishing-integration-events-from-domain-events)), the processor also
collects it into the generated `generatedAutoPublishRegistrations` list, so the mapping doesn't need to be registered by
hand:

<!--- CLEAR -->

```kotlin
@LoadEvent
class OrderPlacedIntegration(val orderId: String) : IntegrationEvent() {
    companion object : AutoPublishesFrom<OrderPlaced> {
        override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
    }
}

val bus = CompileTimeLoadedMessageBus(
    loader = MyDependencies(),
    transactionManager = myTransactionManager,
    middleware = listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
)
```

An event annotated with `@LoadEvent` whose companion does not implement `AutoPublishesFrom` (or that has no companion)
is still known to the processor — it just contributes no registration; this is not an error, and leaves room for other
`@LoadEvent`-driven code generation in future.

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
these indexes automatically, including any `@LoadEvent`/`AutoPublishesFrom` opt-ins, which are folded into the main
module's `generatedAutoPublishRegistrations`.

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
by the consumer. The generated bus builds one `BoundedContext` per distinct identity and exposes a typed registration
point for each, named after the identity, plus `default` for handlers from modules that declared no identity:

```kotlin
bus.billing.addEventHandlers(InvoiceIssued::class, listOf(SyncLedgerHandler::class.loaded))
bus.default.addEventHandlers(AuditRecorded::class, listOf(ArchiveAuditHandler::class.loaded))
```

There is deliberately no bus-wide `integrationEventMapper`: with several contexts, "which context?" has no answer.
`domainEventMapper` stays bus-wide — domain events do not cross contexts.

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

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-domain-modeling-01.kt).

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
