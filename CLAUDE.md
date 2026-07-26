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
`List<AutoPublishRegistration<*>>` for `AutoPublishIntegrationEvents`. Submodules (`isSubModule=true`) generate only a
`DependencyIndex` with `@KbusIndex` metadata (including any auto-publish opt-ins) instead of full code.

**Bounded context identity.** `kbus.boundedContextIdentity` is a per-Gradle-module KSP build arg, orthogonal to
`kbus.subModuleName` (a bounded context spans several Gradle modules — several
`subModuleName`s, one `boundedContextIdentity`). It is stamped onto every `HandlerData` the producing module's KSP run
creates, round-trips through `HandlerInfo.module` on `@KbusIndex`, and is **never inferred by the consumer** — that
invariant is what would make a later `@KbusModule("…")` annotation a drop-in override rather than a rewrite. `""` means
unassigned (folded into the default context) and is deliberately distinct from a context literally named `"default"`;
`DependencyIndexGenerator` therefore always emits `module`, because `IndexParser.findArgument` errors on a missing *or*
null value. Identity does not participate in `HandlerConflictPolicy` — cross-module command ownership is a later stage.
`BusGenerator` groups `INTEGRATION` event handlers by identity and emits one `GenerationHandlerLocator`
per context (all sharing the one `HandlerFactory`), passes them as `contexts` to the super-constructor, and exposes one
`CompileTimeIntegrationEventMapper` accessor per context — `bus.orders`, `bus.default`. The bus-wide
`integrationEventMapper` is **gone** (ambiguous with N contexts); `domainEventMapper` stays.

### Handler Locators

- `GenerationHandlerLocator` — Uses KSP-generated factory (preferred)
- `PersistingHandlerLocator` — Runtime registration for dynamic scenarios

### Middleware Pipeline

Composable middleware chain wrapping handler execution. Built-in: `MessageLogger`, `Locker`.

### Unit of Work

Commands execute in a `UnitOfWork` managing three phases: primary work → secondary work (domain events, within
transaction) → post-commit work (integration events).

### Event Routing

The `kbus-core` event code is split into three sub-packages under `messages.event`, mirroring the three stages:
`publish` (`DirectPublisher`, `IntegrationEventPublisherFactory`, `AutoPublishesFrom`),
`routing` (`EventRouter`, `AggregateException`), and `dispatch` (`EventDispatcher`,
`DomainEventDispatcher`, `IntegrationEventMapper`, `IntegrationEventObserverRegistry`,
`ObservableEventMapper`). The message-type definitions (`IntegrationEvent`, `DomainEvent`) stay at the
`messages.event` root. Contracts (`kbus-contracts`) keep a flat `messages.event` package.

The seam between publish and dispatch, so dispatch is what happens on the far side of a route rather than part of
publishing itself. `EventRouter` (bus-owned, `kbus-core`) owns the
`IntegrationEventObserverRegistry` (moved off `EventDispatcher`, which no longer emits) and a list of
`EventDestination`s (contracts, like `OutboxStore` — the extension point external transports and per-module inboxes will
implement). `route(envelopes: List<EventEnvelope>)` emits each event to
`observe()` collectors once per routing attempt, before fan-out, then attempts delivery to *every*
accepting destination — collecting failures into a thrown `AggregateException` rather than stopping at the first one, so
healthy destinations still get the event immediately. Observation is **at-least-once**: `route` re-emits on every call
and a failed destination is re-routed by the poller, so observers see a retried event again; exactly-once would require
deduping on
`EventEnvelope.id` against a durable store (the same id-keyed machinery a consuming inbox will need).
`EventEnvelope(id, event)`
(contracts, `messages.event` package, replacing `OutboxEntry`) is what survives every hand-off from publish through
routing to delivery; its id — minted once, at the ingress boundary, via
`EventEnvelope.of` — is what an at-least-once consumer (the outbox poller, later an inbox) dedupes on.

