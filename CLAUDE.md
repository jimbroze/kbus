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

1. **Index phase** — Scans `@KbusIndex` annotations to load dependency metadata from libraries. Every module reads
   the indexes it can see, so what a module *learned* is tracked apart from what it *declares* — its own index
   carries only its own declarations
2. **Handler phase** — Scans `@LoadMessageHandler` to extract handler definitions and dependencies
3. **Event phase** — Scans `@LoadEvent` to make events known to the processor; if the event's companion implements
   `AutoPublishesFrom`, records an auto-publish definition (integration event ← domain event)

Generates: `ContainerInterface`, `AutoLoader`, a typed `Bus` class, a handlers interface, handler factory and typed
nested command executor **per bounded context**, and (only when at least one `@LoadEvent`/`AutoPublishesFrom` opt-in
exists) `generatedAutoPublishRegistrations` — a
`List<AutoPublishRegistration<*>>` for `AutoPublishIntegrationEvents`. Submodules (`isSubModule=true`) generate only a
`DependencyIndex` with `@KbusIndex` metadata (including any auto-publish opt-ins) instead of full code.

`kbus.boundedContextIdentity` is a per-Gradle-module KSP build arg naming the bounded context a module's handlers
belong to, orthogonal to `kbus.subModuleName` (one context can span several Gradle modules). It is stamped onto
handler metadata at generation time and never inferred by the consumer. The generated bus groups handlers by identity
and emits one context per identity.

A context's factory holds only that context's handlers, so a command another context owns is unresolvable there
rather than merely refused — the isolation is structural. Its typed nested executor covers the commands its module
can see, and a module can only consume an executor interface generated *upstream* of it: KSP cannot resolve a type
the same run is about to write.

### Handler Locators

- `GenerationHandlerLocator` — Uses KSP-generated factory (preferred)
- `PersistingHandlerLocator` — Runtime registration for dynamic scenarios

### Middleware Pipeline

Composable middleware chain wrapping handler execution. Built-in: `MessageLogger`, `Locker`. Every middleware
declares a `MiddlewareScope`, with no default: whether it re-runs for a command nested inside another command's
invocation is the author's call, not the framework's.

### Unit of Work

Commands execute in a `UnitOfWork` managing three phases: primary work → secondary work (domain events, within
transaction) → post-commit work (integration events).

### Event Routing

Integration events go through three stages — publish → route → dispatch — mirrored by three sub-packages under
`kbus-core`'s `messages.event`: `publish`, `routing`, `dispatch`. `EventRouter` is the seam: publishers hand
`EventEnvelope`s to it, and it fans them out to `EventDestination`s (local context dispatch today; external transports
later). Delivery is at-least-once — a destination that throws is not acknowledged, so the entry is retried.

### Event Ordering

Domain events are ordered — by dispatch phase, then by publication order within a phase — because they run in one
process inside a command, with no retries. Integration events have **no ordering guarantee** and the API offers no way
to request one: publish, routing and delivery are all concurrent, and retry reorders by construction. The relay's
concurrency limit is a throughput knob, not an ordering feature; a limit of 1 orders one batch in one process and
nothing wider. A consumer needing order carries a sequence number on the event and rejects stale arrivals.

### Bounded Contexts

A bus takes a list of `BoundedContext`s, each an authored declaration pairing an id with a `HandlerLocator` and its
handler registrations. An empty list means a single implicit default context. Commands and queries resolve by asking
each context whether it owns the message; exactly one owner is required. Event dispatch fans out to every subscribing
context, so the dispatch middleware chain runs once per subscribing context.

Handler registration closes when the bus is constructed, enforced by the API's shape rather than a runtime flag:
registration methods live on `ContextRegistration`, only ever reachable from a construction-time lambda.

### Per-Context Inbox

A context can opt into its own inbox, giving it durable, independently-acked consumption: routed envelopes are saved
to that context's own store and dispatched by a background pump, so one context's failures don't affect another's.
Isolation is structural — each context supplies its own store instance. Dedupe on `EventEnvelope.id` is the
load-bearing invariant; a consumed id must remain a tombstone.

### Transactional Outbox

Opt-in via `OutboxConfig` on the bus constructor (a peer of `TransactionManager`, not middleware). `UnitOfWork` has no
knowledge of the outbox; the per-command `CommandInvocation` carries which publisher applies, and `TransactionalOutbox`
registers its own flush (inside the transaction) and drain (post-commit) through `UnitOfWork`'s generic phase API. When
an outbox is configured, every integration publish routes through it, not just command-scoped ones. The bus-owned
poller is the at-least-once delivery guarantee; the opportunistic drain is only a latency optimisation.

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
  primary/secondary/post-commit ordering and nothing else — no outbox, no publisher. `CommandExecutor` stays a thin
  orchestrator; don't accumulate concerns there. Middleware is for cross-cutting invocation concerns (logging,
  locking), not transactional semantics.
