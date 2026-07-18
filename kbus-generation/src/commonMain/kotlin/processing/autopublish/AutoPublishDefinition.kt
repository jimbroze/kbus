package com.jimbroze.kbus.generation.processing.autopublish

import com.squareup.kotlinpoet.ClassName

data class AutoPublishDefinition(
    val integrationEventClass: ClassName,
    val domainEventClass: ClassName,
)
