import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.squareup.kotlinpoet.*
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions

abstract class GenerateDefaultKAbsSenderTask : AbstractGeneratorTask() {

    @get:InputDirectory
    abstract val botSourcesDir: DirectoryProperty
    @get:InputDirectory
    abstract val metaSourcesDir: DirectoryProperty

    @Internal override val targetPackageName = "io.github.kochkaev.kotlin.telegrambots.core"
    @Internal override val targetFileName = "DefaultKAbsSender"

    @TaskAction
    fun execute() {
        logger.info("Starting generation for $targetFileName")

        val typeSolver = CombinedTypeSolver(
            ReflectionTypeSolver(),
            JavaParserTypeSolver(botSourcesDir.get().asFile),
            JavaParserTypeSolver(metaSourcesDir.get().asFile)
        )
        val parserConfiguration = ParserConfiguration().setSymbolResolver(JavaSymbolSolver(typeSolver))
        val javaParser = JavaParser(parserConfiguration)

        val senderFile = botSourcesDir.get().asFile.walk().first { it.isFile && it.name == "DefaultAbsSender.java" }
        val cu = javaParser.parse(senderFile).result.get()
        val classDecl = cu.getClassByName("DefaultAbsSender").orElseThrow { IllegalStateException("DefaultAbsSender class not found") }

        val fileSpecBuilder = FileSpec.builder(targetPackageName, targetFileName)
            .indent("    ")
            .addAnnotation(AnnotationSpec.builder(Suppress::class).useSiteTarget(AnnotationSpec.UseSiteTarget.FILE).addMember("%S, %S, %S", "unused", "RedundantVisibilityModifier", "DEPRECATION").build())
            .addImport("kotlinx.coroutines.future", "await")

        val classBuilder = TypeSpec.classBuilder(targetFileName)
            .superclass(DefaultAbsSender::class)
            .addModifiers(KModifier.OPEN)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("options", DefaultBotOptions::class)
                    .addParameter("token", String::class)
                    .build()
            )
            .addSuperclassConstructorParameter("options")
            .addSuperclassConstructorParameter("token")

        val asyncMethods = classDecl.methods.filter {
            (it.isProtected || it.isPublic) && it.type.asString().startsWith("CompletableFuture")
        }

        asyncMethods.forEach { method ->
            val newName = method.nameAsString.replace("Async", "K")
            val returnType = (method.type.toKotlinTypeName(method) as ParameterizedTypeName).typeArguments.first()

            val funSpec = FunSpec.builder(newName)
                .apply {
                    if (method.isProtected) addModifiers(KModifier.PROTECTED)
                    if (method.isAnnotationPresent("Deprecated")) {
                        addAnnotation(AnnotationSpec.builder(Deprecated::class).addMember("%S", "Super method is deprecated").build())
                    }
                }
                .addModifiers(KModifier.SUSPEND)
                .returns(returnType)
                .apply {
                    method.typeParameters.forEach { typeParam ->
                        val bounds = typeParam.typeBound.map { it.toKotlinTypeName(method) }
                        addTypeVariable(TypeVariableName(typeParam.nameAsString, bounds))
                    }
                    method.parameters.forEach { param ->
                        addParameter(param.nameAsString, param.type.toKotlinTypeName(method))
                    }
                    val paramNames = method.parameters.joinToString(", ") { it.nameAsString }
                    addStatement("return %L(%L).await()", method.nameAsString, paramNames)
                }
                .build()
            classBuilder.addFunction(funSpec)
        }

        fileSpecBuilder.addType(classBuilder.build())
        fileSpecBuilder.build().writeTo(outputDir.get().asFile)
        logger.info("Generated ${asyncMethods.size} suspend functions for $targetFileName")
    }
}
