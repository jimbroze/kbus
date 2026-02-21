package com.jimbroze.kbus.generation.processing.handlers

import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

data class HandlerData(
    val handlerClass: ClassName,
    val messageClass: ClassName,
    val returnType: TypeName,
    val topLevelDependencies: List<Dependency>,
) {
    val nameAsDependency: String
        get() = handlerClass.simpleName.replaceFirstChar { it.lowercase() }
}
