package com.jimbroze.kbus.core.messages.event.routing

class AggregateException(val exceptions: List<Exception>) :
    Exception(
        "${exceptions.size} failure(s): ${exceptions.joinToString { it.message ?: it.toString() }}"
    )
