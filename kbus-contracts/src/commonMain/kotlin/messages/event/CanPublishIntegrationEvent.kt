package com.jimbroze.kbus.contracts.messages.event

abstract class CanPublishIntegrationEvent {
    private lateinit var publisher: IntegrationEventPublisher

    fun setPublisher(publisher: IntegrationEventPublisher) {
        this.publisher = publisher
    }

    suspend fun publish(event: IntegrationEvent) = publisher.publish(listOf(event))
}
