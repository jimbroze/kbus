package com.jimbroze.kbus.core.infrastructure.stream

import com.jimbroze.kbus.core.infrastructure.serialize.SerializedMessage
import kotlinx.coroutines.flow.Flow

interface MessageTransport {
    fun send(messageType: String, message: ByteArray)

    fun receive(): Flow<SerializedMessage>
}
