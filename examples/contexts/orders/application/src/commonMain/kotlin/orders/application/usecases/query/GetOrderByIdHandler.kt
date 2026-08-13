package com.jimbroze.kbus.example.orders.application.usecases.query

import com.jimbroze.kbus.api.annotations.LoadMessageHandler
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.example.orders.application.OrderRepository
import com.jimbroze.kbus.example.orders.contracts.GetOrderById
import com.jimbroze.kbus.example.orders.contracts.GetOrderByIdResult
import com.jimbroze.kbus.example.orders.contracts.OrderNotFound
import com.jimbroze.kbus.example.orders.contracts.OrderSummary

@LoadMessageHandler
@Suppress("unused")
class GetOrderByIdHandler(private val orderRepository: OrderRepository) :
    QueryHandler<GetOrderById, GetOrderByIdResult>() {
    override suspend fun handle(message: GetOrderById): GetOrderByIdResult {
        val order =
            orderRepository.findById(message.orderId)
                ?: return BusResult.failure(OrderNotFound(message.orderId))

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
