package com.jimbroze.kbus.example.orders.application.usecases.query

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.orders.application.OrderRepository
import com.jimbroze.kbus.example.orders.contracts.GetOrderById
import com.jimbroze.kbus.example.orders.contracts.OrderSummary

@LoadMessageHandler
@Suppress("unused")
class GetOrderByIdHandler(private val orderRepository: OrderRepository) :
    QueryHandler<GetOrderById, BusResult<OrderSummary, MessageFailure>>() {
    override suspend fun handle(message: GetOrderById): BusResult<OrderSummary, MessageFailure> {
        val order =
            orderRepository.findById(message.orderId)
                ?: return BusResult.failure(
                    object : MessageFailure {
                        override val reason = GenericFailure("Order not found: ${message.orderId}")
                    }
                )

        return BusResult.success(
            OrderSummary(
                orderId = order.id,
                customerId = order.customerId,
                total = order.total,
                status = order.status.name,
            )
        )
    }
}
