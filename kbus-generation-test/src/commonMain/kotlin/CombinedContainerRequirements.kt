package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.annotations.ContainerInterface
import com.jimbroze.kbus.annotations.HandlersInterface
import com.jimbroze.kbus.generated.DependenciesInterface
import com.jimbroze.kbus.generated.HandlerInterface

@ContainerInterface interface CombinedContainerRequirements : DependenciesInterface

@HandlersInterface interface CombinedHandlers : HandlerInterface
