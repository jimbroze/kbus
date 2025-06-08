package com.jimbroze.kbus.generation

import com.google.devtools.ksp.symbol.KSName

class RootPackageName {
    lateinit var rootName: String

    fun addName(classQualifiedName: KSName) {
        val packageName = classQualifiedName.getQualifier()
        rootName =
            if (!this::rootName.isInitialized) {
                packageName
            } else {
                rootName.commonPrefixWith(packageName).trimEnd('.')
            }
    }

    override fun toString(): String {
        if (!this::rootName.isInitialized) {
            error("No package names have been added")
        }

        return rootName
    }
}
