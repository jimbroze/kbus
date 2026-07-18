package com.jimbroze.kbus.generation.utility

import com.google.devtools.ksp.symbol.KSClassDeclaration

internal fun KSClassDeclaration.extendsType(qualifiedName: String): Boolean {
    if (this.qualifiedName?.asString() == qualifiedName) return true
    return superTypes.any { superType ->
        val superDecl = superType.resolve().declaration as? KSClassDeclaration
        superDecl != null && superDecl.extendsType(qualifiedName)
    }
}
