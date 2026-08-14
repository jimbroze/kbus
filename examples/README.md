# KBUS examples

A modular monolith built with KBUS: two bounded contexts, each split into one Gradle module per
layer, with every piece of bus wiring outside the contexts themselves.

```
examples/
  contexts/
    orders/          contracts · domain · application · infrastructure · acl
    inventory/       contracts · domain · application · infrastructure
  app/               the only module that knows a bus exists
  app-manual/        the same contexts, wired without code generation
  app-contract/      the requirements both wirings must meet
  docs-samples/      the snippets Knit extracts from the root README
```

A layer's Gradle project carries its context in its name — `:examples:contexts:orders-application`
lives in `contexts/orders/application` — because a Gradle module is identified by its group and its
name alone, and every context here repeats the same layer names.

## Layers

**`contracts`** is the context's whole public API: its commands, its queries, and the integration
events it publishes. It depends on nothing — not even its own `domain` — so a consumer that reaches
for `orders`' published language cannot transitively see an `Order`.

**`domain`** holds the model and its domain events. Nothing outside the context may name it.

**`application`** holds the use cases: command handlers, query handlers, domain event handlers, and
integration event handlers for events other contexts publish. Reacting to a foreign event is a use
case like any other, so it lives here rather than being split out by what triggered it. This layer
also declares the ports it needs (`OrderRepository`, `StockReservations`, …).

**`infrastructure`** implements those ports.

**`acl`** is outbound only: it translates this context's needs into another context's published
language and dispatches. `InventoryStockReservations` implements `orders`' `StockReservations` port
by sending `inventory`'s `ReserveStock` — so nothing in `orders`' application layer names another
context at all.

### Dependency rules

- Any module may depend on a **foreign `contracts`**.
- No module may depend on a foreign anything else.

Both rules are load-bearing rather than stylistic: `contracts` depending on nothing is what stops a
foreign domain model leaking through a transitive dependency.

The same shape holds for the framework itself. A context takes `kbus-api` for its messages and
handlers, `kbus-domain` for its model, and `kbus-application` for what a handler is given; an
`infrastructure` module adds `kbus-infrastructure` for the ports it implements. None of them takes
`kbus-core`, so a handler cannot reach the bus — the isolation is a classpath fact rather than a
convention. `app` and `app-manual` are the only modules that depend on `kbus-core`, because
assembling the bus is the one job that needs it.

## Commands return the minimum

A command returns an identifier and nothing else — `PlaceOrder` returns an `OrderId`. Read models
belong to queries (`GetOrderById` → `OrderSummary`), which is what keeps the write and read sides
from drifting into one shape that serves neither.

## Wiring

`app` is where the contexts are assembled. It supplies the generated container's ports, builds the
typed `CompileTimeLoadedMessageBus`, and turns on the framework's opt-in machinery: a transactional
outbox, a per-context inbox for each context, auto-publishing of `OrderPlaced` as
`OrderPlacedIntegration`, and each context's event subscriptions.

The auto-publish mapping is the one piece `app` does not state: `OrderPlacedMapper` carries
`@LoadEventMapper`, so the generator collects it and the wiring passes the generated list. The
mapper still lives in the orders context, because only that context can decide which of its facts
another context is entitled to see.

An anti-corruption layer cannot simply take the bus: the concrete generated bus class is assembled
downstream of every context, and naming its untyped `execute` is a compile error. So
`InventoryStockReservations` takes a `CommandGateway<ReserveStock, ReserveStockResult>` — the one
command it is entitled to send — and `app` binds it to the generated `ReserveStockGateway`, which
exists only because something can handle `ReserveStock`.

## Two wirings, one set of contexts

`app-manual` assembles the same contexts with no generated code: it binds the ports itself, fills a
`PersistingHandlerLocator` with a factory per handler, and passes the contexts to `MessageBus`. The
outbox, the per-context inboxes and the subscriptions are the same core APIs `app` uses, and the
auto-publish mapping the generator collects for `app` is registered here by naming the mapper.

Every context module is shared between the two, unchanged — the wiring is the only thing that
differs, which is the point. Registering by hand costs a factory per handler and settles nothing at
compile time: a command with no registration is a runtime `MissingHandlerException`, where the
generated wiring cannot produce a bus that has forgotten one.

The commands a context can reach from inside its own handlers stay typed either way.
`CancelAndReplaceOrderHandler` asks for `OrdersCommands`, and `app-manual` implements that interface
over the nested executor rather than having it generated.

`app-contract` holds the requirements as an abstract test class, so both wirings run the same
assertions and each adds only what is true of itself alone.

## Per-module KSP configuration

Every layer module that declares handlers applies `kbus.handler-module` and names its
context:

```kotlin
plugins { id("kbus.handler-module") }

boundedContext { identity = "orders" }
```

One identity spans several Gradle modules; the plugin derives the submodule name from the module's
own name, so index class names stay unique across the build.

## Running

```bash
./gradlew :examples:app:jvmTest :examples:app-manual:jvmTest
```
