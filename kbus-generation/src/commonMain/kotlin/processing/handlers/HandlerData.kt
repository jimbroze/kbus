package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.generation.processing.dependencies.Dependency

data class HandlerData(
    val nameAsDependency: String,
    val handlerClass: KSClassDeclaration,
    val messageClass: KSClassDeclaration,
    val returnType: KSTypeReference,
    val topLevelDependencies: List<Dependency>,
)
