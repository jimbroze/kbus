package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.squareup.kotlinpoet.ClassName

data class HandlerData(
    val handlerClass: ClassName,
    val messageClass: ClassName,
    val returnType: KSTypeReference,
    val topLevelDependencies: List<Dependency>,
) {
    val nameAsDependency: String
        get() = handlerClass.simpleName.replaceFirstChar { it.lowercase() }
}
