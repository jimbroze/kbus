package com.jimbroze.kbus.generation.processing.dependencies

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.Dynamic
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName

object NameGenerator {
    fun getNameForType(type: TypeName, isNested: Boolean = false): String {
        val nameWithTypeArgs = getTypeArgs(type)

        val typeArgumentsString = processTypeArgs(nameWithTypeArgs)

        val formattedName = formatName(nameWithTypeArgs.simpleName, isNested)

        return formattedName + typeArgumentsString
    }

    private fun processTypeArgs(nameWithTypeArgs: NameWithTypeArgs): String =
        nameWithTypeArgs.typeArguments
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "And", prefix = "Of") { arg ->
                getNameForType(arg, isNested = true)
            } ?: ""

    private fun getTypeArgs(typeName: TypeName): NameWithTypeArgs =
        when (typeName) {
            is ClassName -> NameWithTypeArgs(typeName.simpleName, emptyList())
            is ParameterizedTypeName ->
                NameWithTypeArgs(typeName.rawType.simpleName, typeName.typeArguments)
            is TypeVariableName -> NameWithTypeArgs(typeName.name, typeName.bounds)
            is LambdaTypeName -> NameWithTypeArgs("Function", emptyList())
            is WildcardTypeName -> getTypeArgs(unwrapWildcards(typeName))
            Dynamic -> NameWithTypeArgs("Any", emptyList())
        }

    private fun unwrapWildcards(type: TypeName): TypeName =
        if (type is WildcardTypeName) {
            type.outTypes.firstOrNull() ?: type.inTypes.firstOrNull() ?: type
        } else {
            type
        }

    private fun formatName(simpleName: String, isNested: Boolean): String =
        simpleName.replaceFirstChar { if (isNested) it.uppercase() else it.lowercase() }

    private data class NameWithTypeArgs(val simpleName: String, val typeArguments: List<TypeName>)
}
