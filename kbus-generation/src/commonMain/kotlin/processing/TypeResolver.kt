package com.jimbroze.kbus.generation.processing

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance

// TODO change dependencies to store KotlinPoet TypeName to simplify parsing
object TypeResolver {
    fun resolve(typeName: String, resolver: Resolver): KSType {
        // 1. Handle Nullability (Suffix '?')
        val isNullable = typeName.endsWith("?")
        val cleanName = if (isNullable) typeName.dropLast(1) else typeName

        // 2. Check for Generics (Contains '<')
        val angleIndex = cleanName.indexOf('<')

        // CASE A: Simple Class (No Generics)
        if (angleIndex == -1) {
            val declaration =
                resolver.getClassDeclarationByName(cleanName)
                    ?: error("Could not find class: $cleanName")

            val type = declaration.asStarProjectedType()
            return if (isNullable) type.makeNullable() else type
        }

        // CASE B: Generic Class
        // Split "List<String>" into "List" and "String"
        val baseClassName = cleanName.substring(0, angleIndex)
        val argsContent = cleanName.substring(angleIndex + 1, cleanName.lastIndexOf('>'))

        val baseDeclaration =
            resolver.getClassDeclarationByName(baseClassName)
                ?: error("Could not find generic base class: $baseClassName")

        // 3. Parse the Arguments (Recursively)
        // We need to split "String, List<Int>" carefully (respecting nested brackets)
        val argTypeRefs =
            splitTypeArgs(argsContent).map { argString ->
                if (argString == "*") {
                    // Handle Star Projection <*>
                    resolver.getTypeArgument(
                        resolver.createKSTypeReferenceFromKSType(resolver.builtIns.anyType),
                        Variance.STAR,
                    )
                } else {
                    // Recursive Call
                    val argType = resolve(argString, resolver)
                    val argRef = resolver.createKSTypeReferenceFromKSType(argType)
                    resolver.getTypeArgument(argRef, Variance.INVARIANT)
                }
            }

        // 4. Reconstruct the KSType
        val type = baseDeclaration.asType(argTypeRefs)
        return if (isNullable) type.makeNullable() else type
    }

    // Helper to split "A, B<C, D>, E" by top-level commas only
    private fun splitTypeArgs(args: String): List<String> {
        val result = mutableListOf<String>()
        var bracketCount = 0
        var currentStart = 0

        for (i in args.indices) {
            val char = args[i]
            if (char == '<') bracketCount++
            if (char == '>') bracketCount--

            if (char == ',' && bracketCount == 0) {
                result.add(args.substring(currentStart, i).trim())
                currentStart = i + 1
            }
        }
        // Add the last segment
        result.add(args.substring(currentStart).trim())
        return result
    }
}
