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

```kotlin
dependencies {
    implementation("com.jimbroze:kbus-core:<version>")

    // For KSP code generation (optional)
    implementation("com.jimbroze:kbus-annotations:<version>")
    ksp("com.jimbroze:kbus-generation:<version>")
}
```

## Quick Start

### Define Messages and Handlers

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

// A query that returns a User result
class GetUser(val id: Int) :
    Query<BusResult<User, MessageFailure>>()

class GetUserHandler :
    QueryHandler<GetUser, BusResult<User, MessageFailure>>() {

    override suspend fun handle(message: GetUser): BusResult<User, MessageFailure> {
        val user = userRepository.findById(message.id)
            ?: return BusResult.failure(GenericMessageFailure(GenericFailure("User not found")))
        return BusResult.success(user)
    }
}
```

### Create the Bus and Dispatch Messages

```kotlin
// Register handlers
val stores = HandlerFactoryStoreCollection()
stores.commandStore.registerHandlers(
    CreateUser::class,
    listOf(CommandHandlerFactory(CreateUserHandler::class) { CreateUserHandler() })
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
```

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

```kotlin
class OrderShipped(val orderId: String) : DomainEvent()

class Order(private val domainEventPublisher: DomainEventPublisher) {

    override suspend fun place(orderId: Int): Boolean {
        // Place the order...

        domainEventPublisher.publish(OrderShipped(orderId))

        return true
    }
}
```

`CommandDependencies` (which contains `DomainEventPublisher`) is injected into command handlers automatically and
routes events through the Unit of Work.

Choose a dispatch strategy by extending the appropriate *handler* base class. Each handler can have a different dispatch
strategy, even for the same event type.

```kotlin
// Dispatched immediately when the event is raised
class NotifyWarehouse : DispatchImmediately<OrderShipped>() {
    override suspend fun handle(message: OrderShipped) { /* ... */
    }
}

// Dispatched after the primary handler completes but before transaction commit
class UpdateInventory : DispatchAtEndOfTransaction<OrderShipped>() {
    override suspend fun handle(message: OrderShipped) { /* ... */
    }
}

// Dispatched after the transaction has been committed
class SendShipmentNotification : DispatchAfterTransaction<OrderShipped>() {
    override suspend fun handle(message: OrderShipped) { /* ... */
    }
}
```

#### Integration Events

Integration events are dispatched after the transaction commits, intended for cross-boundary communication:

```kotlin
class UserRegistered(val userId: String) : IntegrationEvent()

class SyncToExternalCRM :
    IntegrationEventHandler<UserRegistered> {

    override suspend fun handle(message: UserRegistered) {
        // Sync to external system...
    }
}
```

Command handlers can dispatch integration events:

```kotlin
class RegisterUserHandler :
    CommandHandler<RegisterUser, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: RegisterUser): BusResult<String, MessageFailure> {
        // Register user...

        dispatch(UserRegistered(userId))

        return BusResult.success(userId)
    }
}
```

## Result Types

All commands and queries return `BusResult<TValue, TMessageFailure>`:

```kotlin
val result: BusResult<String, MessageFailure> = bus.execute(myCommand)

when {
    result.isSuccess -> println("Value: ${result.getOrNull()}")
    result.isFailure -> println("Error: ${result.failureOrNull()?.reason?.message}")
}
```

Create results with companion functions:

```kotlin
BusResult.success("value")
BusResult.failure(GenericMessageFailure(GenericFailure("Something went wrong")))
```

## Middleware

Middleware wraps handler execution in a composable pipeline. Each middleware can run logic before and after the next
handler in the chain.

### Writing Custom Middleware

```kotlin
class TimingMiddleware : Middleware {
    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val start = clock.now()
        try {
            return nextMiddleware(message)
        } finally {
            val duration = clock.now() - start
            println("${message::class.simpleName} took $duration")
        }
    }
}
```

### Using Middleware

Pass middleware when creating the bus:

```kotlin
val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    middlewares = listOf(
        BusLockingMiddleware(Clock.System),
        MessageLogger(logger, LogLevel.DEBUG, LogLevel.INFO, LogLevel.ERROR),
    )
)
```

### Built-in Middleware

- **`MessageLogger`** — Logs message dispatch, completion, and errors at configurable log levels
- **`BusLockingMiddleware`** — Prevents concurrent message handling with a configurable timeout

## Unit of Work

Commands execute within a Unit of Work that manages three phases:

1. **Primary work** — The command handler executes
2. **Secondary work** — Domain event handlers run (within the same transaction)
3. **Post-commit work** — Integration event handlers run (after transaction commit)

To opt into transactional execution, pass a `TransactionManager` to the bus to apply it globally:

```kotlin
val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    transactionManager = myTransactionManager,
)
```

And then implement `ExecuteInTransaction` on the command handlers to run within the transaction:

```kotlin
class TransferFundsHandler() : CommandHandler<TransferFunds, BusResult<Unit, MessageFailure>>(),
    ExecuteInTransaction<TransferFunds, BusResult<Unit, MessageFailure>> {

    override suspend fun handle(message: TransferFunds): BusResult<Unit, MessageFailure> {
        // This runs inside a transaction
        return BusResult.success(Unit)
    }
}
```

You can also provide a `TransactionManager` to individual command handlers:

```kotlin
class TransferFundsHandler(
    override val transactionManager: TransactionManager
) : CommandHandler<TransferFunds, BusResult<Unit, MessageFailure>>(),
    ExecuteInTransaction<TransferFunds, BusResult<Unit, MessageFailure>>
```

## KSP Code Generation

For compile-time type-safe handler resolution with zero reflection, use the KSP code generation module. This
requires adding no annotations or coupling to anything outside your message handlers (which are already coupled
to Kbus).

### Setup

```kotlin
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

```kotlin
@LoadMessageHandler
class PlaceOrderHandler(
    private val orderRepository: OrderRepository,
    private val paymentService: PaymentService,
) : CommandHandler<PlaceOrder, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: PlaceOrder): BusResult<String, MessageFailure> {
        // ...
        return BusResult.success(orderId)
    }
}
```

### Generated Code

The KSP processor generates:

- **`AllDependencies`** — Interface listing all required dependencies (implement this to provide them)
- **`AllHandlers`** — Interface with factory methods for every handler
- **`HandlerFactory`** — Factory that creates handlers with their dependencies resolved
- **`CompileTimeLoadedMessageBus`** — A type-safe bus with strongly-typed `execute` and `fetch` methods for each message
  type
- **`AutoLoader`** — Auto-loading support for runtime handler registration

### Using the Generated Bus

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
    middleware = listOf(MessageLogger(logger)),
)

// Strongly-typed dispatch — compile error if message type is wrong
val result = bus.execute(PlaceOrder(items))
```

### Submodules

For multi-module projects, submodules can export their handler metadata for the main module to consume. You must
provide a package name for the indexes. This prevents trying to load indexes from a dependent library that uses
Kbus.

```kotlin
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

```kotlin
// Value Object — equals() and hashCode() required
class Money(val amount: BigDecimal, val currency: String) : ValueObject<Money>()

// Entity
class Order(override val id: OrderId, val items: List<Item>) : Entity<Order>()

// Aggregate Root
class ShoppingCart(override val id: CartId) : AggregateRoot<ShoppingCart>()
```

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
