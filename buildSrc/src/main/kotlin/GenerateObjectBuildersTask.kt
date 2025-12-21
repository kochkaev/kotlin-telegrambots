import com.github.javaparser.StaticJavaParser
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import org.telegram.telegrambots.meta.api.interfaces.BotApiObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

abstract class GenerateObjectBuildersTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private fun getAllMethods(clazz: Class<*>): List<Method> {
        val methods = mutableListOf<Method>()
        var currentClass: Class<*>? = clazz
        while (currentClass != null && currentClass != Object::class.java) {
            methods.addAll(currentClass.declaredMethods)
            currentClass = currentClass.superclass
        }
        return methods
    }

    private fun getAllFields(clazz: Class<*>): Map<String, Field> {
        val fields = mutableMapOf<String, Field>()
        var currentClass: Class<*>? = clazz
        while (currentClass != null && currentClass != Object::class.java) {
            currentClass.declaredFields.forEach {
                if (!fields.containsKey(it.name)) {
                    fields[it.name] = it
                }
            }
            currentClass = currentClass.superclass
        }
        return fields
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

    private fun TypeName.toKotlinType(): TypeName {
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
        logger.warn("--- Starting Object Builder Function Generation (Setter-based Strategy) ---")

        val fileSpecBuilder = FileSpec.builder("io.github.kochkaev.kotlin.telegrambots", "ObjectBuilders")
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                .addMember("%S, %S", "unused", "RedundantVisibilityModifier")
                .build())

        val classLoader = BotApiObject::class.java.classLoader
        val reflections = Reflections(ConfigurationBuilder()
            .forPackage("org.telegram.telegrambots.meta.api.objects", classLoader)
            .setScanners(Scanners.SubTypes)
            .addClassLoaders(classLoader)
        )
        val allObjectClasses = reflections.getSubTypesOf(BotApiObject::class.java)

        val sourceFilesMap = sourcesDir.get().asFile.walk()
            .filter { it.isFile && it.extension == "java" }
            .associateBy { it.nameWithoutExtension }

        var functionsGenerated = 0

        allObjectClasses.forEach { clazz ->
            if (clazz.isEnum || clazz.isInterface || clazz.isAnonymousClass || Modifier.isAbstract(clazz.modifiers) || !Modifier.isPublic(clazz.modifiers)) return@forEach
            try { clazz.getConstructor() } catch (_: NoSuchMethodException) { return@forEach }

            val sourceFile = sourceFilesMap[clazz.simpleName] ?: return@forEach
            val cu = StaticJavaParser.parse(sourceFile)
            val classDecl = cu.findFirst(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration::class.java) { it.nameAsString == clazz.simpleName }.orElse(null) ?: return@forEach
            
            val fieldsWithNonNull = classDecl.findAll(com.github.javaparser.ast.body.FieldDeclaration::class.java)
                .filter { field -> field.annotations.any { it.name.identifier == "NonNull" } }
                .map { it.variables.first().nameAsString }
                .toSet()

            val functionName = clazz.simpleName.replaceFirstChar { it.lowercase() }
            
            val fieldsByName = getAllFields(clazz)
            val setters = getAllMethods(clazz)
                .filter { it.name.startsWith("set") && it.parameterCount == 1 && Modifier.isPublic(it.modifiers) }
                .associateBy { it.name.substring(3).replaceFirstChar { c -> c.lowercase() } }

            if (setters.isEmpty()) return@forEach

            val (requiredSetters, optionalSetters) = setters.entries.partition { (propertyName, _) ->
                propertyName in fieldsWithNonNull
            }

            val requiredParams = requiredSetters.sortedBy { it.key }.map { (propertyName, setter) ->
                ParameterSpec.builder(propertyName, setter.genericParameterTypes.first().asTypeName().toKotlinType()).build()
            }
            val optionalParams = optionalSetters.sortedBy { it.key }.map { (propertyName, setter) ->
                ParameterSpec.builder(propertyName, setter.genericParameterTypes.first().asTypeName().toKotlinType().copy(nullable = true))
                    .defaultValue("null")
                    .build()
            }

            val funSpecBuilder = FunSpec.builder(functionName)
                .returns(clazz.asTypeName().toKotlinType())
                .addParameters(requiredParams + optionalParams)
                .addStatement("val obj = %T()", clazz.asTypeName())

            val classDeprecation = clazz.getAnnotation(Deprecated::class.java)
            if (classDeprecation != null) {
                funSpecBuilder.addAnnotation(AnnotationSpec.builder(Deprecated::class)
                    .addMember("message = %S", classDeprecation.message)
                    .build())
            }

            (requiredParams + optionalParams).forEach { param ->
                val setter = setters[param.name]!!
                val field = fieldsByName[param.name]
                val usePropertyAccess = field != null && setter.parameterTypes[0] == field.type

                if (param.type.isNullable) {
                    val block = CodeBlock.builder().beginControlFlow("if (${param.name} != null)")
                    if (usePropertyAccess) {
                        block.addStatement("obj.${param.name} = ${param.name}")
                    } else {
                        block.addStatement("obj.${setter.name}(${param.name})")
                    }
                    block.endControlFlow()
                    funSpecBuilder.addCode(block.build())
                } else {
                    if (usePropertyAccess) {
                        funSpecBuilder.addStatement("obj.${param.name} = ${param.name}")
                    } else {
                        funSpecBuilder.addStatement("obj.${setter.name}(${param.name})")
                    }
                }
            }
            
            funSpecBuilder.addStatement("return obj")
            fileSpecBuilder.addFunction(funSpecBuilder.build())
            functionsGenerated++
        }

        logger.warn("--- Object Builder Generation Summary ---")
        logger.warn("Total functions generated: $functionsGenerated")

        if (functionsGenerated > 0) {
            fileSpecBuilder.build().writeTo(outputDir.get().asFile)
            logger.warn("--- Object Builder Generation Finished Successfully ---")
        } else {
            logger.warn("--- No object builders were generated. ---")
        }
    }
}
