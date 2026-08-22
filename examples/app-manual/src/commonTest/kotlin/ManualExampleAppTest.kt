package com.jimbroze.kbus.example.app.manual

import com.jimbroze.kbus.example.adapters.ExampleDatabase
import com.jimbroze.kbus.example.adapters.ExampleDatabaseTransactionManager
import com.jimbroze.kbus.example.app.ExampleAppContract
import kotlinx.coroutines.CoroutineScope

class ManualExampleAppTest : ExampleAppContract() {
    override fun createBus(appScope: CoroutineScope) =
        manualExampleBus(ExampleDatabaseTransactionManager(ExampleDatabase()), appScope)
}
