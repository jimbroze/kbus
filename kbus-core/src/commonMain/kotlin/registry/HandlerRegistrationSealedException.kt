package com.jimbroze.kbus.core.registry

/**
 * Thrown when handlers are registered after the bus that owns them was constructed.
 *
 * A bus snapshots each context's handlers when it is built, so a later registration would be
 * invisible to routing and to owner lookup. Failing loudly here is the whole point: the alternative
 * is a handler that is silently never called.
 */
class HandlerRegistrationSealedException(message: String) : Exception(message)
