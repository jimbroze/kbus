package com.jimbroze.kbus.core.registry

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message =
        "This token must only be created by the KBus code generator. Do not instantiate it manually.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.CLASS)
annotation class GeneratedKBusApi
