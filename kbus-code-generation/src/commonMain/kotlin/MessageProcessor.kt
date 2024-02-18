import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStream
import java.io.OutputStreamWriter

private const val ANNOTATION_NAME = "Load"

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

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

        val visitor = ClassVisitor()
        symbols.forEach { it.accept(visitor, Unit) }

        val unableToProcess = symbols.filterNot { it.validate() }.toList()
        return unableToProcess
    }

    inner class ClassVisitor() : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error("Only classes can be annotated with @$ANNOTATION_NAME", classDeclaration)
                return
            }

            val handlerClass: KSClassDeclaration = classDeclaration

//            TODO improve finding handle function. Need to get override and check that command handler?
            val handleFunctions = handlerClass.getDeclaredFunctions()
                .filter {
                    it.simpleName.asString() == "handle"
                    && it.parameters.count() == 1
                }

            var commandClass: KSClassDeclaration? = null
            for (ksFunctionDeclaration in handleFunctions) {
                val thisCommandType = ksFunctionDeclaration.parameters.first()
                    .type.resolve().declaration
                if (thisCommandType is KSClassDeclaration && thisCommandType.superTypes.any {
                    it.resolve().declaration.qualifiedName!!.asString() == Command::class.qualifiedName
                }) {
                    commandClass = thisCommandType
                    continue
                }
            }

            if (commandClass == null) {
                logger.error("Message handler must have a valid 'handle' function.", classDeclaration)
                return
            }

            val packageName = commandClass.containingFile!!.packageName.asString()
            val commandClassName = commandClass.simpleName.asString()
            val handlerClassName = handlerClass.simpleName.asString()
            val loadedCommandClassName = "${commandClassName}Loaded"


            val loadedCommandConstructorParameters = StringBuilder()
            val commandConstructorParameters = StringBuilder()
            var firstParam = true
//            TODO handler null constructor?
            for (parameter in commandClass.primaryConstructor?.parameters!!) {
                val name = parameter.name!!.asString()
                val typeName = StringBuilder(parameter.type.resolve().declaration.qualifiedName?.asString() ?: "<ERROR>")
                val typeArgs = parameter.type.element!!.typeArguments
                if (parameter.type.element!!.typeArguments.isNotEmpty()) {
                    typeName.append("<")
                    typeName.append(
                        typeArgs.joinToString(", ") {
                            val type = it.type?.resolve()
                            "${it.variance.label} ${type?.declaration?.qualifiedName?.asString() ?: "ERROR"}" +
                                    if (type?.nullability == Nullability.NULLABLE) "?" else ""
                        }
                    )
                    typeName.append(">")
                }

                loadedCommandConstructorParameters.append("${if (firstParam) "" else ", "}$name: $typeName")
                commandConstructorParameters.append("${if (firstParam) "" else ", "}$name")

                firstParam = false
            }


            val file = codeGenerator.createNewFile(
                Dependencies(true, commandClass.containingFile!!),
                packageName,
                loadedCommandClassName
            )
//            file.appendText("package $packageName\n\n")
//            file.appendText("import HELLO\n\n")
            file.appendText("class $loadedCommandClassName($loadedCommandConstructorParameters) {\n")
            file.appendText("    val command = ${commandClassName}(${commandConstructorParameters})\n")
            file.appendText("\n")
            file.appendText("    suspend fun handle(handler: $handlerClassName) = handler.handle(command)\n")
            file.appendText("}\n")
            file.close()

//            class CommandNameLoaded(paramOne: ParamOneType) {
//                val command = CommandName(paramOne)
//
//                suspend fun handle(handler: CommandNameHandler) = handler.handle(command)
//            }
        }
    }
}
