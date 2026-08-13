package com.jimbroze.kbus.example.inventory.application.usecases.query

import com.jimbroze.kbus.api.annotations.LoadMessageHandler
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.example.inventory.application.InventoryRepository
import com.jimbroze.kbus.example.inventory.contracts.GetStockLevel
import com.jimbroze.kbus.example.inventory.contracts.GetStockLevelResult
import com.jimbroze.kbus.example.inventory.contracts.ProductNotStocked
import com.jimbroze.kbus.example.inventory.contracts.StockLevel

@LoadMessageHandler
@Suppress("unused")
class GetStockLevelHandler(private val inventoryRepository: InventoryRepository) :
    QueryHandler<GetStockLevel, GetStockLevelResult>() {
    override suspend fun handle(message: GetStockLevel): GetStockLevelResult {
        val stockItem =
            inventoryRepository.findByProductId(message.productId)
                ?: return BusResult.failure(ProductNotStocked(message.productId))

        return BusResult.success(StockLevel(stockItem.productId, stockItem.quantity))
    }
}
