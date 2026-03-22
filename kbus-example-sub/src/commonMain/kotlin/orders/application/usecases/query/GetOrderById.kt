package com.jimbroze.kbus.generation.test.orders.application.usecases.query

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.generation.test.orders.application.OrderRepository
import com.jimbroze.kbus.generation.test.orders.domain.Order

class GetOrderById(val orderId: String) : Query<BusResult<Order, MessageFailure>>()

@LoadMessageHandler
@Suppress("unused")
class GetOrderByIdHandler(private val orderRepository: OrderRepository) :
    QueryHandler<GetOrderById, BusResult<Order, MessageFailure>>() {
    override suspend fun handle(message: GetOrderById): BusResult<Order, MessageFailure> {
        val order = orderRepository.findById(message.orderId)
        return if (order != null) {
            BusResult.success(order)
        } else {
            BusResult.failure(
                object : MessageFailure {
                    override val reason = GenericFailure("Order not found: ${message.orderId}")
                }
            )
        }
    }
}
