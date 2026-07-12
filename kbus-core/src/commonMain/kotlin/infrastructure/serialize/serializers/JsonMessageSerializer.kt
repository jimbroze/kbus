package com.jimbroze.kbus.core.infrastructure.serialize.serializers

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.infrastructure.serialize.MessageSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

class JsonMessageSerializer(
    format: Json = Json { ignoreUnknownKeys = true },
    private val module: SerializersModule,
) : MessageSerializer {

    private val json = Json(format) { serializersModule = module }

    override fun serialize(message: Message): ByteArray {
        return json.encodeToString(KbusBaseMessageSerializer, message).encodeToByteArray()
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(payload: ByteArray, messageType: String): Message {
        val stringPayload = payload.decodeToString()
        val serializer =
            json.serializersModule.getPolymorphic(Message::class, messageType)
                ?: throw SerializationException("Unknown message type: '$messageType'")
        return json.decodeFromString(serializer, stringPayload)
    }
}

val KbusBaseMessageSerializer = PolymorphicSerializer(Message::class)
