package com.jimbroze.kbus.generation

class RootPackageName {
    lateinit var rootName: String

    fun addNameOption(packageName: String) {
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
