import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStream
import java.io.OutputStreamWriter

private const val ANNOTATION_NAME = "Load"
class MessageProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
): SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
                .getSymbolsWithAnnotation(ANNOTATION_NAME)
            .filterIsInstance<KSClassDeclaration>()

        if (!symbols.iterator().hasNext()) return emptyList()

        codeGenerator.createNewFile(Dependencies.ALL_FILES, "", "Foo", "kt").use { output ->
            OutputStreamWriter(output).use { writer ->
                writer.write("package com.jimbroze\n\n")
                writer.write("class Foo {\n")

                val visitor = ClassVisitor()
                symbols.forEach { it.accept(visitor, writer) }

                writer.write("}\n")
            }
        }

        val unableToProcess = symbols.filterNot { it.validate() }.toList()
        return unableToProcess
    }

    inner class ClassVisitor(private val file: OutputStream) : KSVisitorVoid() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error("Only classes can be annotated with @$ANNOTATION_NAME", classDeclaration)
                return
            }
            // Get "name" parameter from annotation
//            val annotation: KSAnnotation = classDeclaration.annotations.first {
//                it.shortName.asString() == ANNOTATION_NAME
//            }
//
//            val nameArgument: KSValueArgument = annotation.arguments
//                .first { arg -> arg.name?.asString() == "name" }
//
//            val functionName = nameArgument.value as String

            // Getting the list of member properties of the annotated interface.
            val properties: Sequence<KSPropertyDeclaration> = classDeclaration.getAllProperties()

//            TODO improve finding handle function. Need to get override and check that command handler?
            val handleFunctions = classDeclaration.getDeclaredFunctions()
                .filter {
                    it.simpleName.asString() == "handle"
                    && it.parameters.count() == 1
                }

            var commandType: KSClassDeclaration? = null
            handleFunctions.forEach { ksFunctionDeclaration ->
                val thisCommandType = ksFunctionDeclaration.parameters.first()
                    .type.resolve().declaration
                if (thisCommandType is KSClassDeclaration && !thisCommandType.superTypes.any {
                    it.resolve().declaration.qualifiedName!!.asString() == Command::class.qualifiedName
                }) {
                    commandType = thisCommandType
                    break
                }
            }

            if (commandType == null) {
                logger.error("Message handler must have a valid 'handle' function.", classDeclaration)
                return
            }



//                .filter { it.isConstructor()}
        }

        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
        }

        override fun visitTypeArgument(typeArgument: KSTypeArgument, data: Unit) {
        }
    }
}
