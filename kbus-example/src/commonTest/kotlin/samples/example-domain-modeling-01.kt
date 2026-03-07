// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleDomainModeling01

// In the submodule's build.gradle.kts
ksp {
    arg("kbus.subModuleName", project.name)
    arg("kbus.indexPackage", "com.example.myApp.indexes")
}

// In the top-level module's build.gradle.kts
ksp {
    arg("kbus.indexPackage", "com.example.myApp.indexes")
}

// Value Object — equals() and hashCode() required
class Money(val amount: BigDecimal, val currency: String) : ValueObject<Money>()

// Entity
class Order(override val id: OrderId, val items: List<Item>) : Entity<Order>()

// Aggregate Root
class ShoppingCart(override val id: CartId) : AggregateRoot<ShoppingCart>()
