import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
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
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.bots.AbsSender
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

abstract class GenerateExtensionsTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputDirectory
    abstract val sourcesDir: DirectoryProperty

    private fun findBotApiMethodReturnType(clazz: Class<*>): java.lang.reflect.Type? {
        val typesToCheck = mutableListOf<java.lang.reflect.Type>()
        clazz.genericSuperclass?.let { typesToCheck.add(it) }
        typesToCheck.addAll(clazz.genericInterfaces)

        for (type in typesToCheck) {
            if (type is ParameterizedType && type.rawType == BotApiMethod::class.java) {
                return type.actualTypeArguments.firstOrNull()
            }
        }
        return clazz.superclass?.let { findBotApiMethodReturnType(it) }
    }

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

    private fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
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
        logger.warn("--- Starting Telegram Bot Extension Generation (Final Strategy) ---")

        val fileSpecBuilder = FileSpec.builder("io.github.kochkaev.kotlin.telegrambots", "TelegramBotExtensions")
            .addImport("kotlinx.coroutines.future", "await")
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                .addMember("%S, %S", "unused", "RedundantVisibilityModifier")
                .build())

        val classLoader = BotApiMethod::class.java.classLoader
        val reflections = Reflections(ConfigurationBuilder()
            .forPackage("org.telegram.telegrambots.meta.api.methods", classLoader)
            .setScanners(Scanners.SubTypes)
            .addClassLoaders(classLoader)
        )
        val allApiMethodClasses = reflections.getSubTypesOf(BotApiMethod::class.java)

        val sourceFilesMap = sourcesDir.get().asFile.walk()
            .filter { it.isFile && it.extension == "java" }
            .associateBy { it.nameWithoutExtension }

        var functionsGenerated = 0

        allApiMethodClasses.forEach { clazz ->
            if (clazz.isAnonymousClass || Modifier.isAbstract(clazz.modifiers) || !Modifier.isPublic(clazz.modifiers)) return@forEach
            try { clazz.getConstructor() } catch (_: NoSuchMethodException) { return@forEach }

            val sourceFile = sourceFilesMap[clazz.simpleName] ?: return@forEach
            val cu = StaticJavaParser.parse(sourceFile)
            val classDecl = cu.findFirst(ClassOrInterfaceDeclaration::class.java) { it.nameAsString == clazz.simpleName }.orElse(null) ?: return@forEach
            
            val fieldsWithNonNull = classDecl.findAll(FieldDeclaration::class.java)
                .filter { field -> field.annotations.any { it.name.identifier == "NonNull" } }
                .map { it.variables.first().nameAsString }
                .toSet()

            val returnType = findBotApiMethodReturnType(clazz) ?: return@forEach
            val functionName = clazz.simpleName.replaceFirstChar { it.lowercase() }
            
            val fieldsByName = getAllFields(clazz)
            val setters = getAllMethods(clazz)
                .filter { it.name.startsWith("set") && it.parameterCount == 1 && Modifier.isPublic(it.modifiers) }
                .groupBy { it.name.substring(3).replaceFirstChar { c -> c.lowercase() } }

            val (requiredSetters, optionalSetters) = setters.entries.partition { (propertyName, _) ->
                propertyName in fieldsWithNonNull
            }

            val optionalParams = optionalSetters.map { (propertyName, setterGroup) ->
                val representativeSetter = setterGroup.first()
                ParameterSpec.builder(propertyName, representativeSetter.genericParameterTypes.first().asTypeName().toKotlinType().copy(nullable = true))
                    .defaultValue("null")
                    .build()
            }.sortedBy { it.name }

            if (requiredSetters.isEmpty() && optionalSetters.isEmpty()) return@forEach

            val requiredSetterCombinations = if (requiredSetters.isNotEmpty()) {
                cartesianProduct(requiredSetters.map { it.value })
            } else {
                listOf(emptyList())
            }

            requiredSetterCombinations.forEach { combination ->
                val requiredParams = combination.map { setter ->
                    val propertyName = setter.name.substring(3).replaceFirstChar { c -> c.lowercase() }
                    ParameterSpec.builder(propertyName, setter.genericParameterTypes.first().asTypeName().toKotlinType()).build()
                }

                val finalReturnType: TypeName = when (val typeName = returnType.asTypeName().toKotlinType()) {
                    is ClassName -> if (typeName.canonicalName == "java.io.Serializable") UNIT else typeName
                    else -> typeName
                }

                val funSpecBuilder = FunSpec.builder(functionName)
                    .addModifiers(KModifier.SUSPEND)
                    .receiver(AbsSender::class)
                    .returns(finalReturnType)
                    .addParameters(requiredParams.sortedBy { it.name })
                    .addParameters(optionalParams)
                    .addStatement("val apiMethod = %T()", clazz.asTypeName())

                val classDeprecation = clazz.getAnnotation(Deprecated::class.java)
                val deprecatedSettersInCombination = combination.filter { it.isAnnotationPresent(Deprecated::class.java) }
                
                if (classDeprecation != null || deprecatedSettersInCombination.isNotEmpty()) {
                    val message = classDeprecation?.message ?: "This method uses deprecated parameters."
                    funSpecBuilder.addAnnotation(AnnotationSpec.builder(Deprecated::class)
                        .addMember("message = %S", message)
                        .build())
                }

                combination.forEach { setter ->
                    val propertyName = setter.name.substring(3).replaceFirstChar { it.lowercase() }
                    val field = fieldsByName[propertyName]
                    val usePropertyAccess = field != null && setter.parameterTypes[0] == field.type
                    if (usePropertyAccess) {
                        funSpecBuilder.addStatement("apiMethod.$propertyName = $propertyName")
                    } else {
                        funSpecBuilder.addStatement("apiMethod.${setter.name}($propertyName)")
                    }
                }

                optionalSetters.forEach { (propertyName, setterGroup) ->
                    val representativeSetter = setterGroup.first()
                    val field = fieldsByName[propertyName]
                    val usePropertyAccess = field != null && representativeSetter.parameterTypes[0] == field.type

                    val block = CodeBlock.builder().beginControlFlow("if ($propertyName != null)")
                    if (usePropertyAccess) {
                        block.addStatement("apiMethod.$propertyName = $propertyName")
                    } else {
                        block.addStatement("apiMethod.${representativeSetter.name}($propertyName)")
                    }
                    block.endControlFlow()
                    funSpecBuilder.addCode(block.build())
                }

                if (finalReturnType == UNIT) {
                    funSpecBuilder.addStatement("this.executeAsync(apiMethod).await()")
                } else {
                    funSpecBuilder.addStatement("return this.executeAsync(apiMethod).await() as %T", finalReturnType)
                }
                
                fileSpecBuilder.addFunction(funSpecBuilder.build())
                functionsGenerated++
            }
        }

        logger.warn("--- Generation Summary ---")
        logger.warn("Total functions generated: $functionsGenerated")

        if (functionsGenerated == 0) {
            logger.error("FATAL: No functions were generated.")
            throw IllegalStateException("Function generation failed.")
        }

        fileSpecBuilder.build().writeTo(outputDir.get().asFile)
        logger.warn("--- Generation Finished Successfully ---")
    }
}
