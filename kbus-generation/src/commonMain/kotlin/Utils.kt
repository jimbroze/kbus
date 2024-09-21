package com.jimbroze.kbus.generation

import com.google.devtools.ksp.symbol.*

data class ParameterDefinition(val name: String, val typeName: String)

// TODO refactor this?
fun getParamNames(parameter: KSValueParameter): ParameterDefinition {
    val name = parameter.name!!.asString()
    val typeName =
        StringBuilder(parameter.type.resolve().declaration.qualifiedName?.asString() ?: "<ERROR>")
    val typeArgs = parameter.type.element!!.typeArguments

    if (parameter.type.element!!.typeArguments.isNotEmpty()) {
        typeName.append("<")
        typeName.append(
            typeArgs.joinToString(", ") {
                val type = it.type?.resolve()
                "${it.variance.label} ${type?.declaration?.qualifiedName?.asString() ?: "ERROR"}" +
                    if (type?.nullability == Nullability.NULLABLE) "?" else ""
            }
        )
        typeName.append(">")
    }

    return ParameterDefinition(name, typeName.toString())
}

fun findBaseClass(classDeclaration: KSClassDeclaration): KSClassDeclaration? {
    return classDeclaration.superTypes
        .mapNotNull { superType -> superType.resolve().declaration as? KSClassDeclaration }
        .find { it.classKind == ClassKind.CLASS }
}
