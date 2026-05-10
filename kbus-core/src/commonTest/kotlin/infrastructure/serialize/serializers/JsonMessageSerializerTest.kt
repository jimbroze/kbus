package com.jimbroze.kbus.core.infrastructure.serialize.serializers

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.infrastructure.serialize.MessageSerializerTestBase
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
data class SimpleTestMessage(val placeholder: String = "") : Message {
    override val messageType: String = "SimpleTestMessage"
    override fun toString() = "SimpleTestMessage"
}

@Serializable
data class FieldedTestMessage(
    val name: String,
    val count: Int,
    val active: Boolean,
) : Message {
    override val messageType: String = "FieldedTestMessage"
    override fun toString() = "FieldedTestMessage(name=$name, count=$count, active=$active)"
}

class JsonMessageSerializerTest : MessageSerializerTestBase() {
    private val module = SerializersModule {
        polymorphic(Message::class) {
            subclass(SimpleTestMessage::class)
            subclass(FieldedTestMessage::class)
        }
    }

    override fun createSerializer() = JsonMessageSerializer(module = module)

    override fun createSimpleMessage(): Message = SimpleTestMessage()

    override val simpleMessageType: String =
        "com.jimbroze.kbus.core.infrastructure.serialize.serializers.SimpleTestMessage"

    override fun createMessageWithFields(index: Int): Message =
        FieldedTestMessage(
            name = "name-$index",
            count = index,
            active = index % 2 == 0,
        )

    override val messageWithFieldsType: String =
        "com.jimbroze.kbus.core.infrastructure.serialize.serializers.FieldedTestMessage"
}
