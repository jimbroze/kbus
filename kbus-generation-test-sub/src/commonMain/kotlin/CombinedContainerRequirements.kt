package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.ContainerInterface
import com.jimbroze.kbus.annotations.HandlersInterface
import com.jimbroze.kbus.generated.kbus_generation_test_sub.DependenciesInterface
import com.jimbroze.kbus.generated.kbus_generation_test_sub.HandlerInterface

// FIXME need to pass name to loadHandler annotation?
@ContainerInterface interface CombinedContainerRequirements : DependenciesInterface

@HandlersInterface interface CombinedHandlers : HandlerInterface

class TestingExternal {
    init {
        val test = ExternalDependenciesCommandSub("TestingExternal")
    }
}
