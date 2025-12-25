import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.squareup.kotlinpoet.*
import org.gradle.api.tasks.Internal
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.bots.AbsSender
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.*

abstract class GenerateTelegramBotExtensionsTask : AbstractReflectionGeneratorTask() {

    @Internal override val targetPackageName = "io.github.kochkaev.kotlin.telegrambots.dsl"
    @Internal override val targetFileName = "TelegramBotExtensions"
    @Internal override val reflectionBaseClass = BotApiMethod::class.java

    private fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return listOf(emptyList())
        val head = lists.first()
        val tail = lists.drop(1)
        val tailProduct = cartesianProduct(tail)
        return head.flatMap { item ->
            tailProduct.map { product -> listOf(item) + product }
        }
    }

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

    override fun generateFunctionsForClass(
        fileSpecBuilder: FileSpec.Builder,
        clazz: Class<*>,
        classDecl: ClassOrInterfaceDeclaration
    ): Int {
        fileSpecBuilder.addImport("kotlinx.coroutines.future", "await")

        val deprecatedFieldsWithMessages = classDecl.findAll(FieldDeclaration::class.java)
            .filter { it.isAnnotationPresent("Deprecated") }
            .flatMap { field ->
                val javadocMessage = field.javadoc.flatMap { javadoc ->
                    javadoc.blockTags.find { it.tagName == "deprecated" }?.content?.toText().optional()
                }.orElse("Field is deprecated.")
                field.variables.map { variable ->
                    variable.nameAsString to javadocMessage
                }
            }.toMap()
        val deprecatedFields = deprecatedFieldsWithMessages.keys

        val fieldsWithNonNull = classDecl.findAll(FieldDeclaration::class.java)
            .filter { field -> field.annotations.any { it.name.identifier == "NonNull" } }
            .map { it.variables.first().nameAsString }
            .toSet()

        val returnType = findBotApiMethodReturnType(clazz) ?: return 0
        val functionName = clazz.simpleName.replaceFirstChar { it.lowercase() }

        val setters = getAllMethods(clazz)
            .filter { it.name.startsWith("set") && it.parameterCount == 1 && java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .groupBy { it.name.substring(3).replaceFirstChar { c -> c.lowercase() } }

        val (requiredSetters, optionalSetters) = setters.entries.partition { (propertyName, _) ->
            propertyName in fieldsWithNonNull
        }

        if (requiredSetters.isEmpty() && optionalSetters.isEmpty()) return 0

        val requiredSetterCombinations = if (requiredSetters.isNotEmpty()) {
            cartesianProduct(requiredSetters.map { it.value })
        } else {
            listOf(emptyList())
        }

        val (deprecatedOptionalSetters, nonDeprecatedOptionalSetters) = optionalSetters.partition { (propertyName, _) ->
            propertyName in deprecatedFields
        }

        val nonDeprecatedOptionalParams = nonDeprecatedOptionalSetters.map { (propertyName, setterGroup) ->
            val representativeSetter = setterGroup.first()
            ParameterSpec.builder(propertyName, representativeSetter.genericParameterTypes.first().asTypeName().toKotlinType().copy(nullable = true))
                .defaultValue("null")
                .build()
        }.sortedBy { it.name }

        val isClassDeprecated = classDecl.isAnnotationPresent("Deprecated")
        val classDeprecationMessage = if (isClassDeprecated) {
            classDecl.javadoc.flatMap { javadoc ->
                Optional.ofNullable(javadoc.blockTags.find { it.tagName == "deprecated" }?.content?.toText())
            }.orElse("Class ${clazz.simpleName} is deprecated.")
        } else {
            null
        }

        var functionsGenerated = 0
        requiredSetterCombinations.forEach { combination ->
            val requiredParams = combination.map { setter ->
                val propertyName = setter.name.substring(3).replaceFirstChar { c -> c.lowercase() }
                ParameterSpec.builder(propertyName, setter.genericParameterTypes.first().asTypeName().toKotlinType()).build()
            }

            val deprecatedRequiredProperties = combination.map { it.name.substring(3).replaceFirstChar { c -> c.lowercase() } }.filter { it in deprecatedFields }
            val isCombinationDeprecated = deprecatedRequiredProperties.isNotEmpty()

            val combinationDeprecationMessage = if (isCombinationDeprecated) {
                val messages = deprecatedRequiredProperties.joinToString(separator = "\n") { prop ->
                    "- '$prop': ${deprecatedFieldsWithMessages[prop]}"
                }
                "This function uses deprecated required parameters:\n$messages"
            } else null

            generateFunction(
                fileSpecBuilder,
                clazz,
                functionName,
                returnType,
                requiredParams,
                nonDeprecatedOptionalParams,
                setters,
                combination,
                isClassDeprecated || isCombinationDeprecated,
                classDeprecationMessage ?: combinationDeprecationMessage
            )
            functionsGenerated++

            if (deprecatedOptionalSetters.isNotEmpty()) {
                val deprecatedOptionalParams = deprecatedOptionalSetters.map { (propertyName, setterGroup) ->
                    val representativeSetter = setterGroup.first()
                    ParameterSpec.builder(propertyName, representativeSetter.genericParameterTypes.first().asTypeName().toKotlinType().copy(nullable = true))
                        .defaultValue("null")
                        .build()
                }.sortedBy { it.name }

                val deprecatedOptionalProperties = deprecatedOptionalSetters.map { it.key }
                val allDeprecatedProperties = (deprecatedRequiredProperties + deprecatedOptionalProperties).distinct()
                val deprecationMessage = allDeprecatedProperties.joinToString(separator = "\n") { prop ->
                    "- '$prop': ${deprecatedFieldsWithMessages[prop]}"
                }

                generateFunction(
                    fileSpecBuilder,
                    clazz,
                    functionName,
                    returnType,
                    requiredParams,
                    nonDeprecatedOptionalParams + deprecatedOptionalParams,
                    setters,
                    combination,
                    true,
                    classDeprecationMessage ?: "This function includes deprecated parameters:\n$deprecationMessage"
                )
                functionsGenerated++
            }
        }
        return functionsGenerated
    }

    private fun generateFunction(
        fileSpecBuilder: FileSpec.Builder,
        clazz: Class<*>,
        functionName: String,
        returnType: java.lang.reflect.Type,
        requiredParams: List<ParameterSpec>,
        optionalParams: List<ParameterSpec>,
        setters: Map<String, List<Method>>,
        combination: List<Method>,
        deprecated: Boolean,
        deprecationMessage: String?
    ) {
        val finalReturnType: TypeName = when (val typeName = returnType.asTypeName().toKotlinType()) {
            is ClassName -> if (typeName.canonicalName == "java.io.Serializable") UNIT else typeName
            else -> typeName
        }

        val funSpecBuilder = FunSpec.builder(functionName)
            .addModifiers(KModifier.SUSPEND)
            .receiver(AbsSender::class)
            .returns(finalReturnType)
            .addKdoc("Extension function for [%T.executeAsync] and [%T].\n\n@see %T", AbsSender::class, clazz, clazz)
            .addParameters(requiredParams.sortedBy { it.name })
            .addParameters(optionalParams)
            .addStatement("val apiMethod = %T()", clazz.asTypeName())

        if (deprecated) {
            funSpecBuilder.addAnnotation(AnnotationSpec.builder(Deprecated::class)
                .addMember("message = %S", deprecationMessage ?: "This function is deprecated.")
                .build())
        }

        combination.forEach { setter ->
            val propertyName = setter.name.substring(3).replaceFirstChar { c -> c.lowercase() }
            funSpecBuilder.addStatement("apiMethod.${setter.name}($propertyName)")
        }

        optionalParams.forEach { param ->
            val setter = setters[param.name]!!.first()
            funSpecBuilder.addCode(
                CodeBlock.builder()
                    .beginControlFlow("if (${param.name} != null)")
                    .addStatement("apiMethod.${setter.name}(${param.name})")
                    .endControlFlow()
                    .build()
            )
        }

        if (finalReturnType == UNIT) {
            funSpecBuilder.addStatement("this.executeAsync(apiMethod).await()")
        } else {
            funSpecBuilder.addStatement("return this.executeAsync(apiMethod).await() as %T", finalReturnType)
        }

        fileSpecBuilder.addFunction(funSpecBuilder.build())
    }
}
