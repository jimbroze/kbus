package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/** Collects what a generator writes, keyed by generated file name, without touching a disk. */
class GeneratedSources : CodeGenerator {
    private val streams = mutableMapOf<String, ByteArrayOutputStream>()

    val fileNames: Set<String>
        get() = streams.keys

    operator fun get(fileName: String): String =
        streams[fileName]?.toString(Charsets.UTF_8)
            ?: error("No generated file named '$fileName'. Generated: $fileNames")

    override fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String,
    ): OutputStream = ByteArrayOutputStream().also { streams[fileName] = it }

    override fun createNewFileByPath(
        dependencies: Dependencies,
        path: String,
        extensionName: String,
    ): OutputStream = createNewFile(dependencies, "", path.substringAfterLast('/'), extensionName)

    override fun associate(
        sources: List<KSFile>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) = Unit

    override fun associateByPath(sources: List<KSFile>, path: String, extensionName: String) = Unit

    override fun associateWithClasses(
        classes: List<KSClassDeclaration>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) = Unit

    override val generatedFile: Collection<File> = emptyList()
}

object SilentLogger : KSPLogger {
    override fun logging(message: String, symbol: KSNode?) = Unit

    override fun info(message: String, symbol: KSNode?) = Unit

    override fun warn(message: String, symbol: KSNode?) = Unit

    override fun error(message: String, symbol: KSNode?) = Unit

    override fun exception(e: Throwable) = throw e
}
