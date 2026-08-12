package com.jimbroze.kbus.contracts.annotations

/**
 * Collects an integration event mapper into the generated auto-publish registrations, so the
 * mapping does not have to be registered by hand.
 *
 * The annotated declaration must be an object implementing `IntegrationEventMapper`. The domain
 * event it maps from is read from that supertype.
 */
@Target(AnnotationTarget.CLASS) annotation class LoadEventMapper
