package com.jimbroze.kbus.core.messages.event

class MultipleException(val exceptions: List<Exception>) :
    Exception(
        "${exceptions.size} handler(s) failed: ${exceptions.joinToString { it.message ?: it.toString() }}"
    )
