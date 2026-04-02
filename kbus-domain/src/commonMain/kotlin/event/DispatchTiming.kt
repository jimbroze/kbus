package com.jimbroze.kbus.domain.event

sealed interface DispatchTiming {
    data object ImmediatelyInTransaction : DispatchTiming

    data object AtEndOfTransaction : DispatchTiming

    data object AfterTransaction : DispatchTiming
}
