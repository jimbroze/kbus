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

**Detekt type resolution is currently broken for this project (detekt 1.23.x).** The
`detektJvmMain`/`detektJvmTest` tasks (the only ones with type resolution, required for
type-aware rules like `ForbiddenMethodCall`) source from `compilation.kotlinSourceSets`, which
does not pull in `commonMain`/`commonTest` transitively — since this project has no
`src/jvmMain`/`src/jvmTest` directories, those tasks silently report `NO-SOURCE` and never
analyze anything. The plain `detekt` task (`source.from("src")` in `build.gradle.kts`) works but
has no type resolution. Detekt 2.0 fixes the source-set wiring (types are analyzed via the Kotlin
Analysis API and tasks are generated per compilation with type resolution "wired up
automatically") but is a breaking Gradle-plugin migration, not a drop-in bump — worth doing as
its own follow-up before relying on any type-aware detekt rule.

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
`BusGenerator` groups every handler kind (command, query, domain, integration) by identity and emits one
`GenerationHandlerLocator` per context (all sharing the one `HandlerFactory`, disambiguated by its own
`contextIdentity`), passes them as `contexts` to the super-constructor, and exposes one `BoundedContext` accessor per
context — `bus.orders`, `bus.default` — the one registration point for both `addEventHandlers` and
`addDomainHandlers`. There is deliberately no bus-wide `integrationEventMapper` or `domainEventMapper`: with N
contexts, "which context?" has no answer for either. A generated command convenience function
(`bus.placeOrder(...)`) knows its handler's owning context statically, so it bakes in that `BoundedContextId` and
resolves it through `BaseMessageBus.domainEventDispatcherFor` rather than searching every context for an owner.

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

`BoundedContext` (`kbus-core`, package `com.jimbroze.kbus.core.module`) is an **authored declaration**, not a runtime
object: a user constructs one with an id and a `HandlerLocator`, and registers domain/integration handlers on it via
`addDomainHandlers`/`addEventHandlers`, which delegate through `EventMapperProvider` — a `HandlerLocator` supertype, so
every locator implements it by construction. A bus takes
**`contexts: List<BoundedContext>`** on its constructor; empty ⇒ a single implicit `BoundedContextId.DEFAULT` context
over the bus's shared locator (behaviour-preserving for non-modular apps); duplicate ids among an explicit list throw
`IllegalArgumentException` at construction.

The bus derives one internal `ContextRuntime` per `BoundedContext` — the local-dispatch `EventDestination` and the only
destination today — once its own middleware, scope and dispatcher exist; a `BoundedContext` cannot own that itself,
since none of that wiring is available at the point a user constructs one. `ContextRuntime` holds a **deferred**
reference to that context's own `EventDispatcher` (`() -> EventDispatcher`, resolved on first `deliver`/
`dispatchDomainEvent`, not at construction) — not vestigial, but load-bearing: a dispatcher's `contextFactory`
transitively depends on the router these runtimes feed into, so building them eagerly with a concrete `EventDispatcher`
would be a circular initializer. `appliesTo` is a
real, **lazily derived** subscription set: `Subscriptions` (a `fun interface`, the seam Stage 3's
`DeclaredSubscriptions` drops into) with `LocatorSubscriptions` delegating to
`HandlerLocator.hasHandlersFor` — which must never instantiate handlers (it runs on every route), so both locators
answer from `PersistingEventMapper.hasMappingFor`. Laziness is an invariant here, not an optimisation, and it is
scoped to events specifically: **event** handlers are registerable after the bus exists (see the registration-sealing
rule below), so a construction-time snapshot would silently drop everything registered afterwards.

`ContextRuntime` is also that context's `DomainEventDispatcher`: `dispatchDomainEvent` delegates to the same deferred
`EventDispatcher` reference `deliver` uses, so integration and domain dispatch for one context share a single
dispatcher instance rather than two parallel wiring paths. `CommandExecutor.execute` and
`CommandInvocationFactory.create` both take the owning context's `DomainEventDispatcher`, which becomes
`CommandInvocation.domainEventDispatcher`; `MessageBus.execute` passes the `ContextRuntime` that owner lookup
resolved. Passing the resolved dispatcher rather than a `BoundedContextId` is what makes wrong-context domain dispatch
unrepresentable: there is no id left to look up, so `DefaultCommandDependenciesFactory` takes no lookup function and
`InvocationDomainEventPublisher.baseDispatcher` is non-null.

