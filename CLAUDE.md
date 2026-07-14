# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
./gradlew build                  # Full build with tests
./gradlew allTests               # Run all tests across all platforms
./gradlew jvmTest                # Run JVM tests only (fastest)
./gradlew :kbus-core:jvmTest     # Run tests for a single module
./gradlew :kbus-core:jvmTest --tests "com.jimbroze.kbus.core.SomeTest"  # Single test class
./gradlew check                  # Run all checks (tests + linting)
./gradlew ktfmtFormat            # Format code with ktfmt (Kotlin official style)
./gradlew detekt                 # Run Detekt static analysis
```

Always format code and check static analysis after making changes.

## Project Overview

KBUS is a Kotlin Multiplatform CQRS message bus framework. It routes Commands, Queries, and Events to their handlers,
with KSP code generation for compile-time type-safe handler resolution (zero reflection).

## Module Structure

- **kbus-contracts** — API & interfaces: message types, result types. KSP Annotations
- **kbus-core** — Core framework & infrastructure: bus, middleware pipeline, handler locators, Unit of Work
- **kbus-generation** — KSP processor that generates handler factories, dependency containers, and bus classes
- **kbus-example** / **kbus-example-sub** — Example/Test modules (sub is a submodule test)
- **testDoubles** — Shared test fixtures
- **buildSrc** — Custom Gradle plugins (`kbus.multiplatform`, `kbus.publish`)

## Architecture

### Message Types & Handlers

Three message types, each with a corresponding handler base class:

- **Command<TResult>** → `CommandHandler` — State-modifying operations, single handler per command, executes within Unit
  of Work
- **Query<TResult>** → `QueryHandler` — Read-only operations, single handler per query
- **Event** → `EventHandler` / `DomainEventHandler` / `IntegrationEventHandler` — Multiple handlers per event, three
  dispatch modes (immediately, after primary work, after commit)

All handlers implement `suspend fun handle(message: TMessage)` for coroutine support.

### Code Generation (KSP)

Annotate handlers with `@LoadMessageHandler` to trigger generation. The KSP processor (`KbusProcessor`) runs in three
phases:

1. **Index phase** — Scans `@KbusIndex` annotations to load dependency metadata from libraries
2. **Handler phase** — Scans `@LoadMessageHandler` to extract handler definitions and dependencies
3. **Event phase** — Scans `@LoadEvent` to make events known to the processor; if the event's companion implements
   `AutoPublishesFrom`, records an auto-publish definition (integration event ← domain event)

Generates: `ContainerInterface`, `HandlersInterface`, `HandlersFactory`, `AutoLoader`, a typed `Bus` class, and (only
when at least one `@LoadEvent`/`AutoPublishesFrom` opt-in exists) `generatedAutoPublishRegistrations` — a
`List<AutoPublishRegistration<*>>` for `AutoPublishIntegrationEvents`.
Submodules (`isSubModule=true`) generate only a `DependencyIndex` with `@KbusIndex` metadata (including any
auto-publish opt-ins) instead of full code.

### Handler Locators

- `GenerationHandlerLocator` — Uses KSP-generated factory (preferred)
- `PersistingHandlerLocator` — Runtime registration for dynamic scenarios

### Middleware Pipeline

Composable middleware chain wrapping handler execution. Built-in: `MessageLogger`, `Locker`.

### Unit of Work

Commands execute in a `UnitOfWork` managing three phases: primary work → secondary work (domain events, within
transaction) → post-commit work (integration events).

### Result Types

`BusResult<TValue, TMessageFailure>` sealed class with `Success` and `Failure` subtypes. Failures use `FailureReason`
interface.

### Dependency Injection in Generated Code

Constructor parameters of `@LoadMessageHandler` classes become dependencies. Types: `PROPERTY` (direct reference),
`FUNCTIONAL` (lambda factory), `COMMAND` (from `CommandDependencies`).

## Conventions

- Kotlin Multiplatform: all core code in `commonMain`, tests use `kotlinx.coroutines.test.runTest`
- Targets: JVM (17), JS, WASM, macOS, iOS, Linux, Windows
- Commit messages follow conventional commits: `feat(scope):`, `refactor(scope):`, `fix(scope):`

## Testing

- Everything should have unit test coverage. Use Test-driven development; creating (failing) tests before writing the
  implementation.
- Unit tests should be isolated and atomic. Wherever possible, tests should avoid requiring implicit knowledge that
  other tests have checked. If one test asserts something, another test does not need to assert the same thing.
- Use `testDoubles` for test fixtures with no dependencies on other kbus modules.

## Process

- Always update README.md if adding or changing features
