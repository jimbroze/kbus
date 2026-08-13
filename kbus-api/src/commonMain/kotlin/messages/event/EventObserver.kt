package com.jimbroze.kbus.api.messages.event

import kotlinx.coroutines.flow.Flow

interface EventObserver<TUiEvent : IntegrationEvent> {
    val events: Flow<TUiEvent>
}
