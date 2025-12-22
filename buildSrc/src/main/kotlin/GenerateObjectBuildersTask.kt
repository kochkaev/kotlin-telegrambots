import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.squareup.kotlinpoet.*
import org.gradle.api.tasks.Internal
import org.telegram.telegrambots.meta.api.interfaces.BotApiObject
import java.lang.reflect.Method
import java.util.*

abstract class GenerateObjectBuildersTask : AbstractGeneratorTask() {

    @Internal override val targetPackageName = "io.github.kochkaev.kotlin.telegrambots"
    @Internal override val targetFileName = "ObjectBuilders"
    @Internal override val reflectionBaseClass = BotApiObject::class.java

    override fun generateFunctionsForClass(
        fileSpecBuilder: FileSpec.Builder,
        clazz: Class<*>,
        classDecl: ClassOrInterfaceDeclaration
    ): Int {
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

        val functionName = clazz.simpleName.replaceFirstChar { it.lowercase() }

        val setters = getAllMethods(clazz)
            .filter { it.name.startsWith("set") && it.parameterCount == 1 && java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .associateBy { it.name.substring(3).replaceFirstChar { c -> c.lowercase() } }

        if (setters.isEmpty()) return 0

        val (requiredSetters, optionalSetters) = setters.entries.partition { (propertyName, _) ->
            propertyName in fieldsWithNonNull
        }

        val requiredParams = requiredSetters.sortedBy { it.key }.map { (propertyName, setter) ->
            ParameterSpec.builder(propertyName, setter.genericParameterTypes.first().asTypeName().toKotlinType()).build()
        }

        val (deprecatedOptionalSetters, nonDeprecatedOptionalSetters) = optionalSetters.partition { (propertyName, _) ->
            propertyName in deprecatedFields
        }

        val nonDeprecatedOptionalParams = nonDeprecatedOptionalSetters.sortedBy { it.key }.map { (propertyName, setter) ->
            ParameterSpec.builder(propertyName, setter.genericParameterTypes.first().asTypeName().toKotlinType().copy(nullable = true))
                .defaultValue("null")
                .build()
        }

        val isClassDeprecated = classDecl.isAnnotationPresent("Deprecated")
        val classDeprecationMessage = if (isClassDeprecated) {
            classDecl.javadoc.flatMap { javadoc ->
                Optional.ofNullable(javadoc.blockTags.find { it.tagName == "deprecated" }?.content?.toText())
            }.orElse("Class ${clazz.simpleName} is deprecated.")
        } else {
            null
        }

        val deprecatedRequiredProperties = requiredSetters.map { it.key }.filter { it in deprecatedFields }
        val isFunctionDeprecated = deprecatedRequiredProperties.isNotEmpty()
        val deprecationMessage = if (isFunctionDeprecated) {
            val messages = deprecatedRequiredProperties.joinToString(separator = "\n") { prop ->
                "- '$prop': ${deprecatedFieldsWithMessages[prop]}"
            }
            "This function uses deprecated required parameters:\n$messages"
        } else null

        generateFunction(
            fileSpecBuilder,
            clazz,
            functionName,
            requiredParams,
            nonDeprecatedOptionalParams,
            setters,
            isClassDeprecated || isFunctionDeprecated,
            classDeprecationMessage ?: deprecationMessage
        )
        var functionsGenerated = 1

        if (deprecatedOptionalSetters.isNotEmpty()) {
            val deprecatedOptionalParams = (deprecatedOptionalSetters.sortedBy { it.key }.map { (propertyName, setter) ->
                ParameterSpec.builder(propertyName, setter.genericParameterTypes.first().asTypeName().toKotlinType().copy(nullable = true))
                    .defaultValue("null")
                    .build()
            })

            val deprecatedOptionalProperties = deprecatedOptionalSetters.map { it.key }
            val allDeprecatedProperties = (deprecatedRequiredProperties + deprecatedOptionalProperties).distinct()
            val fullDeprecationMessage = allDeprecatedProperties.joinToString(separator = "\n") { prop ->
                "- '$prop': ${deprecatedFieldsWithMessages[prop]}"
            }

            generateFunction(
                fileSpecBuilder,
                clazz,
                functionName,
                requiredParams,
                nonDeprecatedOptionalParams + deprecatedOptionalParams,
                setters,
                true,
                classDeprecationMessage ?: "This function includes deprecated parameters:\n$fullDeprecationMessage"
            )
            functionsGenerated++
        }
        return functionsGenerated
    }

    private fun generateFunction(
        fileSpecBuilder: FileSpec.Builder,
        clazz: Class<*>,
        functionName: String,
        requiredParams: List<ParameterSpec>,
        optionalParams: List<ParameterSpec>,
        setters: Map<String, Method>,
        deprecated: Boolean,
        deprecationMessage: String?
    ) {
        val funSpecBuilder = FunSpec.builder(functionName)
            .returns(clazz.asTypeName().toKotlinType())
            .addKdoc("Builder function for [%T].\n\n@see %T", clazz, clazz)
            .addParameters(requiredParams)
            .addParameters(optionalParams)
            .addStatement("val obj = %T()", clazz.asTypeName())

        if (deprecated) {
            funSpecBuilder.addAnnotation(AnnotationSpec.builder(Deprecated::class)
                .addMember("message = %S", deprecationMessage ?: "This function is deprecated.")
                .build())
        }

        (requiredParams + optionalParams).forEach { param ->
            val setter = setters[param.name]!!
            if (param.type.isNullable) {
                funSpecBuilder.addCode(
                    CodeBlock.builder()
                        .beginControlFlow("if (${param.name} != null)")
                        .addStatement("obj.${setter.name}(${param.name})")
                        .endControlFlow()
                        .build()
                )
            } else {
                funSpecBuilder.addStatement("obj.${setter.name}(${param.name})")
            }
        }

        funSpecBuilder.addStatement("return obj")
        fileSpecBuilder.addFunction(funSpecBuilder.build())
    }
}
