package com.jimbroze.kbus.core.infrastructure.stream

import com.jimbroze.kbus.contracts.bus.BusAccess
import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.infrastructure.serialize.MessageSerializer

class MessageStream(
    private val serializer: MessageSerializer,
    private val transport: MessageTransport,
    private val bus: BusAccess,
) {
    fun send(command: Command<*>) {
        this.send(command as Message)
    }

    fun send(event: IntegrationEvent) {
        this.send(event as Message)
    }

    suspend fun receiveMessages() {
        this.transport.receive().collect { serializedMessage ->
            val message =
                this.serializer.deserialize(
                    payload = serializedMessage.payload,
                    messageType = serializedMessage.messageType,
                )

            this.handle(message)
        }
    }

    private fun send(message: Message) {
        val payload = this.serializer.serialize(message)

        this.transport.send(
            message::class.qualifiedName ?: error("Message class must have a qualified name"),
            payload,
        )
    }

    private suspend fun handle(message: IntegrationEvent) {
        bus.dispatch(message)
    }

    private suspend fun handle(message: Command<*>) {
        bus.execute(message)
    }

    private suspend fun handle(message: Message) {
        // Do nothing
    }
}
