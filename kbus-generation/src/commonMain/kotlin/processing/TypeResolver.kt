package com.jimbroze.kbus.generation.processing

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

object TypeResolver {
    fun resolveClassName(signature: String): ClassName {
        val typeName = resolve(signature)

        require(typeName is ClassName) {
            "Expected a class name, but got '$signature' (resolved to KotlinPoet type: ${typeName::class.simpleName})"
        }

        return typeName
    }

    fun resolve(signature: String): TypeName {
        val type = signature.replace("`", "").trim()

        return if (type.endsWith("?")) {
            resolveNullable(type)
        } else if (type.contains('<')) {
            resolveGeneric(type)
        } else {
            ClassName.bestGuess(type)
        }
    }

    private fun resolveNullable(type: String): TypeName =
        resolve(type.dropLast(1)).copy(nullable = true)

    private fun resolveGeneric(type: String): ParameterizedTypeName {
        val angleIndex = type.indexOf('<')

        val baseName = type.substring(0, angleIndex)
        val argsString = type.substring(angleIndex + 1, type.lastIndexOf('>'))

        val argsList = splitTypeArgs(argsString)
        val typeArgs = argsList.map { resolve(it) }

        return ClassName.bestGuess(baseName).parameterizedBy(typeArgs)
    }

    private fun splitTypeArgs(args: String): List<String> {
        val result = mutableListOf<String>()
        var openBrackets = 0
        var currentStart = 0

        for (i in args.indices) {
            val char = args[i]
            if (char == '<') openBrackets++
            if (char == '>') openBrackets--

            if (char == ',' && openBrackets == 0) {
                result.add(args.substring(currentStart, i))
                currentStart = i + 1
            }
        }
        result.add(args.substring(currentStart))
        return result
    }
}
