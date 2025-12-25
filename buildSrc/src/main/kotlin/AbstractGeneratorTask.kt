import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory

abstract class AbstractGeneratorTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal protected abstract val targetPackageName: String
    @get:Internal protected abstract val targetFileName: String

    @Internal
    protected val javaToKotlinMap: Map<String, TypeName> = mapOf(
        // Primitives and their wrappers
        "boolean" to BOOLEAN, "java.lang.Boolean" to BOOLEAN,
        "char" to CHAR, "java.lang.Character" to CHAR,
        "byte" to BYTE, "java.lang.Byte" to BYTE,
        "short" to SHORT, "java.lang.Short" to SHORT,
        "int" to INT, "java.lang.Integer" to INT,
        "long" to LONG, "java.lang.Long" to LONG,
        "float" to FLOAT, "java.lang.Float" to FLOAT,
        "double" to DOUBLE, "java.lang.Double" to DOUBLE,
        "void" to UNIT, // Java void maps to Kotlin Unit

        // Common Java types to Kotlin equivalents
        "java.lang.String" to STRING,
        "java.lang.Object" to ANY,
        "java.io.Serializable" to ClassName("java.io", "Serializable"),

        // Collections
        "java.util.List" to LIST,
        "java.util.ArrayList" to ClassName("kotlin.collections", "ArrayList"),
        "java.util.Set" to SET,
        "java.util.HashSet" to ClassName("kotlin.collections", "HashSet"),
        "java.util.Map" to MAP,
        "java.util.HashMap" to ClassName("kotlin.collections", "HashMap"),
        "java.util.Collection" to COLLECTION,

        // CompletableFuture
        "java.util.concurrent.CompletableFuture" to ClassName("java.util.concurrent", "CompletableFuture")
    )

    protected fun Type.toKotlinTypeName(method: MethodDeclaration? = null): TypeName {
        if (method != null && this.isClassOrInterfaceType) {
            val typeName = this.asClassOrInterfaceType().name.asString()
            val methodTypeParam = method.typeParameters.firstOrNull { it.name.asString() == typeName }
            if (methodTypeParam != null) {
                val bounds = methodTypeParam.typeBound.map { it.toKotlinTypeName(method) }
                return TypeVariableName(typeName, bounds)
            }
        }

        if (this.isPrimitiveType) {
            return javaToKotlinMap[this.asPrimitiveType().type.asString()] ?: throw IllegalStateException("Unknown primitive type: ${this.asPrimitiveType().type.asString()}")
        }

        val resolvedType = this.resolve()
        if (resolvedType is ResolvedReferenceType) {
            val fqn = resolvedType.qualifiedName
            val mappedTypeName = javaToKotlinMap[fqn]
            val baseTypeName = mappedTypeName ?: ClassName.bestGuess(fqn)
            if (this is ClassOrInterfaceType && this.typeArguments.isPresent) {
                val typeArgs = this.typeArguments.get().map { it.toKotlinTypeName(method) }.toTypedArray()
                return (baseTypeName as ClassName).parameterizedBy(*typeArgs)
            }
            return baseTypeName
        }
        if (this.isTypeParameter) {
            return TypeVariableName(this.asTypeParameter().nameAsString)
        }

        throw IllegalStateException("Unsupported resolved type: ${resolvedType.javaClass.simpleName} (${resolvedType.describe()})")
    }
}
