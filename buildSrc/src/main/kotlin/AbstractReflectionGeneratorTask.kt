import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Optional

abstract class AbstractReflectionGeneratorTask : AbstractGeneratorTask() {

    @get:InputDirectory
    abstract val sourcesDir: DirectoryProperty

    @get:Internal
    protected abstract val reflectionBaseClass: Class<*>

    protected abstract fun generateFunctionsForClass(
        fileSpecBuilder: FileSpec.Builder,
        clazz: Class<*>,
        classDecl: ClassOrInterfaceDeclaration
    ): Int

    protected fun getAllMethods(clazz: Class<*>): List<Method> {
        val methods = mutableListOf<Method>()
        var currentClass: Class<*>? = clazz
        while (currentClass != null && currentClass != Object::class.java) {
            methods.addAll(currentClass.declaredMethods)
            currentClass = currentClass.superclass
        }
        return methods
    }

    protected fun <T> T?.optional(): Optional<T & Any> = Optional.ofNullable(this)

    protected fun TypeName.toKotlinType(): TypeName {
        return when (this) {
            is ParameterizedTypeName -> {
                val rawType = this.rawType.toKotlinType() as ClassName
                val typeArguments = this.typeArguments.map { it.toKotlinType() }.toTypedArray()
                rawType.parameterizedBy(*typeArguments).copy(nullable = this.isNullable)
            }
            is ClassName -> {
                javaToKotlinMap[this.canonicalName]?.copy(nullable = this.isNullable) ?: this
            }
            else -> this
        }
    }

    @TaskAction
    fun execute() {
        logger.info("Starting generation for $targetFileName")

        val fileSpecBuilder = FileSpec.builder(targetPackageName, targetFileName)
            .indent("    ")
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                .addMember("%S, %S, %S", "unused", "RedundantVisibilityModifier", "DEPRECATION")
                .build())

        val typeSolver = CombinedTypeSolver(
            ReflectionTypeSolver(),
            JavaParserTypeSolver(sourcesDir.get().asFile)
        )
        val parserConfiguration = ParserConfiguration().setSymbolResolver(JavaSymbolSolver(typeSolver))
        val javaParser = JavaParser(parserConfiguration)

        val classLoader = reflectionBaseClass.classLoader
        val reflections = Reflections(ConfigurationBuilder()
            .forPackage(reflectionBaseClass.packageName, classLoader)
            .setScanners(Scanners.SubTypes)
            .addClassLoaders(classLoader)
        )
        val allSubClasses = reflections.getSubTypesOf(reflectionBaseClass)

        val sourceFilesMap = sourcesDir.get().asFile.walk()
            .filter { it.isFile && it.extension == "java" }
            .associateBy { it.nameWithoutExtension }

        var functionsGenerated = 0

        allSubClasses.forEach { clazz ->
            if (clazz.isAnonymousClass || Modifier.isAbstract(clazz.modifiers) || !Modifier.isPublic(clazz.modifiers)) return@forEach
            try { clazz.getConstructor() } catch (_: NoSuchMethodException) { return@forEach }

            val sourceFile = sourceFilesMap[clazz.simpleName] ?: return@forEach
            val cu = javaParser.parse(sourceFile).result.get()
            val classDecl = cu.findFirst(ClassOrInterfaceDeclaration::class.java) { it.nameAsString == clazz.simpleName }.orElse(null) ?: return@forEach

            functionsGenerated += generateFunctionsForClass(fileSpecBuilder, clazz, classDecl)
        }

        if (functionsGenerated > 0) {
            fileSpecBuilder.build().writeTo(outputDir.get().asFile)
            logger.info("Generated $functionsGenerated functions for $targetFileName")
        } else {
            logger.info("No functions were generated for $targetFileName.")
        }
    }
}
