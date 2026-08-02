package com.jimbroze.kbus.contracts.annotations

/**
 * Makes an event known to KBUS code generation.
 *
 * A companion object implementing `AutoPublishesFrom` additionally opts the event into generated
 * auto-publish registrations. Without such a companion, the event is simply known to the processor
 * and nothing is generated for it.
 */
@Target(AnnotationTarget.CLASS) annotation class LoadEvent
