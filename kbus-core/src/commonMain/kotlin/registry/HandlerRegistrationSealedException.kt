package com.jimbroze.kbus.core.registry

/**
 * Thrown when handlers are registered after the bus that owns them was constructed.
 *
 * A command or query has exactly one owning context, and the bus is what resolves that owner.
 * Closing registration at construction is what lets ownership be settled — and conflicts reported —
 * while there is still a stack trace pointing at the wiring, rather than at the first dispatch in
 * production.
 */
class HandlerRegistrationSealedException(message: String) : Exception(message)
