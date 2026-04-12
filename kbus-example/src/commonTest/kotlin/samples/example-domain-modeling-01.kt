// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleDomainModeling01

import com.jimbroze.kbus.domain.AggregateRoot
import com.jimbroze.kbus.domain.Entity
import com.jimbroze.kbus.domain.Identifier
import com.jimbroze.kbus.domain.ValueObject

// Value Object — equals() and hashCode() required
class Money(val amount: Double, val currency: String) : ValueObject<Money>() {
    override fun equals(other: Any?) =
        other is Money && amount == other.amount && currency == other.currency

    override fun hashCode() = 31 * amount.hashCode() + currency.hashCode()
}

// Entity
class OrderId(private val value: String) : Identifier {
    override fun equals(other: Any?) = other is OrderId && value == other.value
    override fun hashCode() = value.hashCode()
}

class Order(override val id: OrderId, val items: List<String>) : Entity<Order>()

// Aggregate Root
class CartId(private val value: String) : Identifier {
    override fun equals(other: Any?) = other is CartId && value == other.value
    override fun hashCode() = value.hashCode()
}

class ShoppingCart(override val id: CartId) : AggregateRoot<ShoppingCart>()