`BoundedContext` (`kbus-core`, package `com.jimbroze.kbus.core.module`) is the local-dispatch
`EventDestination` and the only destination today: it owns a handler slice and dispatches to it via
`HandlerLocator` + `EventDispatcher`. A bus holds **one context per `BoundedContextId`**, passed as
`contexts: Map<BoundedContextId, HandlerLocator>` on the bus constructor; empty ⇒ a single implicit
`BoundedContextId.DEFAULT` context over the bus's shared locator (behaviour-preserving for non-modular apps).
`appliesTo` is a real, **lazily derived** subscription set: `Subscriptions` (a `fun interface`, the seam Stage 3's
`DeclaredSubscriptions` drops into) with `LocatorSubscriptions` delegating to
`HandlerLocator.hasHandlersFor` — which must never instantiate handlers (it runs on every route), so both locators
answer from `PersistingEventMapper.hasMappingFor`. Laziness is an invariant, not an optimisation: handlers are commonly
registered *after* the bus is constructed, so a construction-time snapshot would silently drop (and, with an inbox, fail
to capture) everything that arrived first. Per-context locators are used for integration-event lookup **only** —
commands, queries and domain events still resolve through `BaseMessageBus.handlerLocator`.

Consequences of N contexts, all deliberate: the dispatch middleware chain runs **once per subscribing context** (a
`Locker` acquires once per context, sequentially — `EventRouter.route` iterates destinations serially); an event **no**
context subscribes to runs the chain **zero** times and is silently accepted and acked (with an outbox: `markPublished`,
not retried forever); and `observe()` is unchanged — router-level, once per routing attempt, before fan-out. `observe`
is a **bus-level diagnostic tap, not a subscription mechanism**; treating it as one would dissolve context isolation.
One flaky context currently leaves the whole entry unpublished, so the poller re-routes to *every*
context and healthy ones re-dispatch each cycle — per-destination ack is a Stage 3 (inbox) concern.
`DirectPublisher` is the no-outbox `IntegrationEventPublisher` ingress — it mints envelopes and calls `router.route`
immediately, with no durability. Bus wiring (`BaseMessageBus`): `BoundedContext` → `EventRouter` →
`DirectPublisher` / `OutboxCoordinator` → `IntegrationEventPublisherFactory`. `observe()` delegates to
`router.observerRegistry`, not `EventDispatcher`. A destination that throws is not acknowledged — with an outbox
configured, that leaves the entry unpublished for the poller to retry; this is the whole ack mechanism, and routing has
no dependency on the outbox otherwise — every publish path, with or without one, goes through the router.

### Transactional Outbox

Opt-in via `OutboxConfig` on the bus constructor (a peer of `TransactionManager`, NOT middleware).
`UnitOfWork` has zero knowledge of the outbox. Instead, `CommandInvocation<TResult>` (`unitOfWork` +
`integrationEventPublisher`) is the per-command scope threaded through the dispatcher, dependency factory, and
middleware context in the slots `UnitOfWork<*>` used to occupy. `CommandInvocationFactory`
(bus-owned) decides once, at creation time, which publisher applies — the outbox or the direct publisher — and, when an
outbox is configured, passes the unit of work to `TransactionalOutbox`'s constructor, which self-registers into
`UnitOfWork`'s generic phase API: `flush()` (saves buffered entries to the `OutboxStore`) is registered as the *first*
secondary work item, so it runs inside the transaction; `drain()` (fire-and-forget delivery via the `EventRouter`) is
registered as post-commit work, gated by `OutboxConfig.opportunisticDrain`. `TransactionalOutbox.publish` defers the
actual store write until `flush()` runs — publishes that arrive after `flush()` (e.g. from SECONDARY/POST_COMMIT
handlers) save immediately instead. This makes every publish path, including command-chain middleware (which runs before
the transaction opens), rollback-safe: if primary work throws, `flush()` never runs and nothing is ever staged. A
bus-owned poller is the at-least-once delivery guarantee; the drain is only a latency optimisation. The poller is owned
end to end by
`OutboxCoordinator` (bus-owned, one per bus): its `startPolling()` is called from `BaseMessageBus.start()`, not from any
constructor — no background work ever begins before the application explicitly starts the bus. `TransactionalOutbox`,
`ImmediateOutboxPublisher`, `OutboxCoordinator`, and `OutboxPoller` all hold an `EventRouter` (not a bare
`IntegrationEventPublisher`); `deliverAndMark` calls
`router.route(listOf(envelope))` per entry, so the router's all-or-nothing-per-entry ack semantics are what leaves an
entry unpublished for the poller to retry.

