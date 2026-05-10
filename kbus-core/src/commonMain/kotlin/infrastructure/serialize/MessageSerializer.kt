package com.jimbroze.kbus.core.infrastructure.serialize

import com.jimbroze.kbus.contracts.common.Message

interface MessageSerializer {
    /** Convert a Message into a storable/transmittable byte array. */
    fun serialize(message: Message): ByteArray

    /**
     * Reconstruct a Message from bytes using its fully qualified type name.
     *
     * @param payload The serialized bytes.
     * @param messageType The fully qualified name (e.g., "com.myapp.events.OrderPlaced")
     */
    fun deserialize(payload: ByteArray, messageType: String): Message
}
