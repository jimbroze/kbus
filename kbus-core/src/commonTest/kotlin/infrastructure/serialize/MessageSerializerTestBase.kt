package com.jimbroze.kbus.core.infrastructure.serialize

import com.jimbroze.kbus.contracts.common.Message
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Abstract test base for [MessageSerializer] implementations. Subclass this and implement all
 * abstract members to fully test a serializer.
 */
abstract class MessageSerializerTestBase {
    protected lateinit var serializer: MessageSerializer

    /** Create a fresh serializer instance for each test. */
    abstract fun createSerializer(): MessageSerializer

    /**
     * Return a simple [Message] with no fields beyond the [Message] contract. Two calls must return
     * equal instances.
     */
    abstract fun createSimpleMessage(): Message

    /**
     * The fully qualified class name of the message returned by [createSimpleMessage], used as the
     * `messageType` argument to [MessageSerializer.deserialize].
     */
    abstract val simpleMessageType: String

    /**
     * Return a [Message] that carries named fields of varying types. Instances for different
     * [index] values must not be equal to each other; two calls with the same [index] must return
     * equal instances.
     */
    abstract fun createMessageWithFields(index: Int): Message

    /**
     * The fully qualified class name of the message returned by [createMessageWithFields], used as
     * the `messageType` argument to [MessageSerializer.deserialize].
     */
    abstract val messageWithFieldsType: String

    @BeforeTest
    fun setUp() {
        serializer = createSerializer()
    }

    @Test
    fun serialize_returns_non_empty_byte_array() {
        val bytes = serializer.serialize(createSimpleMessage())

        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun deserialize_after_serialize_returns_equal_message() {
        val message = createSimpleMessage()

        val bytes = serializer.serialize(message)
        val restored = serializer.deserialize(bytes, simpleMessageType)

        assertEquals(message, restored)
    }

    @Test
    fun deserialize_returns_instance_of_the_original_class() {
        val message = createSimpleMessage()

        val bytes = serializer.serialize(message)
        val restored = serializer.deserialize(bytes, simpleMessageType)

        assertEquals(message::class, restored::class)
    }

    @Test
    fun roundtrip_preserves_all_fields() {
        val message = createMessageWithFields(0)

        val bytes = serializer.serialize(message)
        val restored = serializer.deserialize(bytes, messageWithFieldsType)

        assertEquals(message, restored)
    }

    @Test
    fun serialize_is_deterministic_for_the_same_message() {
        val message = createSimpleMessage()

        val first = serializer.serialize(message)
        val second = serializer.serialize(message)

        assertContentEquals(first, second)
    }

    @Test
    fun messages_with_different_content_produce_different_bytes() {
        val bytes0 = serializer.serialize(createMessageWithFields(0))
        val bytes1 = serializer.serialize(createMessageWithFields(1))

        assertFalse(bytes0.contentEquals(bytes1))
    }

    @Test
    fun deserialize_with_unknown_message_type_throws() {
        val bytes = serializer.serialize(createSimpleMessage())

        assertFailsWith<Exception> {
            serializer.deserialize(bytes, "com.example.NonExistentMessage")
        }
    }
}
