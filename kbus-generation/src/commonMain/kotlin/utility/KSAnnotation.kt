package com.jimbroze.kbus.generation.utility

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import kotlin.reflect.KProperty1

internal inline fun <reified T> KSAnnotation.findArgument(property: KProperty1<*, *>): T {
    if (T::class.java.isEnum) {
        return findEnumArgument(property)
    }

    val value = getArgumentValue(property)
    return value as? T
        ?: error(
            "Argument '${property.name}' expected '${T::class.simpleName}' " +
                "but KSP returned '${value::class.simpleName}'"
        )
}

private inline fun <reified T> KSAnnotation.findEnumArgument(property: KProperty1<*, *>): T {
    val enumName =
        when (val enumValue = getArgumentValue(property)) {
            is KSClassDeclaration -> enumValue.simpleName.asString() // KSP 2
            is KSType -> enumValue.declaration.simpleName.asString() // KSP 1
            else ->
                error(
                    "Argument '${property.name}' expected an Enum, but KSP returned " +
                        "'${enumValue::class.simpleName}'"
                )
        }

    val enumInstance =
        T::class.java.enumConstants.firstOrNull { (it as Enum<*>).name == enumName }
            ?: error("Enum constant '$enumName' not found in '${T::class.simpleName}'")

    @Suppress("UNCHECKED_CAST")
    return enumInstance as T
}

private fun KSAnnotation.getArgumentValue(property: KProperty1<*, *>): Any {
    val argument =
        arguments.find { it.name?.asString() == property.name }
            ?: error("Argument '${property.name}' is missing")

    return argument.value ?: error("Argument '${property.name}' has a null value")
}
