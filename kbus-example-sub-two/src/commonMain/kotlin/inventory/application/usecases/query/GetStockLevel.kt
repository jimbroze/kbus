package com.jimbroze.kbus.generation.test.inventory.application.usecases.query

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.generation.test.inventory.application.InventoryRepository

class GetStockLevel(val productId: String) : Query<BusResult<Int, MessageFailure>>()

@LoadMessageHandler
@Suppress("unused")
class GetStockLevelHandler(private val inventoryRepository: InventoryRepository) :
    QueryHandler<GetStockLevel, BusResult<Int, MessageFailure>>() {
    override suspend fun handle(message: GetStockLevel): BusResult<Int, MessageFailure> {
        val stockItem = inventoryRepository.findByProductId(message.productId)
        return if (stockItem != null) {
            BusResult.success(stockItem.quantity)
        } else {
            BusResult.failure(
                object : MessageFailure {
                    override val reason = GenericFailure("Product not found: ${message.productId}")
                }
            )
        }
    }
}
