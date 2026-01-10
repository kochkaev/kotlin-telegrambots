import com.squareup.kotlinpoet.*
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory

abstract class GenerateAbstractKTelegramClientTask : AbstractGeneratorTask() {

    @get:InputDirectory
    abstract val sourcesDir: DirectoryProperty

    @Internal override val targetPackageName = "io.github.kochkaev.kotlin.telegrambots.core"
    @Internal override val targetFileName = "AbstractKTelegramClient"

    @TaskAction
    fun execute() {
        logger.info("Starting generation for $targetFileName")

        val typeSolver = CombinedTypeSolver(
            ReflectionTypeSolver(),
            JavaParserTypeSolver(sourcesDir.get().asFile),
        )
        val symbolResolver = JavaSymbolSolver(typeSolver)
        val parserConfiguration = ParserConfiguration().setSymbolResolver(symbolResolver)
        val javaParser = JavaParser(parserConfiguration)

        val senderFile = sourcesDir.get().asFile.walk().first { it.isFile && it.name == "TelegramClient.java" }
        val cu = javaParser.parse(senderFile).result.get()
        val classDecl = cu.getInterfaceByName("TelegramClient").orElseThrow { IllegalStateException("TelegramClient interface not found") }

        val fileSpecBuilder = FileSpec.builder(targetPackageName, targetFileName)
            .indent("    ")
            .addAnnotation(AnnotationSpec.builder(Suppress::class).useSiteTarget(AnnotationSpec.UseSiteTarget.FILE).addMember("%S, %S, %S", "unused", "RedundantVisibilityModifier", "DEPRECATION").build())

        val classBuilder = TypeSpec.classBuilder(targetFileName)
            .addSuperinterface(ClassName(targetPackageName, "KTelegramClient"))
            .addModifiers(KModifier.ABSTRACT)

        val methods = classDecl.methods.filter {
            !it.type.asString().startsWith("CompletableFuture") && !it.isDefault
        }

        methods.forEach { method ->
            val returnType = method.type.toKotlinTypeName(method, symbolResolver)

            val funSpec = FunSpec.builder(method.nameAsString)
                .addModifiers(KModifier.OVERRIDE)
                .apply {
                    if (method.isAnnotationPresent("Deprecated")) {
                        addAnnotation(AnnotationSpec.builder(Deprecated::class).addMember("%S", "Super method is deprecated").build())
                    }
                }
                .returns(returnType)
                .apply {
                    method.typeParameters.forEach { typeParam ->
                        val bounds = typeParam.typeBound.map { it.toKotlinTypeName(method, symbolResolver) }
                        addTypeVariable(TypeVariableName(typeParam.nameAsString, bounds))
                    }
                    method.parameters.forEach { param ->
                        addParameter(param.nameAsString, param.type.toKotlinTypeName(method, symbolResolver))
                    }
                    val paramNames = method.parameters.joinToString(", ") { it.nameAsString }
                    addStatement("return %L(%L).join()", "${method.nameAsString}Async", paramNames)
                }
                .build()
            classBuilder.addFunction(funSpec)
        }

        fileSpecBuilder.addType(classBuilder.build())
        fileSpecBuilder.build().writeTo(outputDir.get().asFile)
        logger.info("Generated ${methods.size} suspend functions for $targetFileName")
    }
}
