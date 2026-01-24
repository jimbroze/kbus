package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.generation.processing.dependencies.Dependency

data class HandlerData(
    val handlerClass: KSClassDeclaration,
    val messageClass: KSClassDeclaration,
    val returnType: KSTypeReference,
    val topLevelDependencies: List<Dependency>,
) {
    val nameAsDependency: String
        get() = handlerClass.simpleName.asString().replaceFirstChar { it.lowercase() }
}
