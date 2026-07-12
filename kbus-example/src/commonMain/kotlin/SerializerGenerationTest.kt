package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlinx.serialization.Serializable

@Serializable data class CreateUserCommand(val email: String) : Command<BusResult<Unit, MessageFailure>>()

@LoadMessageHandler
class CreateUserCommandHandler :
    CommandHandler<CreateUserCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: CreateUserCommand): BusResult<Unit, MessageFailure> =
        BusResult.success(Unit)
}

@Serializable data class OrderShippedEvent(val orderId: String) : DomainEvent()

@LoadMessageHandler
class OrderShippedEventHandler : DomainEventHandler<OrderShippedEvent>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: OrderShippedEvent) {}
}
