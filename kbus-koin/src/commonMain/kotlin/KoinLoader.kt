package com.jimbroze.kbus.koin

import com.jimbroze.kbus.core.RuntimeDependencyLoader
import kotlin.reflect.KClass
import org.koin.core.Koin

class KoinLoader(private val container: Koin = Koin()) : RuntimeDependencyLoader() {
    override fun <TClass : Any> instantiate(cls: KClass<TClass>): TClass {
        return container.get(cls)
    }
}
