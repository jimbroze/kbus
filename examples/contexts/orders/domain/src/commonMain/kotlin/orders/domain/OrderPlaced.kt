package com.jimbroze.kbus.example.orders.domain

import com.jimbroze.kbus.domain.event.DomainEvent

class OrderPlaced(val orderId: String, val customerId: String) : DomainEvent()
