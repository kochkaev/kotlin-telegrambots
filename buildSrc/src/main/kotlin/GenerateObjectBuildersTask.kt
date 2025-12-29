import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.squareup.kotlinpoet.*
import org.gradle.api.tasks.Internal
import org.telegram.telegrambots.meta.api.interfaces.BotApiObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

abstract class GenerateObjectBuildersTask : AbstractReflectionGeneratorTask() {

    @Internal override val targetPackageName = "io.github.kochkaev.kotlin.telegrambots.dsl"
    @Internal override val targetFileName = "ObjectBuilders"
    @Internal override val reflectionBaseClass = BotApiObject::class.java

    private fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return listOf(emptyList())
        val head = lists.first()
        val tail = lists.drop(1)
        val tailProduct = cartesianProduct(tail)
        return head.flatMap { item ->
            tailProduct.map { product -> listOf(item) + product }
        }
    }

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
            .filter { it.name.startsWith("set") && it.parameterCount == 1 && Modifier.isPublic(it.modifiers) }
            .groupBy { it.name.substring(3).replaceFirstChar { c -> c.lowercase() } }

        if (setters.isEmpty()) return 0

        val (overloadedSetters, singleSetters) = setters.entries.partition { (_, setterGroup) ->
            setterGroup.size > 1
        }

        val combinationSetters = if (overloadedSetters.isNotEmpty()) {
            cartesianProduct(overloadedSetters.map { it.value })
        } else {
            listOf(emptyList())
        }

        val singleParamsData = singleSetters.map { (propertyName, setterGroup) ->
            val setter = setterGroup.first()
            val isRequired = propertyName in fieldsWithNonNull
            val paramType = setter.genericParameterTypes.first().asTypeName().toKotlinType()
            val param = ParameterSpec.builder(propertyName, if (isRequired) paramType else paramType.copy(nullable = true))
                .apply { if (!isRequired) defaultValue("null") }
                .build()
            param to setter
        }

        val isClassDeprecated = classDecl.isAnnotationPresent("Deprecated")
        val classDeprecationMessage = if (isClassDeprecated) {
            classDecl.javadoc.flatMap { javadoc ->
                Optional.ofNullable(javadoc.blockTags.find { it.tagName == "deprecated" }?.content?.toText())
            }.orElse("Class ${clazz.simpleName} is deprecated.")
        } else {
            null
        }

        var functionsGenerated = 0
        combinationSetters.forEach { combination ->
            val combinationParamsData = combination.map { setter ->
                val propertyName = setter.name.substring(3).replaceFirstChar { c -> c.lowercase() }
                val isRequired = propertyName in fieldsWithNonNull
                val paramType = setter.genericParameterTypes.first().asTypeName().toKotlinType()
                val param = ParameterSpec.builder(propertyName, if (isRequired) paramType else paramType.copy(nullable = true))
                    .apply { if (!isRequired) defaultValue("null") }
                    .build()
                param to setter
            }

            val allParamsData = (singleParamsData + combinationParamsData).sortedBy { it.first.name }

            val deprecatedProperties = allParamsData.map { it.first.name }.filter { it in deprecatedFields }
            val isFunctionDeprecated = deprecatedProperties.isNotEmpty()

            val deprecationMessage = if (isFunctionDeprecated) {
                val messages = deprecatedProperties.joinToString(separator = "\n") { prop ->
                    "- '$prop': ${deprecatedFieldsWithMessages[prop]}"
                }
                "This function uses deprecated parameters:\n$messages"
            } else null

            generateFunction(
                fileSpecBuilder,
                clazz,
                functionName,
                allParamsData,
                isClassDeprecated || isFunctionDeprecated,
                classDeprecationMessage ?: deprecationMessage
            )
            functionsGenerated++
        }
        return functionsGenerated
    }

    private fun generateFunction(
        fileSpecBuilder: FileSpec.Builder,
        clazz: Class<*>,
        functionName: String,
        allParamsData: List<Pair<ParameterSpec, Method>>,
        deprecated: Boolean,
        deprecationMessage: String?
    ) {
        val funSpecBuilder = FunSpec.builder(functionName)
            .returns(clazz.asTypeName().toKotlinType())
            .addKdoc("Builder function for [%T].\n\n@see %T", clazz, clazz)
            .addParameters(allParamsData.map { it.first })
            .addStatement("val obj = %T()", clazz.asTypeName())

        if (deprecated) {
            funSpecBuilder.addAnnotation(AnnotationSpec.builder(Deprecated::class)
                .addMember("message = %S", deprecationMessage ?: "This function is deprecated.")
                .build())
        }

        allParamsData.forEach { (param, setter) ->
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
