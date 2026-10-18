package com.jimbroze.kbus.generation.utility

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver

internal fun KSClassDeclaration.extendsType(qualifiedName: String): Boolean {
    if (this.qualifiedName?.asString() == qualifiedName) return true
    return superTypes.any { superType ->
        val superDecl = superType.resolve().declaration as? KSClassDeclaration
        superDecl != null && superDecl.extendsType(qualifiedName)
    }
}

/**
 * The type arguments this class gives the supertype named [qualifiedName], however many classes lie
 * between them, in the order that supertype declares its type parameters. A type parameter of this
 * class is left as a type variable. Null when this class does not extend that supertype.
 */
internal fun KSClassDeclaration.typeArgumentsSuppliedTo(qualifiedName: String): List<TypeName>? =
    typeArgumentsSuppliedTo(qualifiedName, typeArgumentsByParameterName = emptyMap())

private fun KSClassDeclaration.typeArgumentsSuppliedTo(
    qualifiedName: String,
    typeArgumentsByParameterName: Map<String, TypeName>,
): List<TypeName>? {
    val typeParameterResolver = typeParameters.toTypeParameterResolver()
    return superTypes.firstNotNullOfOrNull { superTypeReference ->
        val superType = superTypeReference.resolve()
        val superClass =
            superType.declaration as? KSClassDeclaration ?: return@firstNotNullOfOrNull null
        val superTypeArguments =
            superType.arguments.map {
                it.toTypeName(typeParameterResolver).substituting(typeArgumentsByParameterName)
            }

        if (superClass.qualifiedName?.asString() == qualifiedName) superTypeArguments
        else
            superClass.typeArgumentsSuppliedTo(
                qualifiedName,
                superClass.typeParameters.map { it.name.asString() }.zip(superTypeArguments).toMap(),
            )
    }
}

private fun TypeName.substituting(typeArgumentsByParameterName: Map<String, TypeName>): TypeName =
    when (this) {
        is TypeVariableName ->
            typeArgumentsByParameterName[name]?.let {
                it.copy(nullable = it.isNullable || isNullable)
            } ?: this
        is ParameterizedTypeName ->
            rawType
                .parameterizedBy(
                    typeArguments.map { it.substituting(typeArgumentsByParameterName) }
                )
                .copy(nullable = isNullable)
        is WildcardTypeName ->
            when {
                inTypes.isNotEmpty() ->
                    WildcardTypeName.consumerOf(
                        inTypes.single().substituting(typeArgumentsByParameterName)
                    )
                else ->
                    WildcardTypeName.producerOf(
                        outTypes.single().substituting(typeArgumentsByParameterName)
                    )
            }
        else -> this
    }
