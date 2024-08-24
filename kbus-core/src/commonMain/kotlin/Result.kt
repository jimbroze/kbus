package com.jimbroze.kbus.core

class ResultFailureException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
    constructor(cause: Throwable) : this(null, cause)
}
