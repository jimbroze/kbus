package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.core.Middleware
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.core.TransactionManager
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlin.text.StringBuilder

class MessageBusGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val busClassName: String,
) {

    fun generate(packagePath: String, loaderName: String, handlers: Set<LoadedHandlerDefinition>) {
        logger.info("Generating CompileTimeLoadedMessageBus")

        val fileText = StringBuilder()

        fileText.appendLine("package $packagePath")
        fileText.appendLine()
        fileText.append(generateBusClassCode(loaderName, handlers))

        val file = codeGenerator.createNewFile(Dependencies(true), packagePath, busClassName)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    private fun generateBusClassCode(
        loaderName: String,
        handlers: Set<LoadedHandlerDefinition>,
    ): StringBuilder {
        // TODO use MessageBus constructor for type safety? Replace pre-written class instead?
        val superClassName = MessageBus::class.qualifiedName!!
        val middlewareClassName = Middleware::class.qualifiedName!!
        val transactionManagerClassName = TransactionManager::class.qualifiedName!!

        //        private constructor(
        //                middleware: List<Middleware>,
        //        transactionManager: TransactionManager,
        //        private val locator: GeneratedHandlerLocator,
        //        ) : MessageBus(locator, transactionManager, middleware) {
        //
        //            constructor(
        //                middleware: List<Middleware>,
        //                transactionManager: TransactionManager,
        //                loader: IContainer,
        //            ) : this(middleware, transactionManager, GeneratedHandlerLocator(loader))

        val busClassCode = StringBuilder()
        busClassCode.appendLine("class $busClassName")
        busClassCode.appendLine("private constructor(")
        busClassCode.appendLine("private val locator: GeneratedHandlerLocator,".prependIndent())
        busClassCode.appendLine("transactionManager: $transactionManagerClassName,".prependIndent())
        busClassCode.appendLine("middleware: List<$middlewareClassName>,".prependIndent())
        busClassCode.appendLine(") : $superClassName(locator, transactionManager, middleware) {")

        busClassCode.appendLine("constructor(".prependIndent())
        busClassCode.appendLine("loader: $loaderName,".prependIndent().prependIndent())
        busClassCode.appendLine(
            "transactionManager: $transactionManagerClassName,".prependIndent().prependIndent()
        )
        busClassCode.appendLine(
            "middleware: List<$middlewareClassName>,".prependIndent().prependIndent()
        )
        busClassCode.appendLine(
            ") : this(GeneratedHandlerLocator(loader), transactionManager, middleware)"
                .prependIndent()
        )
        busClassCode.appendLine()

        for (handler in handlers) {
            val messageBaseClass = handler.handlerDefinition.messageBaseClass
            if (messageBaseClass === Command::class) {
                val handlerFactoryMethodName =
                    handler.handlerDefinition.handler.simpleName.asString().replaceFirstChar {
                        it.lowercase()
                    }

                val returnTypeName =
                    handler.handlerDefinition.message.superTypes
                        .firstOrNull {
                            (it.resolve().declaration as? KSClassDeclaration)!!.classKind ==
                                ClassKind.CLASS
                        }!!
                        .element!!
                        .typeArguments
                        .first()
                        .type!!
                        .resolve()
                        .toTypeName()

                busClassCode.append(
                    FunSpec.builder("execute")
                        .addModifiers(KModifier.SUSPEND)
                        .addParameter("command", handler.handlerDefinition.message.toClassName())
                        .returns(returnTypeName)
                        .addCode(
                            """
                            |val handlerCreator = { commandDependencies: %T ->
                            |    locator.generatedHandlerFactory.%N(commandDependencies)
                            |}
                            |
                            |return commandExecutor.execute(command, handlerCreator)
                            |"""
                                .trimMargin(),
                            CommandDependencies::class,
                            handlerFactoryMethodName,
                        )
                        .build()
                        .toString()
                )
            } else if (messageBaseClass === Query::class) {
                val handlerFactoryMethodName =
                    handler.handlerDefinition.handler.simpleName.asString().replaceFirstChar {
                        it.lowercase()
                    }

                val returnTypeName =
                    handler.handlerDefinition.message.superTypes
                        .firstOrNull {
                            (it.resolve().declaration as? KSClassDeclaration)!!.classKind ==
                                ClassKind.CLASS
                        }!!
                        .element!!
                        .typeArguments
                        .first()
                        .type!!
                        .resolve()
                        .toTypeName()

                busClassCode.append(
                    FunSpec.builder("fetch")
                        .addModifiers(KModifier.SUSPEND)
                        .addParameter("query", handler.handlerDefinition.message.toClassName())
                        .returns(returnTypeName)
                        .addCode(
                            """
                            |val handler = locator.generatedHandlerFactory.%N()
                            |
                            |return queryFetcher.fetch(query, handler)
                            |"""
                                .trimMargin(),
                            handlerFactoryMethodName,
                        )
                        .build()
                        .toString()
                )
            }
        }

        busClassCode.appendLine("}")

        return busClassCode
    }
}
