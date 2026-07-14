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

Domain events dispatch relative to the Unit of Work lifecycle. Publish them from a domain object by
taking a `DomainEventPublisher` as a constructor dependency. See the section on // TODO

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

`CommandDependencies` (which contains `DomainEventPublisher`) is injected into command handlers automatically and
routes events through the Unit of Work.

### Event Dispatch & Error Strategy Matrix

The safety of an error strategy depends entirely on **when** the handler executes relative to the database transaction.

* **Pre-Commit:** You can safely throw exceptions to roll back the transaction.
* **Post-Commit:** You lose throwing privileges and must rely on logging or Dead Letter Queues (DLQ).

| Dispatch Timing                                                         | `FIRE_AND_FORGET`                                       | `FAIL_FAST`                                          | `CONTINUE_AND_AGGREGATE`                                         |
|:------------------------------------------------------------------------|:--------------------------------------------------------|:-----------------------------------------------------|:-----------------------------------------------------------------|
| **`DispatchImmediatelyInTransaction`**<br>*(Before main work finishes)* | ✅ **Safe**<br>Errors logged; transaction continues.     | ✅ **Standard**<br>Throws immediately; rolls back DB. | ✅ **Safe**<br>Collects all, throws at end; rolls back DB.        |
| **`DispatchAfterPrimaryWork`**<br>*(Before DB commit)*                  | ✅ **Safe**<br>Secondary work fails quietly; DB commits. | ✅ **Safe**<br>Throws before commit; rolls back DB.   | ✅ **Safe**<br>Collects all, throws before commit; rolls back DB. |
| **`DispatchAfterTransaction`**<br>*(After DB commit)*                   | ✅ **Standard**<br>Failures caught and sent to DLQ.      | ❌ **Dangerous**<br>Throws after transaction commits  | ❌ **Dangerous**<br>Throws after transaction commits              |

### Concurrency

All events can be dispatched sequentially or concurrently by applying `DispatchSequentially` or
`DispatchConcurrently` interfaces to the event. This applies regardless of dispatch timing or error strategy. That
is, while concurrent events will dispatch to multiple handlers at the same time, `FAIL_FAST` concurrent events will
still throw on the first failure; meaning all running handlers for that event will cancel.

### Event Defaults

By default, domain event handlers that extend `DomainEventHandler` directly are dispatched **asynchronously after the
transaction commits**. This default is intentional:

- **Asynchronous by default** — All events (both domain and integration) default to asynchronous, fire-and-forget
  dispatch. If work must be synchronous and transactional, it should ideally be modeled as an explicit
  Command, not an Event. This keeps coupled operations visible in the code rather than hiding them behind event
  handlers that appear decoupled but are actually tightly bound. Synchronous event dispatch should be avoided where
  possible outside of infrastructure concerns.
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

Integration events are dispatched after the transaction commits, intended for cross-boundary communication.
Integration events are **always dispatched asynchronously** — handlers run concurrently in a fire-and-forget manner.

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

Integration events can be observed as Kotlin Flows directly from the bus. With the generated bus, only known
events can be observed — attempting to observe an unknown event is a compile error:

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

Events are emitted to observers before handlers are invoked. Observers receive events regardless of handler
success or failure.

Command handlers can dispatch integration events:

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

        dispatch(UserRegistered(userId))

        return BusResult.success(userId)
    }
}
```

> You can get the full code [here](kbus-example/src/commonTest/kotlin/samples/example-integration-events-02.kt).

#### Auto-Publishing Integration Events from Domain Events

The `AutoPublishIntegrationEvents` middleware publishes integration events automatically whenever a registered domain
event is dispatched — no explicit `dispatch` call needed. Register mappings with `autoPublish`, either as a lambda or
by implementing `IntegrationEventMapper` on the integration event's companion object to declare the domain event it is
derived from. A domain event may be registered multiple times to publish several integration events.

<!--- CLEAR -->
<!--- INCLUDE
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.messages.event.IntegrationEventMapper
import com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.middleware.autoPublish
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.domain.event.DomainEvent
-->

```kotlin
class OrderPlaced(val orderId: String) : DomainEvent()

class OrderPlacedIntegration(val orderId: String) : IntegrationEvent() {
    companion object : IntegrationEventMapper<OrderPlaced> {
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
handler in the chain. Every `handle` call also receives a `MiddlewareInvocationContext`, a per-invocation context
object passed to all middleware in the chain. It currently exposes `integrationEventPublisher`, an
`IntegrationEventPublisher` wired to the bus's real dispatch path — middleware can use it to publish integration
events directly, independent of any command's `BusAccess`.

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
- **`AutoPublishIntegrationEvents`** — Publishes the integration event mapped from a registered domain event via `AutoPublishesFrom`

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

## KSP Code Generation

For compile-time type-safe handler resolution with zero reflection, use the KSP code generation module. This
requires adding no annotations or coupling to anything outside your message handlers (which are already coupled
to Kbus).

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
  each message type
- **`AutoLoader`** — Auto-loading support for runtime handler registration

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

### Submodules

For multi-module projects, submodules can export their handler metadata for the main module to consume. You must
provide a package name for the indexes. This prevents trying to load indexes from a dependent library that uses
Kbus.

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
these indexes automatically.

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