**Handler registration closes when the bus is constructed — for commands and queries.** `HandlerLocator.seal()` is
called on every context's locator from `BaseMessageBus`'s `init` (via `BoundedContext.seal()`), and
`MessageHandlerFactoryStore.registerHandlers`/`removeHandlers` then throw `HandlerRegistrationSealedException`
(`kbus-core`, `registry`). Sealing is a precondition, not a bug fix: owner lookup is still lazy today, so a late command
handler *would* in fact be found. What a closed handler set buys is the pair of properties the bus was built for —
construction-time conflict detection, and an eager owner map in place of the per-dispatch scan over every context.
Neither is sound while registration stays open. Sealing must be idempotent — two contexts may share one
`HandlerFactoryStoreCollection`. `GenerationHandlerLocator.seal()` is a no-op: its commands and queries come from a
generated factory fixed at compile time.

**Event handlers are deliberately exempt from sealing**, and this is a constraint of the generated API, not an
oversight: a generated bus exposes its contexts only *after* construction, so `bus.billing.addEventHandlers(...)` on a
live bus is its documented registration path (README). That exemption is exactly why `Subscriptions` stays lazy while
command/query lookup may safely become eager. Closing it would mean giving the generated bus a pre-construction
registration hook.

**Commands and queries resolve by owner lookup**, not through `BaseMessageBus.handlerLocator` directly:
`execute`/`fetch` ask each context's `HandlerLocator.hasHandlerFor` (a command/query analogue of `hasHandlersFor`, same
no-instantiation contract) and require exactly one owner — zero throws the existing `MissingHandlerException`, two or
more throws `AmbiguousHandlerException(messageClass, contextIds)` (`kbus-contracts`, beside `MissingHandlerException`),
since a command or query is single-owner by contract and must not be resolved by list order. `GenerationHandlerLocator`
needs its own `contextIdentity` (`""` for default) to answer this, because contexts produced from the same Gradle
module currently share one generated `HandlerFactory` instance — `GenerationHandlerFactory.commandModule`/
`queryModule` report the owning identity (or `null`) per message class, generated from `HandlerData.module`, and the
locator compares it against its own identity. `BaseMessageBus.handlerLocator` itself is **not** a lookup candidate once
`contexts` is non-empty — it only backs the implicit default context when `contexts` is empty — so a hand-rolled bus
that passes explicit `contexts` must register every command/query handler through one of those contexts' locators.
Domain-event dispatch is per-context too, via each context's own `ContextRuntime`-held `EventDispatcher` — see the
`ContextRuntime`/`CommandInvocation.domainEventDispatcher` wiring above.

