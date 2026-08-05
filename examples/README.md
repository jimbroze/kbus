# KBUS examples

A modular monolith built with KBUS: two bounded contexts, each split into one Gradle module per
layer, with every piece of bus wiring outside the contexts themselves.

```
examples/
  contexts/
    orders/          contracts · domain · application · infrastructure · acl
    inventory/       contracts · domain · application · infrastructure
  app/               the only module that knows a bus exists
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

## Commands return the minimum

A command returns an identifier and nothing else — `PlaceOrder` returns an `OrderId`. Read models
belong to queries (`GetOrderById` → `OrderSummary`), which is what keeps the write and read sides
from drifting into one shape that serves neither.

## Wiring

`app` is where the contexts are assembled. It supplies the generated container's ports, builds the
typed `CompileTimeLoadedMessageBus`, and turns on the framework's opt-in machinery: a transactional
outbox, a per-context inbox for each context, auto-publishing of `OrderPlaced` as
`OrderPlacedIntegration`, and each context's event subscriptions.

An anti-corruption layer cannot simply take the bus: typed dispatch lives only on the concrete
generated bus class, which is assembled downstream of every context, and the untyped `execute` on it
is deliberately unusable. So `InventoryStockReservations` takes the one call it makes as a function,
and `app` binds it to the typed bus.

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
./gradlew :examples:app:jvmTest
```
