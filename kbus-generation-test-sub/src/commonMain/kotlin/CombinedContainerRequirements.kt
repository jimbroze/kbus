package com.jimbroze.kbus.generation.test

// FIXME need to pass name to loadHandler annotation?
// @ContainerInterface interface CombinedContainerRequirements : DependenciesInterface

// @HandlersInterface interface CombinedHandlers : HandlerInterface

class TestingExternal {
    init {
        val test = ExternalDependenciesCommandSub("TestingExternal")
    }
}
