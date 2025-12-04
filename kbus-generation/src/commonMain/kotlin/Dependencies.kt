package com.jimbroze.kbus.generation

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.QueryHandler
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class NestedDependency(
    override val declaration: KSDeclaration,
    override val typeArgs: List<KSTypeArgument>,
    override val name: String,
    override val nullability: Nullability,
    val isRoot: Boolean,
    val isCommandDependency: Boolean,
    val childNames: List<String>,
) : Dependency(declaration, typeArgs, name, nullability) {
    companion object {
        fun fromDependency(
            dependency: Dependency,
            isRoot: Boolean,
            isCommandDependency: Boolean,
            childNames: List<String>,
        ): NestedDependency {
            return NestedDependency(
                dependency.declaration,
                dependency.typeArgs,
                dependency.name,
                dependency.nullability,
                isRoot,
                isCommandDependency,
                childNames,
            )
        }
    }

    fun isDuplicateOf(other: NestedDependency): Boolean {
        return this !== other && super.equals(other)
    }
}

open class Dependency(
    open val declaration: KSDeclaration,
    open val typeArgs: List<KSTypeArgument>,
    open val name: String,
    open val nullability: Nullability = Nullability.NOT_NULL,
) {
    companion object {
        fun withCustomName(
            declaration: KSDeclaration,
            typeArgs: List<KSTypeArgument>,
            customName: String? = null,
            nullability: Nullability = Nullability.NOT_NULL,
        ): Dependency {
            val name =
                customName ?: declaration.simpleName.asString().replaceFirstChar { it.lowercase() }

            return Dependency(declaration, typeArgs, name = name, nullability = nullability)
        }

        fun fromParameter(parameter: KSValueParameter, useParamName: Boolean): Dependency {
            val type = parameter.type.resolve()
            val typeArgs = parameter.type.element?.typeArguments.orEmpty()

            val customName = if (useParamName) parameter.name?.asString() else null

            return withCustomName(
                type.declaration,
                typeArgs,
                customName = customName,
                nullability = type.nullability,
            )
        }
    }

    fun getTypeWithArgs(): String {
        val typeName = StringBuilder(declaration.qualifiedName!!.asString())

        for (typeArg in typeArgs) {
            val type = typeArg.type?.resolve()
            val typeText = type?.declaration?.qualifiedName?.asString() ?: continue
            val variance = typeArg.variance.label
            val nullability = if (type.nullability == Nullability.NULLABLE) "?" else ""

            typeName.append("<$variance $typeText $nullability>")
        }
        if (nullability == Nullability.NULLABLE) typeName.append("?")

        return typeName.toString()
    }

    fun isCommandHandler(): Boolean {
        val declaration = declaration
        if (declaration !is KSClassDeclaration) return false
        return declaration.superTypes.any {
            it.resolve().declaration.qualifiedName?.asString() ==
                CommandHandler::class.qualifiedName
        }
    }

    fun isQueryHandler(): Boolean {
        val declaration = declaration
        if (declaration !is KSClassDeclaration) return false
        return declaration.superTypes.any {
            it.resolve().declaration.qualifiedName?.asString() == QueryHandler::class.qualifiedName
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Dependency

        if (declaration != other.declaration) return false
        if (typeArgs != other.typeArgs) return false
        if (name != other.name) return false
        if (nullability != other.nullability) return false

        return true
    }

    override fun hashCode(): Int {
        var result = declaration.hashCode()
        result = 31 * result + typeArgs.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + nullability.hashCode()
        return result
    }
}
