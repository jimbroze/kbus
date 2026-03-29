package com.jimbroze.kbus.domain.event

sealed interface DispatchTiming {
    data object Immediately : DispatchTiming

    data object AfterPrimaryWork : DispatchTiming

    data object AfterTransaction : DispatchTiming
}
