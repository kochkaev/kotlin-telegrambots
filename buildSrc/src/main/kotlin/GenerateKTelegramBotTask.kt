import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.io.File
import kotlin.jvm.optionals.getOrNull

abstract class GenerateKTelegramBotTask : DefaultTask() {

    @get:InputDirectory
    abstract val botSourcesDir: DirectoryProperty

    @get:InputDirectory
    abstract val metaSourcesDir: DirectoryProperty

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
        val sourceDirs = listOf(botSourcesDir.get().asFile, metaSourcesDir.get().asFile)

        cu.imports.firstOrNull { !it.isAsterisk && it.nameAsString.endsWith(".$typeName") }?.let {
            return it.nameAsString
        }
        val wildcardImports = cu.imports.filter { it.isAsterisk }.map { it.nameAsString }
        for (pkg in wildcardImports) {
            val potentialFqn = "$pkg.$typeName"
            val potentialPath = potentialFqn.replace('.', File.separatorChar) + ".java"
            sourceDirs.firstNotNullOfOrNull {
                if (it.resolve(potentialPath).exists()) potentialFqn else null
            }?.let { return it }
        }
        cu.packageDeclaration.getOrNull()?.let { pkg ->
            val potentialFqn = "${pkg.nameAsString}.$typeName"
            val potentialPath = potentialFqn.replace('.', File.separatorChar) + ".java"
            sourceDirs.firstNotNullOfOrNull {
                if (it.resolve(potentialPath).exists()) potentialFqn else null
            }?.let { return it }
        }
        return null
    }

    private fun Type.toKotlinTypeName(cu: CompilationUnit, method: MethodDeclaration): TypeName {
        val methodTypeParamNames = method.typeParameters.map { it.nameAsString }

        if (this.isClassOrInterfaceType) {
            val classOrInterfaceType = this.asClassOrInterfaceType()
            val typeName = classOrInterfaceType.name.asString()

            if (methodTypeParamNames.contains(typeName)) {
                val typeVar = method.typeParameters.first { it.nameAsString == typeName }
                val bounds = typeVar.typeBound.map { it.toKotlinTypeName(cu, method) }
                return TypeVariableName(typeName, bounds)
            }
        }

        if (this.isTypeParameter) {
            val typeVar = this.asTypeParameter()
            val bounds = typeVar.typeBound.map { it.toKotlinTypeName(cu, method) }
            return TypeVariableName(typeVar.nameAsString, bounds)
        }

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
                val typeArgs = this.typeArguments.get().map { it.toKotlinTypeName(cu, method) }.toTypedArray()
                return (className as ClassName).parameterizedBy(*typeArgs)
            }
            return className
        }
        throw IllegalStateException("Unsupported type: ${this.javaClass.simpleName} ($this)")
    }

    @TaskAction
    fun execute() {
        val packageName = "io.github.kochkaev.kotlin.telegrambots.core"
        val className = "DefaultKAbsSender"
        val senderFile = botSourcesDir.get().asFile.walk().first { it.isFile && it.name == "DefaultAbsSender.java" }
        val cu = StaticJavaParser.parse(senderFile)
        val classDecl = cu.getClassByName("DefaultAbsSender").orElseThrow { IllegalStateException("DefaultAbsSender class not found") }

        val fileSpecBuilder = FileSpec.builder(packageName, className)
            .addAnnotation(AnnotationSpec.builder(Suppress::class).useSiteTarget(AnnotationSpec.UseSiteTarget.FILE).addMember("%S, %S, %S", "unused", "RedundantVisibilityModifier", "DEPRECATION").build())
            .addImport("kotlinx.coroutines.future", "await")

        val classBuilder = TypeSpec.classBuilder(className)
            .superclass(DefaultAbsSender::class)
            .addModifiers(KModifier.OPEN)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("options", DefaultBotOptions::class)
                    .addParameter("token", String::class)
                    .build()
            )
            .addSuperclassConstructorParameter("options")
            .addSuperclassConstructorParameter("token")

        val asyncMethods = classDecl.methods.filter {
            (it.isProtected || it.isPublic) && it.type.asString().startsWith("CompletableFuture")
        }

        asyncMethods.forEach { method ->
            val newName = method.nameAsString.replace("Async", "K")
            val returnType = (method.type.toKotlinTypeName(cu, method) as ParameterizedTypeName).typeArguments.first()

            val funSpec = FunSpec.builder(newName)
                .apply {
                    if (method.isProtected) addModifiers(KModifier.PROTECTED)
                    if (method.isAnnotationPresent("Deprecated")) {
                        addAnnotation(AnnotationSpec.builder(Deprecated::class).addMember("%S", "Super method is deprecated").build())
                    }
                }
                .addModifiers(KModifier.SUSPEND)
                .returns(returnType)
                .apply {
                    method.typeParameters.forEach { typeParam ->
                        val bounds = typeParam.typeBound.map { it.toKotlinTypeName(cu, method) }
                        addTypeVariable(TypeVariableName(typeParam.nameAsString, bounds))
                    }
                    method.parameters.forEach { param ->
                        addParameter(param.nameAsString, param.type.toKotlinTypeName(cu, method))
                    }
                    val paramNames = method.parameters.joinToString(", ") { it.nameAsString }
                    addStatement("return %L(%L).await()", method.nameAsString, paramNames)
                }
                .build()
            classBuilder.addFunction(funSpec)
        }

        fileSpecBuilder.addType(classBuilder.build())
        fileSpecBuilder.build().writeTo(outputDir.get().asFile)
    }
}
