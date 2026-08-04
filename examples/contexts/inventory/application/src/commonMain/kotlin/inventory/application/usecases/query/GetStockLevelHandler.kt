package com.jimbroze.kbus.example.inventory.application.usecases.query

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.inventory.application.InventoryRepository
import com.jimbroze.kbus.example.inventory.contracts.GetStockLevel
import com.jimbroze.kbus.example.inventory.contracts.StockLevel

@LoadMessageHandler
@Suppress("unused")
class GetStockLevelHandler(private val inventoryRepository: InventoryRepository) :
    QueryHandler<GetStockLevel, BusResult<StockLevel, MessageFailure>>() {
    override suspend fun handle(message: GetStockLevel): BusResult<StockLevel, MessageFailure> {
        val stockItem =
            inventoryRepository.findByProductId(message.productId)
                ?: return BusResult.failure(
                    object : MessageFailure {
                        override val reason =
                            GenericFailure("Product not found: ${message.productId}")
                    }
                )

        return BusResult.success(StockLevel(stockItem.productId, stockItem.quantity))
    }
}
