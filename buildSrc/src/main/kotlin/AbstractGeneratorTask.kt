import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Optional

abstract class AbstractGeneratorTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    protected abstract val targetPackageName: String
    protected abstract val targetFileName: String
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

    protected fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return listOf(emptyList())
        val head = lists.first()
        val tail = lists.drop(1)
        val tailProduct = cartesianProduct(tail)
        return head.flatMap { item ->
            tailProduct.map { product -> listOf(item) + product }
        }
    }

    private val javaToKotlinMap = mapOf(
        ClassName("java.lang", "Object") to ANY,
        ClassName("java.lang", "String") to STRING,
        ClassName("java.lang", "Integer") to INT,
        ClassName("java.lang", "Long") to LONG,
        ClassName("java.lang", "Short") to SHORT,
        ClassName("java.lang", "Byte") to BYTE,
        ClassName("java.lang", "Float") to FLOAT,
        ClassName("java.lang", "Double") to DOUBLE,
        ClassName("java.lang", "Character") to CHAR,
        ClassName("java.lang", "Boolean") to BOOLEAN,
        ClassName("java.util", "List") to LIST,
        ClassName("java.util", "Map") to MAP,
        ClassName("java.util", "Set") to SET,
        ClassName("java.util", "Collection") to COLLECTION
    )

    protected fun <T> T?.optional(): Optional<T & Any> = Optional.ofNullable(this)

    protected fun TypeName.toKotlinType(): TypeName {
        return when (this) {
            is ParameterizedTypeName -> {
                val rawType = this.rawType.toKotlinType() as ClassName
                val typeArguments = this.typeArguments.map { it.toKotlinType() }.toTypedArray()
                rawType.parameterizedBy(*typeArguments).copy(nullable = this.isNullable)
            }
            is ClassName -> javaToKotlinMap[this]?.copy(nullable = this.isNullable) ?: this
            else -> this
        }
    }

    @TaskAction
    fun execute() {
        logger.warn("--- Starting Generation for $targetFileName ---")

        val fileSpecBuilder = FileSpec.builder(targetPackageName, targetFileName)
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                .addMember("%S, %S, %S", "unused", "RedundantVisibilityModifier", "DEPRECATION")
                .build())

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
            val cu = StaticJavaParser.parse(sourceFile)
            val classDecl = cu.findFirst(ClassOrInterfaceDeclaration::class.java) { it.nameAsString == clazz.simpleName }.orElse(null) ?: return@forEach

            functionsGenerated += generateFunctionsForClass(fileSpecBuilder, clazz, classDecl)
        }

        logger.warn("--- Generation Summary for $targetFileName ---")
        logger.warn("Total functions generated: $functionsGenerated")

        if (functionsGenerated > 0) {
            fileSpecBuilder.build().writeTo(outputDir.get().asFile)
            logger.warn("--- Generation Finished Successfully for $targetFileName ---")
        } else {
            logger.warn("--- No functions were generated for $targetFileName. ---")
        }
    }
}
