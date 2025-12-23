import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.telegram.telegrambots.meta.bots.AbsSender
import java.io.File
import kotlin.jvm.optionals.getOrNull

abstract class GenerateHandlersDslTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val javaToKotlinMap = mapOf(
        "String" to STRING, "Integer" to INT, "Long" to LONG,
        "Short" to SHORT, "Byte" to BYTE, "Float" to FLOAT,
        "Double" to DOUBLE, "Character" to CHAR, "Boolean" to BOOLEAN,
        "List" to LIST, "Map" to MAP, "Set" to SET, "Collection" to COLLECTION,
        "Serializable" to ClassName("java.io", "Serializable"),
        "File" to ClassName("org.telegram.telegrambots.meta.api.objects", "File"),
        "CompletableFuture" to ClassName("java.util.concurrent", "CompletableFuture")
    )

    private fun findFqn(cu: CompilationUnit, typeName: String): String? {
        cu.imports.firstOrNull { !it.isAsterisk && it.nameAsString.endsWith(".$typeName") }?.let {
            return it.nameAsString
        }
        val wildcardImports = cu.imports.filter { it.isAsterisk }.map { it.nameAsString }
        for (pkg in wildcardImports) {
            val potentialFqn = "$pkg.$typeName"
            val potentialPath = potentialFqn.replace('.', File.separatorChar) + ".java"
            if (sourcesDir.get().file(potentialPath).asFile.exists()) {
                return potentialFqn
            }
        }
        cu.packageDeclaration.getOrNull()?.let { pkg ->
            val potentialFqn = "${pkg.nameAsString}.$typeName"
            val potentialPath = potentialFqn.replace('.', File.separatorChar) + ".java"
            if (sourcesDir.get().file(potentialPath).asFile.exists()) {
                return potentialFqn
            }
        }
        return null
    }

    private fun Type.toKotlinTypeName(cu: CompilationUnit): TypeName {
        if (this.isVoidType) return UNIT
        if (this.isPrimitiveType) {
            val primitive = this.asPrimitiveType()
            return when (primitive.type) {
                com.github.javaparser.ast.type.PrimitiveType.Primitive.BOOLEAN -> BOOLEAN
                com.github.javaparser.ast.type.PrimitiveType.Primitive.CHAR -> CHAR
                com.github.javaparser.ast.type.PrimitiveType.Primitive.BYTE -> BYTE
                com.github.javaparser.ast.type.PrimitiveType.Primitive.SHORT -> SHORT
                com.github.javaparser.ast.type.PrimitiveType.Primitive.INT -> INT
                com.github.javaparser.ast.type.PrimitiveType.Primitive.LONG -> LONG
                com.github.javaparser.ast.type.PrimitiveType.Primitive.FLOAT -> FLOAT
                com.github.javaparser.ast.type.PrimitiveType.Primitive.DOUBLE -> DOUBLE
            }
        }
        if (this is ClassOrInterfaceType) {
            val typeName = this.name.asString()
            val fqn = findFqn(cu, typeName) ?: "java.lang.$typeName"
            val className = javaToKotlinMap[typeName] ?: ClassName.bestGuess(fqn)
            if (this.typeArguments.isPresent) {
                val typeArgs = this.typeArguments.get().map { it.toKotlinTypeName(cu) }.toTypedArray()
                return (className as ClassName).parameterizedBy(*typeArgs)
            }
            return className
        }
        throw IllegalStateException("Unsupported type: ${this.javaClass.simpleName} ($this)")
    }

    @TaskAction
    fun execute() {
        val packageName = "io.github.kochkaev.kotlin.telegrambots.dsl"
        val fileName = "GeneratedHandlers"
        val updateFile = sourcesDir.get().asFile.walk().first { it.isFile && it.name == "Update.java" }
        val cu = StaticJavaParser.parse(updateFile)
        val classDecl = cu.getClassByName("Update").orElseThrow { IllegalStateException("Update class not found") }

        val fileSpecBuilder = FileSpec.builder(packageName, fileName)
            .addAnnotation(AnnotationSpec
                .builder(Suppress::class)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                .addMember("%S, %S", "unused", "RedundantVisibilityModifier")
                .build())
            .addImport("kotlinx.coroutines.flow", "filter")
            .addImport("kotlinx.coroutines", "launch")


        val methodNames = classDecl.methods.map { it.nameAsString }.toSet()

        classDecl.fields.forEach { field ->
            val fieldName = field.variables.first().nameAsString
            val capitalizedFieldName = fieldName.replaceFirstChar { it.titlecase() }
            val hasMethodName = "has$capitalizedFieldName"

            if (methodNames.contains(hasMethodName)) {
                val handlerName = "on$capitalizedFieldName"
                val fieldType = field.elementType.toKotlinTypeName(cu)

                val funSpec = FunSpec.builder(handlerName)
                    .receiver(ClassName(packageName, "HandlersDsl"))
                    .addParameter("handler", LambdaTypeName.get(receiver = AbsSender::class.java.asTypeName(), parameters = listOf(ParameterSpec.unnamed(fieldType)), returnType = UNIT).copy(suspending = true))
                    .addStatement("scope.launch { updates.filter { it.%L() }.collect { with(bot) { handler(it.%L) } } }", hasMethodName, fieldName)
                    .build()
                fileSpecBuilder.addFunction(funSpec)
            }
        }

        fileSpecBuilder.build().writeTo(outputDir.get().asFile)
    }
}
