package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.infrastructure.serialize.serializers.JsonMessageSerializer
import com.jimbroze.kbus.generated.KbusSerializerMap
import com.jimbroze.kbus.generated.KbusSerializersModule
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val CREATE_USER_FQN = "com.jimbroze.kbus.generation.test.CreateUserCommand"
private const val ORDER_SHIPPED_FQN = "com.jimbroze.kbus.generation.test.OrderShippedEvent"

class SerializerGenerationTest {

    // --- KbusSerializersModule ---

    @Test
    fun test_serializers_module_includes_all_serializable_message_types() {
        val json = Json { serializersModule = KbusSerializersModule }
        val poly = PolymorphicSerializer(Message::class)

        json.encodeToString(poly, CreateUserCommand("test@example.com"))
        json.encodeToString(poly, OrderShippedEvent("order-123"))
    }

    @Test
    fun test_serializers_module_round_trips_command_via_json() {
        val json = Json { serializersModule = KbusSerializersModule }
        val poly = PolymorphicSerializer(Message::class)
        val original = CreateUserCommand("round@trip.com")

        val decoded = json.decodeFromString(poly, json.encodeToString(poly, original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_serializers_module_round_trips_event_via_json() {
        val json = Json { serializersModule = KbusSerializersModule }
        val poly = PolymorphicSerializer(Message::class)
        val original = OrderShippedEvent("order-456")

        val decoded = json.decodeFromString(poly, json.encodeToString(poly, original))

        assertEquals(original, decoded)
    }

    @Test
    fun test_serializers_module_excludes_non_serializable_message_types() {
        val json = Json { serializersModule = KbusSerializersModule }
        val poly = PolymorphicSerializer(Message::class)

        assertFailsWith<SerializationException> {
            json.encodeToString(poly, NestedClassesCommand(""))
        }
    }

    // --- KbusSerializerMap ---

    @Test
    fun test_serializer_map_contains_serializable_message_type_fqns() {
        assertTrue(KbusSerializerMap.containsKey(CREATE_USER_FQN))
        assertTrue(KbusSerializerMap.containsKey(ORDER_SHIPPED_FQN))
    }

    @Test
    fun test_serializer_map_excludes_non_serializable_message_types() {
        assertFalse(
            KbusSerializerMap.containsKey("com.jimbroze.kbus.generation.test.NestedClassesCommand")
        )
    }

    @Test
    fun test_serializer_map_values_can_deserialize_without_polymorphic_wrapper() {
        @Suppress("UNCHECKED_CAST")
        val kSerializer = KbusSerializerMap[CREATE_USER_FQN] as KSerializer<Message>
        assertNotNull(kSerializer)

        val json = Json {}
        val original = CreateUserCommand("map@test.com")
        val decoded = json.decodeFromString(kSerializer, json.encodeToString(kSerializer, original))

        assertEquals(original, decoded)
    }

    // --- JsonMessageSerializer integration ---

    @Test
    fun test_json_message_serializer_round_trips_with_generated_module() {
        val serializer = JsonMessageSerializer(module = KbusSerializersModule)
        val original = CreateUserCommand("json@serializer.com")

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes, CREATE_USER_FQN)

        assertEquals(original, deserialized)
    }
}