- **Integration events decouple publish from dispatch — and routing is the seam between them.** A command's return
  never awaits integration handler execution; delivery is at-least-once and unordered. Don't offer a guarantee the
  framework cannot hold across processes and retries — an ordering option that quietly degrades under load is worse
  than none.
- **A command's transaction boundary is the execution path that reached it.** Same context, through the
  handler's own dependencies: always shares the caller's Unit of Work. Cross-context, through the bus: never
  can, because that path holds no handle to a caller's. It is not a setting either side can override.
- **Producers own event data; consumers own consumption policy.** Handler-side concerns sit on handlers; a consumer
  that needs stronger guarantees than a producer declared states that explicitly rather than having it inferred.
- **Registration closes when the bus is built.** A command or query has exactly one owning context, so ownership must
  be settleable at construction — conflicts are reportable against the wiring, not against the first dispatch.
- **No background work starts from a constructor.** Outboxes, inboxes and lifecycle-aware middleware only begin work
  on `start()`; `stop(gracePeriod)` is suspend and deterministic. A bus with none of these needs no `start()` call.

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
- **Naming is the first tool; a comment is the fallback.** Before writing one, try to make it
  unnecessary by renaming the class, function, property or variable. A comment that explains what a
  name should have said is a naming bug.
- **Describe the interface, not the implementation.** A comment on a declaration is for its callers:
  what it guarantees, what it requires, what it does *not* promise. Don't narrate the body — how it
  is implemented must be free to change without the comment going stale.
- **Never name a type or file the code doesn't already import.** A comment that mentions some other
  class, module or test is coupling, and coupling a reader can't follow to a definition and a
  compiler can't check. Describe the constraint in its own terms instead.

## Naming

- A name should say *what a value is*, not just its shape. `known`/`unknown`, `result`, `data`,
  `item`, `scope` force a reader to go read the surrounding code to answer "known **what**?" — the
  type or the local block only narrows that far. Prefer `busContextIds` over `known`,
  `fireAndForgetScope` over `scope`, `storeIdsWithNoContext` over `unknown`.
- This applies to locals, parameters, and private fields, not just public API — a name that's clear
  only because of a comment above it, or only by tracing every call site, is not clear.
  Constructor/function parameters that share a concept with another name already used for it
  elsewhere in the file or module (e.g. an ack-strategy-override function) should reuse that name,
  not a shorter generic one — consistency across a call chain is itself a form of clarity.
- It's fine — encouraged, even — to be a little verbose if it makes the name unambiguous to a new
  developer. `pumpScope` beats `scope`; `storeIdsWithNoContext` beats `unknown`. A few extra
  characters are cheap; a name a reader has to decode by reading the function body is not.
- Watch for names that collide with language keywords or shadow their everyday meaning (e.g. an
  `override` parameter next to Kotlin's `override` modifier) — pick something unambiguous even if
  it's longer.
- **Discuss naming with me before settling on it.** When introducing a new public type, function or
  concept — or renaming an existing one — propose two or three candidate names with a one-line
  trade-off each and ask which I prefer, rather than picking one and moving on. Local variables and
  private helpers don't need this; anything a reader of the API will see does.

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
- **What the processor *refuses* to generate is tested by compiling it**, in `kbus-generation`'s `jvmTest` source set,
  which runs the real compiler and KSP over source a user would have written. A rejection has to reach the author, so
  those tests assert the message and its attribution, not just that the build failed — report one with `logger.error`
  against the offending declaration and skip the handler, never by throwing, which KSP reports as an internal error
  with no source attached. The harness is JVM-only and cannot check an accepted *event* handler: the `.loaded` property
  generated for one is annotated for every target, which a single-platform compilation rejects. Generated output is
  asserted against KotlinPoet in `commonTest`; the whole pipeline end to end is covered by the example modules.

## Process

- Always update README.md if adding or changing features
- One logical change per commit. When a task covers several independent changes — separate review
  comments, unrelated fixes, a rename alongside a behaviour change — commit each one on its own, even
  when they land in the same session and touch the same files. Each commit should be revertable
  without dragging the others with it.

## Editing This File

- **Don't edit CLAUDE.md unprompted.** Ask first, and say what you want to add and why. This applies
  to expanding an existing section as much as to adding a new one.
- Architecture sections stay at overview altitude: what a thing is for and the one invariant that
  makes it work. No constructor signatures, parameter orders, visibility modifiers, package layouts,
  wiring chains, or test method names — those live in the code and will be stale the next time it's
  refactored.
- This file is instructions, not a design record. Never write a decision here to preserve or justify
  it — that is what commit messages, PRs and the README are for. A rationale recorded here comes back
  as settled policy and gets used to defend the choice instead of re-examining it.
