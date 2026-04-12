package com.jimbroze.kbus.core.registry

import kotlin.reflect.KClass

class DuplicateEventHandlerException(message: String = "Duplicate event handler registered") :
    Exception(message) {
    constructor(
        handlerName: String?,
        eventName: String?,
    ) : this("Duplicate event handler $handlerName for event $eventName")

    constructor(
        handlerType: KClass<*>,
        eventType: KClass<*>,
    ) : this(handlerType.simpleName, eventType.simpleName)
}
