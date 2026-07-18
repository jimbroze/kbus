package com.jimbroze.kbus.contracts.annotations

/**
 * Makes an [com.jimbroze.kbus.contracts.messages.event.Event] known to KBUS code generation.
 *
 * A companion object implementing
 * [AutoPublishesFrom][com.jimbroze.kbus.core.messages.event.AutoPublishesFrom] additionally opts
 * the event into generated auto-publish registrations. Without such a companion, the event is
 * simply known to the processor with no generated output.
 */
@Target(AnnotationTarget.CLASS) annotation class LoadEvent
