package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.ObservableEventPublisher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface EventObserver<TUiEvent : IntegrationEvent> {
    val events: Flow<TUiEvent>
}

// TODO Autoloader generates observer & publisher from mapper
// TODO rename to InternalEventStream?

class ObservableEventMapper<TEvent : IntegrationEvent> :
    ObservableEventPublisher<TEvent>, EventObserver<TEvent> {

    // TODO add to constructor
    private val _events =
        MutableSharedFlow<TEvent>(
            onBufferOverflow = BufferOverflow.SUSPEND,
            extraBufferCapacity = 32,
        )

    override val events: Flow<TEvent> = _events.asSharedFlow()

    override suspend fun emit(event: TEvent) {
        _events.emit(event)
    }
}
