package com.jimbroze.kbus.example.app.manual

import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.example.app.ExampleAppContract
import kotlinx.coroutines.CoroutineScope

class ManualExampleAppTest : ExampleAppContract() {
    override fun createBus(appScope: CoroutineScope) =
        manualExampleBus(EmptyTransactionManager(), appScope)
}