When an outbox is configured, **every** integration publish routes through it, not just command-scoped ones.
`IntegrationEventPublisherFactory.create(unitOfWork)` returns a
`TransactionalOutbox` when given a unit of work, and otherwise falls back to
`OutboxCoordinator.immediatePublisher` — a stateless, long-lived `ImmediateOutboxPublisher`
that saves to the `OutboxStore` immediately (no transaction to defer to) and opportunistically drains, sharing the same
`deliverAndMark` delivery loop as `TransactionalOutbox.drain` and
`OutboxPoller.pollOnce`. This covers query middleware and every other non-command publish path (previously
fire-and-forget with no durability). Only when no outbox is configured does
`create` fall through to the direct publisher.

Command handlers, domain event handlers, and integration event handlers all reach the relevant publisher through the
same `CanPublishIntegrationEvent` mixin (`setPublisher`/`publish`):
`CommandExecutor` calls `handler.setPublisher(invocation.integrationEventPublisher)`;
`EventDispatcher.dispatchDomainEvent` does the same for each domain event handler before dispatch, and separately swaps
the publisher into the middleware context (AutoPublish path);
`EventDispatcher.dispatchIntegrationEvent` resolves the context once and sets its publisher on every handler
implementing `CanPublishIntegrationEvent`, making integration-event publish chains possible and outbox-durable end to
end.

### Result Types

`BusResult<TValue, TMessageFailure>` sealed class with `Success` and `Failure` subtypes. Failures use `FailureReason`
interface.

### Dependency Injection in Generated Code

Constructor parameters of `@LoadMessageHandler` classes become dependencies. Types: `PROPERTY` (direct reference),
`FUNCTIONAL` (lambda factory), `COMMAND` (from `CommandDependencies`).

## Design Philosophy

- **Pre-V1: no backwards-compatibility obligations.** Don't hold back on refactors or breaking changes when they are
  better — clearer, more readable, more maintainable — or better suit the framework's goals and design intents.
- **Explicit wiring over ambient state.** Dependencies flow through constructors, factories, and parameters — never
  through coroutine-context elements, statics, or reflection. Coroutine context is reserved for middleware-internal
  concerns (e.g. `LockingMiddleware`'s re-entrancy token), not core semantics. If a component needs per-command state,
  thread it through the object graph.
- **`UnitOfWork` is the bus's transaction; phases are its only extension surface.** It knows
  primary/secondary/post-commit ordering and nothing else — no outbox, no publisher. Per-command wiring (which publisher
  applies) lives on `CommandInvocation`, composed by
  `CommandInvocationFactory`; outbox flush/drain registration is `TransactionalOutbox`'s own responsibility, done via
  the `UnitOfWork` passed into its constructor, through the same generic
  `addSecondaryWork`/`addPostCommitWork` API. `CommandExecutor` stays a thin orchestrator; don't accumulate concerns
  there. Middleware is for cross-cutting invocation concerns (logging, locking), not transactional semantics.
- **Integration events decouple publish from dispatch — and routing is the seam between them.**
  Publish → route → dispatch is three stages, not two: publishing (durable save, in-transaction) and dispatching (async
  delivery to handlers, post-commit) are separate steps, and every publish path — with or without an outbox — hands off
  through `EventRouter` before a destination ever dispatches. A command's return never awaits integration handler
  execution; delivery is at-least-once via the outbox poller, and the post-commit drain is opportunistic fire-and-forget
  (awaiting it can deadlock with held locks).
- **Producers own event data; consumers own consumption policy.** Handler-side concerns (domain `dispatchTiming`) sit on
  handlers; event-level `errorStrategy` doubles as the outbox's ack semantics (`FailFast` = retry until handlers
  complete).
- **No background work starts from a constructor.** A bus with an outbox and/or
  `LifecycleAwareMiddleware` only begins that work when the application calls `start()`; `stop()`
  is `suspend` and deterministic (cancels and joins the bus's root job). Buses with neither need no
  `start()` call — dispatch works immediately, at zero ceremony.

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
