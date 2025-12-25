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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.telegram.telegrambots.meta.bots.AbsSender

abstract class GenerateHandlersDslTask : AbstractGeneratorTask() {

    @get:InputDirectory
    abstract val sourcesDir: DirectoryProperty
    @get:OutputDirectory
    abstract override val outputDir: DirectoryProperty

    @Internal override val targetPackageName = "io.github.kochkaev.kotlin.telegrambots.dsl"
    @Internal override val targetFileName = "GeneratedHandlers"

    @TaskAction
    fun execute() {
        logger.info("Starting generation for $targetFileName")

        val typeSolver = CombinedTypeSolver(
            ReflectionTypeSolver(),
            JavaParserTypeSolver(sourcesDir.get().asFile)
        )
        val parserConfiguration = ParserConfiguration().setSymbolResolver(JavaSymbolSolver(typeSolver))
        val javaParser = JavaParser(parserConfiguration)

        val updateFile = sourcesDir.get().asFile.walk().first { it.isFile && it.name == "Update.java" }
        val cu = javaParser.parse(updateFile).result.get()
        val classDecl = cu.getClassByName("Update").orElseThrow { IllegalStateException("Update class not found") }

        val fileSpecBuilder = FileSpec.builder(targetPackageName, targetFileName)
            .indent("    ")
            .addAnnotation(AnnotationSpec
                .builder(Suppress::class)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                .addMember("%S, %S", "unused", "RedundantVisibilityModifier")
                .build())
            .addImport("kotlinx.coroutines.flow", "filter")
            .addImport("kotlinx.coroutines", "launch")

        val methodNames = classDecl.methods.map { it.nameAsString }.toSet()
        var functionsGenerated = 0

        classDecl.fields.forEach { field ->
            val fieldName = field.variables.first().nameAsString
            val capitalizedFieldName = fieldName.replaceFirstChar { it.titlecase() }
            val hasMethodName = "has$capitalizedFieldName"

            if (methodNames.contains(hasMethodName)) {
                val handlerName = "on$capitalizedFieldName"
                val fieldType = field.elementType.toKotlinTypeName()

                val funSpec = FunSpec.builder(handlerName)
                    .receiver(ClassName(targetPackageName, "HandlersDsl"))
                    .addParameter("handler", LambdaTypeName.get(receiver = AbsSender::class.asTypeName(), parameters = listOf(ParameterSpec.unnamed(fieldType)), returnType = UNIT).copy(suspending = true))
                    .addStatement("scope.launch { updates.filter { it.%L() }.collect { with(bot) { handler(it.%L) } } }", hasMethodName, fieldName)
                    .build()
                fileSpecBuilder.addFunction(funSpec)
                functionsGenerated++
            }
        }

        if (functionsGenerated > 0) {
            fileSpecBuilder.build().writeTo(outputDir.get().asFile)
            logger.info("Generated $functionsGenerated handler functions for $targetFileName")
        } else {
            logger.info("No handler functions were generated for $targetFileName")
        }
    }
}
