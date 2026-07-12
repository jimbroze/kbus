package com.jimbroze.kbus.core.infrastructure.serialize

data class SerializedMessage(val messageType: String, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SerializedMessage

        if (messageType != other.messageType) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageType.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