Consequences of N contexts, all deliberate: the dispatch middleware chain runs **once per subscribing context** (a
`Locker` acquires once per context, sequentially — `EventRouter.route` iterates destinations serially); an event **no**
context subscribes to runs the chain **zero** times and is silently accepted and acked (with an outbox: `markPublished`,
not retried forever); and `observe()` is unchanged — router-level, once per routing attempt, before fan-out. `observe`
is a **bus-level diagnostic tap, not a subscription mechanism**; treating it as one would dissolve context isolation.
A context with no [inbox](#per-context-inbox) still has this behaviour: one flaky context leaves the whole entry
unpublished, so the poller re-routes to *every* context and healthy ones re-dispatch each cycle
(`MessageBusMultiContextTest.aFailingContextLeavesTheEntryUnpublished_andHealthyContextsReDispatchOnRetry` pins it). A
per-context inbox is the fix — see `### Per-Context Inbox`.
`DirectPublisher` is the no-outbox `IntegrationEventPublisher` ingress and the one caller-facing integration-publish
path — the inbox pump, the outbox poller and the outbox drain are all background coroutines nobody awaits. It mints
envelopes and partitions the batch by each event's own `errorStrategy` (not per event, or `observe()`/`EventInbox
.deliver` would be multiplied): a `FireAndForget` group is launched on its scope (`MessageBus` wires in
`eventDispatcherScope`) so the caller doesn't wait on it, every other group is routed and awaited so a destination
failure still propagates to the publisher. No durability either way. Bus wiring (`BaseMessageBus`): `ContextRuntime` →
`EventRouter` → `DirectPublisher` / `OutboxCoordinator` → `IntegrationEventPublisherFactory`. `observe()` delegates to
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
`ImmediateOutboxPublisher`, and `OutboxCoordinator` all hold an `EventRouter` (not a bare `IntegrationEventPublisher`)
and delegate their delivery loop to a shared `EnvelopeRelay` (`core.messages.event.relay`) — the one fetch/deliver/ack
loop behind the outbox drain, the immediate publisher, the outbox poller, and every [inbox](#per-context-inbox) pump.
`outboxRelay(store, router)` is the outbox-specific factory: `deliver = { router.route(listOf(it)) }`, so the router's
all-or-nothing-per-entry ack semantics are what leaves an entry unpublished for the poller to retry. `EnvelopeRelay`'s
`relay()` catches and rethrows `CancellationException` rather than swallowing it — required once N per-context inbox
pumps can be cancelled mid-dispatch on every `stop()`, so a cancelled tick never acks an envelope whose handlers were
cancelled.

When an outbox is configured, **every** integration publish routes through it, not just command-scoped ones.
`IntegrationEventPublisherFactory.create(unitOfWork)` returns a
`TransactionalOutbox` when given a unit of work, and otherwise falls back to
`OutboxCoordinator.immediatePublisher` — a stateless, long-lived `ImmediateOutboxPublisher`
that saves to the `OutboxStore` immediately (no transaction to defer to) and opportunistically drains, sharing the same
`EnvelopeRelay`-based delivery loop as `TransactionalOutbox.drain` and `OutboxCoordinator`'s poller. This covers query
middleware and every other non-command publish path (previously fire-and-forget with no durability). Only when no
outbox is configured does `create` fall through to the direct publisher.

Command handlers, domain event handlers, and integration event handlers all reach the relevant publisher through the
same `CanPublishIntegrationEvent` mixin (`setPublisher`/`publish`):
`CommandExecutor` calls `handler.setPublisher(invocation.integrationEventPublisher)`;
`EventDispatcher.dispatchDomainEvent` does the same for each domain event handler before dispatch, and separately swaps
the publisher into the middleware context (AutoPublish path);
`EventDispatcher.dispatchIntegrationEvent` resolves the context once and sets its publisher on every handler
implementing `CanPublishIntegrationEvent`, making integration-event publish chains possible and outbox-durable end to
end.

### Per-Context Inbox

Opt-in per context: a `BoundedContext` declares its own `ContextInbox(store, ackPolicy)`. `InboxConfig` on the bus
constructor (`inbox` param, last, after `contexts`) carries only the tuning every pump shares — `pollInterval`,
`batchSize`, `opportunisticDispatch` — so a null `inbox` means default tuning, not "no inboxes". Packages: `contracts.inbox` (`InboxStore`), `core.messages.event.relay`
(`EnvelopeRelay`, shared with the outbox), `core.module.inbox` (`EventInbox`, `InboxCoordinator`, `InboxConfig`),
`core.infrastructure.inbox` (`InMemoryInboxStore`).

`EventInbox` is a **decorator**, not a field on `ContextRuntime` — `EventInbox(inner: EventDestination, store, …) :
EventDestination` wraps a context runtime so `ContextRuntime` itself is untouched (no branch, no second entry point). Its
`deliver` collapses to *save durably and return*: `store.save(envelopes)`, then (if `opportunisticDispatch`)
`pumpScope.launch { drain() }`. `drain()`/`pump()` are `relay.pollOnce()`/`relay.poll()` on an internal `EnvelopeRelay`
whose `fetch = store::fetchPending`, `deliver = { inner.deliver(listOf(it)) }` (per-entry, so ack is per-entry — a
throwing handler for one envelope leaves only that envelope unacked), `ack = store::markConsumed`.

**Per-context store instances are the structural isolation.** Each declaring context supplies its own store instance, so
a context's pump physically cannot see another context's rows — this, not a shared table with a context column, is what
makes contexts independent. A context declaring no `ContextInbox` keeps synchronous dispatch; `InboxCoordinator`
(mirrors `OutboxCoordinator`'s shape: config-or-null + scope in, `destinations: List<EventDestination>` derived once,
idempotent `startConsuming()`, no `stop` — cancellation is the bus's root job) wraps only the declaring ones. Declaring
the store on the context rather than in a map keyed by `BoundedContextId` is what removes the old fail-fast for an id
the bus has no context for: naming a nonexistent context is no longer expressible.

**Dedupe-on-id is the load-bearing invariant, not routing.** `EventRouter.route` is unchanged — still all-or-nothing per
envelope, still fans a whole envelope to every destination. What kills the no-inbox amplification
(`MessageBusMultiContextTest`'s pinned test, above) is that a re-routed envelope hits `InboxStore.save`, which must be
idempotent per `EventEnvelope.id` — silently dropping an id that is still pending *or* already consumed. So a healthy
context's `deliver` is a no-op on retry (already acked upstream), and a sick context's failure is a *handler* failure
inside its own pump, invisible to the router. `markConsumed`, unlike `OutboxStore.markPublished`, must **not** forget
the id — a consumed id is a dedupe tombstone that must outlive the producing outbox's retry horizon, or a late
redelivery slips back in as a genuine duplicate.

`EnvelopeRelay`'s single-flight mutex (`pollMutex`, held across fetch/deliver/ack) is what stops an opportunistic drain
racing a scheduled pump tick for the same inbox — both call `pollOnce`, so the loser blocks rather than double-fetching.
Per-process only; cross-process overlap is `InboxStore.fetchPending`'s problem, exactly as for the outbox.

Two axes, not one: `errorStrategy` (on the event) decides whether a handler exception ever reaches the relay —
`FailFast`/`ContinueAndAggregate` retry on failure, `FireAndForget` swallows it into `handleFailure` so the envelope is
acked regardless — while `concurrency` only ever decided *timing*, and integration dispatch (`EventDispatcher
.dispatchConcurrently`'s detach narrowed to `phase == POST_COMMIT` only, which integration events never are) now always
awaits its handlers before returning, for every `errorStrategy` and every `concurrency`. So `errorStrategy` becomes
that context's own inbox ack semantics, independent of every other context: `FailFast` retries until every handler
completes; `ContinueAndAggregate` retries the whole batch (re-running successes) on any failure; `FireAndForget` still
runs every handler to completion first, then acks regardless of the outcome — a failing handler is never retried, but
it is no longer a crash-window durability hole, since the tombstone is written only after dispatch returns.

`ContextInbox.ackPolicy` (`InboxAckPolicy`, `core.module.inbox`) is the fix for `FireAndForget`'s "never retried" edge,
applied per context rather than per event: `HonourEventStrategy` is the table above; `RequireHandlerSuccess` maps
`FireAndForget` → `ContinueAndAggregate` before dispatch, so a handler failure leaves the envelope pending like
`FailFast`/`ContinueAndAggregate` already do (those two pass through untouched — they don't need the override). The
mapping is expressed on the public `ErrorStrategy` contract type, not the dispatcher-internal `EventErrorStrategy`, so
it can sit on `ContextRuntime`'s constructor and `EventDispatcher.dispatchIntegrationEvent`'s public trailing
parameter without an internal type leaking into public API. `ContextRuntime.withAckStrategy` (`internal`, applied only
by `InboxCoordinator`, only to contexts with a configured store) returns a copy carrying the override function; a
context with no inbox is never overridden. `ackPolicy` has **no default** — either choice silently picks a side of a
durability trade-off the consumer owns (`HonourEventStrategy` acks a failed `FireAndForget` handler;
`RequireHandlerSuccess` retries it forever, there being no attempt cap or dead-letter path yet), so `InboxConfig`
requires the consumer to state it.

`EventInbox` stays `internal constructor`, built only by `InboxCoordinator` — opening it to wrap an arbitrary
`EventDestination` (e.g. a future external transport) is cheap to do later and impossible to undo once public.

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
  execution; delivery is at-least-once via the outbox poller. The *launching* of a dispatch stays fire-and-forget from
  the triggering caller's point of view (the outbox's post-commit drain, `DirectPublisher`'s routing of a
  `FireAndForget` batch) — awaiting it there can deadlock with held locks — but once launched, dispatch itself always
  awaits its own handlers before returning, which is what lets an inboxed context ack only after they complete.
- **Producers own event data; consumers own consumption policy.** Handler-side concerns (domain `dispatchTiming`) sit on
  handlers; event-level `errorStrategy` doubles as ack semantics for whatever is doing the acking — the outbox for a
  context with no inbox (`FailFast` = retry until handlers complete), or that context's own inbox once it has one,
  independent of every other context. `InboxConfig.ackPolicy` (`InboxAckPolicy.RequireHandlerSuccess`) is how a consumer
  demands stronger guarantees than a producer's declared `FireAndForget` — stated explicitly (the parameter is required,
  with no default), never inferred from context shape.
- **Registration closes when the bus is built — for commands and queries only.** A command or query has exactly one
  owning context and the bus is what resolves that owner, so ownership must be settleable at construction — that is what
  makes conflicts reportable against the wiring rather than against the first dispatch, and an eager owner map sound.
  `HandlerLocator.seal()` enforces it with a loud `HandlerRegistrationSealedException`. Event handlers stay open by necessity, not preference — the generated bus exposes its contexts only
  after construction, so registering on a live bus is its documented API, and that is precisely why a context's
  subscription set must stay derived-on-demand rather than snapshotted.
- **No background work starts from a constructor.** A bus with an outbox, an inbox, and/or
  `LifecycleAwareMiddleware` only begins that work when the application calls `start()`; `stop(gracePeriod)` is
  `suspend` and deterministic — one grace period covers each middleware's `suspend onStop()` and then in-flight
  dispatch, after which the root job is cancelled and joined regardless, itself under a bounded wait (cancellation is
  cooperative and there is no hard kill, so an uncancellable coroutine must leak rather than hang shutdown). Buses with
  none of these need no `start()` call — dispatch works immediately, at zero ceremony.

## Conventions

- Kotlin Multiplatform: all core code in `commonMain`, tests use `kotlinx.coroutines.test.runTest`
- Targets: JVM (17), JS, WASM, macOS, iOS, Linux, Windows
- Commit messages follow conventional commits: `feat(scope):`, `refactor(scope):`, `fix(scope):`

## Comments

- A comment describes the current code, not its history. Write for a reader who only ever sees this
  version — never "previously X", "used to do Y", "now does Z", "moved from A to B", or similar
  before/after framing. That belongs in the commit message, not the source.
- Default to no comment. Only add one when there's a specific, non-obvious reason for the code being
  the way it is — a hidden constraint, an invariant a change could silently break, a rejected
  alternative worth ruling out — something a reader could get wrong by inspecting the code alone.
  Don't restate what well-named identifiers already say.
- This applies to KDoc as much as inline comments. Prefer explaining an invariant or a "why" over a
  narrated changelog of how the implementation got here.

## Testing

- Everything should have unit test coverage. Use Test-driven development; creating (failing) tests before writing the
  implementation.
- Unit tests should be isolated and atomic. Wherever possible, tests should avoid requiring implicit knowledge that
  other tests have checked. If one test asserts something, another test does not need to assert the same thing.
- Use `testDoubles` for test fixtures with no dependencies on other kbus modules.
- **Every `CoroutineScope` built in test code must be parented to `backgroundScope`** — enforced by the
  `checkNoLeakedTestScopes` Gradle task (wired into `check`). A scope with no such parentage is never cancelled at
  teardown, so anything launched into it (a bus, `startPolling`/`startConsuming`) outlives the test: invisible on
  JVM/Native, but a 20+ minute CI hang on Node, which won't exit while a timer is pending. Use `backgroundScope`
  directly, or `CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher)` when the test needs
  its own cancellable scope — parentage and the choice of dispatcher are orthogonal, so that form works equally with
  `Dispatchers.Default` and with `StandardTestDispatcher(testScheduler)`. Note that
  `CoroutineScope(backgroundScope.coroutineContext + …)` *shares* `backgroundScope`'s `Job` rather than parenting to it,
  so cancelling the result would cancel `backgroundScope` itself. Test helpers and fixtures **take a scope as a
  parameter** rather than building one — a fixture-owned scope leaks into every test that uses it, which is why the
  check has no exempt directory and no opt-out marker.

## Process

- Always update README.md if adding or changing features
- One logical change per commit. When a task covers several independent changes — separate review
  comments, unrelated fixes, a rename alongside a behaviour change — commit each one on its own, even
  when they land in the same session and touch the same files. Each commit should be revertable
  without dragging the others with it.
