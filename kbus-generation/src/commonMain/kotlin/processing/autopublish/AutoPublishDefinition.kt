package com.jimbroze.kbus.generation.processing.autopublish

import com.squareup.kotlinpoet.ClassName

data class AutoPublishDefinition(val mapperClass: ClassName, val domainEventClass: ClassName)
