import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.ConstructorDetector
import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.stmt.TryStmt
import com.github.javaparser.resolution.SymbolResolver
import com.github.javaparser.resolution.TypeSolver
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.TelegramUrl
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture

abstract class GenerateDefaultKTelegramClientTask : AbstractReflectionGeneratorTask() {

    @get:InputFiles
    abstract val dependencyJars: ConfigurableFileCollection

    @get:InputDirectory
    abstract val metaSourcesDir: DirectoryProperty

    @Internal override val targetPackageName = "io.github.kochkaev.kotlin.telegrambots.core"
    @Internal override val targetFileName = "DefaultKTelegramClient"
    @Internal override val reflectionBaseClass = OkHttpTelegramClient::class.java

    private val nonNullFieldsCache = mutableMapOf<ClassOrInterfaceDeclaration, Set<String>>()
    private lateinit var javaParser: JavaParser
    private lateinit var typeSolver: TypeSolver
    private lateinit var symbolResolver: SymbolResolver

    override fun generateFunctionsForClass(
        fileSpecBuilder: FileSpec.Builder,
        clazz: Class<*>,
        classDecl: ClassOrInterfaceDeclaration,
        noArgConstructor: Boolean
    ): Int = 0

    @TaskAction
    override fun execute() {
        logger.info("Starting generation for $targetFileName")

        val fileSpecBuilder = FileSpec.builder(targetPackageName, targetFileName)
            .indent("    ")
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                .addMember("%S, %S, %S", "unused", "RedundantVisibilityModifier", "DEPRECATION")
                .build())

        val abstractSuperclass = ClassName(targetPackageName, "AbstractKTelegramClient")
        val httpExecutor = ClassName(targetPackageName, "HttpExecutor")
        val botSerializer = ClassName(targetPackageName, "BotSerializer")
        val botDeserializer = ClassName(targetPackageName, "BotDeserializer")

        val constructor = FunSpec.constructorBuilder()
            .addParameter("token", String::class)
            .addParameter(ParameterSpec.builder("telegramUrl", TelegramUrl::class).defaultValue("TelegramUrl.DEFAULT_URL").build())
            .addParameter("executor", httpExecutor)
            .addParameter("serializer", botSerializer)
            .addParameter("deserializer", botDeserializer)
            .build()

        val classBuilder = TypeSpec.classBuilder(targetFileName)
            .addModifiers(KModifier.OPEN)
            .superclass(abstractSuperclass)
            .primaryConstructor(constructor)
            .addProperty(PropertySpec.builder("token", String::class, KModifier.OVERRIDE).initializer("token").build())
            .addProperty(PropertySpec.builder("telegramUrl", TelegramUrl::class, KModifier.OVERRIDE).initializer("telegramUrl").build())
            .addProperty(PropertySpec.builder("executor", httpExecutor, KModifier.OVERRIDE).initializer("executor").build())
            .addProperty(PropertySpec.builder("serializer", botSerializer, KModifier.OVERRIDE).initializer("serializer").build())
            .addProperty(PropertySpec.builder("deserializer", botDeserializer, KModifier.OVERRIDE).initializer("deserializer").build())

        // Generic executeAsync for simple JSON methods
        val t = TypeVariableName("T", Serializable::class)
        val m = TypeVariableName("M", BotApiMethod::class.asClassName().parameterizedBy(t))
        val executeAsyncFun = FunSpec.builder("executeAsync")
            .addModifiers(KModifier.OVERRIDE)
            .addTypeVariable(t)
            .addTypeVariable(m)
            .addParameter("method", m)
            .returns(CompletableFuture::class.asClassName().parameterizedBy(t))
            .beginControlFlow("return withExceptionHandling(method.method)")
            .addStatement("val url = getBotUrl() + method.method")
            .addStatement("val jsonBody = serializer.serialize(method)")
            .addStatement("executor.executeJson(url, jsonBody).thenApply { responseJson -> deserializer.deserialize(responseJson, method) }")
            .endControlFlow()
            .build()
        classBuilder.addFunction(executeAsyncFun)

        // --- Multipart Methods Generation ---
        val combinedTypeSolver = CombinedTypeSolver(
            ReflectionTypeSolver(),
            JavaParserTypeSolver(sourcesDir.get().asFile),
            JavaParserTypeSolver(metaSourcesDir.get().asFile)
        )
        typeSolver = combinedTypeSolver
        dependencyJars.forEach { jar ->
            combinedTypeSolver.add(JarTypeSolver(jar))
        }
        symbolResolver = JavaSymbolSolver(combinedTypeSolver)
        val parserConfiguration = ParserConfiguration().setSymbolResolver(symbolResolver)
        javaParser = JavaParser(parserConfiguration)

        val clientFile = sourcesDir.get().asFile.walk().firstOrNull { it.name == "OkHttpTelegramClient.java" }
        if (clientFile != null) {
            val cu = javaParser.parse(clientFile).result.get()
            val classDecl = cu.getClassByName("OkHttpTelegramClient").orElseThrow { IllegalStateException("OkHttpTelegramClient class not found") }
            
            val executeMediaMethodTemplate = classDecl.methods
                .firstOrNull { it.nameAsString == "executeMediaMethod" }
                ?.body?.get()?.statements?.toList() ?: emptyList()

            val reflectionMethods = getAllMethods(reflectionBaseClass)
            val parserMethodsBySignature = classDecl.methods.groupBy { it.getRefSignature(symbolResolver) }
            val reflectionToParserMethods = reflectionMethods.mapNotNull { refMethod ->
                val signature = refMethod.getSignature()
                parserMethodsBySignature[signature]?.firstOrNull()?.let { pMethod -> refMethod to pMethod }
            }

            val multipartMethods = reflectionToParserMethods.filter { (_, pMethod) ->
                pMethod.nameAsString == "executeAsync" &&
                pMethod.parameters.size == 1 &&
                pMethod.parameters[0].type.asString() != "Method" && // Exclude the generic one
                pMethod.body.map { body -> body.toString().contains("TelegramMultipartBuilder") || body.toString().contains("executeMediaMethod") }.orElse(false)
            }

            for ((method, methodDecl) in multipartMethods) {
                val param = method.parameters[0]
                val paramType = param.type.asTypeName().toKotlinType()
                val paramName = methodDecl.parameters[0].nameAsString
                val returnType = methodDecl.type.toKotlinTypeName(methodDecl, symbolResolver)

                val paramClassName = param.type.simpleName
                val paramFile = metaSourcesDir.get().asFile.walk().firstOrNull { it.name == "$paramClassName.java" } ?: continue // Skip if source not found
                val paramCU = javaParser.parse(paramFile).result.get()
                val paramClassDecl = paramCU.getClassByName(paramClassName).orElseThrow { IllegalStateException("$paramClassName class not found") }

                val funSpec = FunSpec.builder("executeAsync")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(paramName, paramType)
                    .returns(returnType)
                
                funSpec.beginControlFlow("return withExceptionHandling(%L.method)", paramName)

                funSpec.addStatement("val url = getBotUrl() + %L.method", paramName)
                funSpec.addStatement("val parts = mutableListOf<%T>()", ClassName(targetPackageName, "Part"))

                val body = methodDecl.body.get()
                val mediaMethodCall = body.findFirst(MethodCallExpr::class.java) { it.nameAsString == "executeMediaMethod" }

                if (mediaMethodCall.isPresent) {
                    executeMediaMethodTemplate.forEach { stmt ->
                        generateCodeForNode(stmt, funSpec, paramName, paramClassDecl, false, "method")
                    }
                    val lambdaBody = (mediaMethodCall.get().arguments[1] as LambdaExpr).body
                    if (lambdaBody.isBlockStmt) {
                        lambdaBody.asBlockStmt().statements.forEach { stmt ->
                            generateCodeForNode(stmt, funSpec, paramName, paramClassDecl, false, null)
                        }
                    } else {
                        generateCodeForNode(lambdaBody as Statement, funSpec, paramName, paramClassDecl, false, null)
                    }
                } else {
                    body.statements.forEach { stmt ->
                        generateCodeForNode(stmt, funSpec, paramName, paramClassDecl, false, null)
                    }
                }

                funSpec.addStatement("executor.executeMultipart(url, parts).thenApply { responseJson -> deserializer.deserialize(responseJson, %L) }", paramName)
                funSpec.endControlFlow()
                classBuilder.addFunction(funSpec.build())
            }
        }
        
        // Download methods
        val fileClass = org.telegram.telegrambots.meta.api.objects.File::class.asClassName()
        val javaFileClass = java.io.File::class.asClassName()
        val inputStreamClass = InputStream::class.asClassName()

        val downloadFileAsStreamAsyncFun = FunSpec.builder("downloadFileAsStreamAsync")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("file", fileClass)
            .returns(CompletableFuture::class.asClassName().parameterizedBy(inputStreamClass))
            .beginControlFlow("return withExceptionHandling(file.filePath ?: \"file\")")
            .addStatement("val filePath = file.filePath ?: throw %T(%S)", TelegramApiException::class, "File path is empty")
            .addStatement("val fileUrl = getBotFileUrl() + filePath")
            .addStatement("executor.downloadFile(fileUrl)")
            .endControlFlow()
            .build()
        classBuilder.addFunction(downloadFileAsStreamAsyncFun)

        val downloadFileAsyncFun = FunSpec.builder("downloadFileAsync")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("file", fileClass)
            .returns(CompletableFuture::class.asClassName().parameterizedBy(javaFileClass))
            .addCode("""
                return downloadFileAsStreamAsync(file).thenApply { stream ->
                    val outputFile = %T.createTempFile("telegram", "download")
                    stream.use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    outputFile
                }
            """.trimIndent(), javaFileClass)
            .build()
        classBuilder.addFunction(downloadFileAsyncFun)

        // Add helper function
        val tVar = TypeVariableName("T")
        val completableFutureOfT = CompletableFuture::class.asClassName().parameterizedBy(tVar)
        val lambdaReturningCompletableFuture = LambdaTypeName.get(returnType = completableFutureOfT)

        val withExceptionHandlingFun = FunSpec.builder("withExceptionHandling")
            .addModifiers(KModifier.PROTECTED, KModifier.INLINE)
            .addTypeVariable(tVar)
            .addParameter("methodName", String::class)
            .addParameter("block", lambdaReturningCompletableFuture, KModifier.CROSSINLINE)
            .returns(completableFutureOfT)
            .addCode("""
                return try {
                    block()
                } catch (e: %T) {
                    %T.failedFuture(e)
                } catch (e: %T) {
                    %T.failedFuture(
                        %T("Unable to execute ${'$'}methodName", e)
                    )
                }
            """.trimIndent(),
                TelegramApiException::class.asClassName(),
                CompletableFuture::class.asClassName(),
                IOException::class.asClassName(),
                CompletableFuture::class.asClassName(),
                TelegramApiException::class.asClassName()
            )
            .build()
        classBuilder.addFunction(withExceptionHandlingFun)

        classBuilder.addFunction(
            FunSpec.builder("getTelegramUrl")
                .addModifiers(KModifier.PRIVATE)
                .returns(String::class)
                .addStatement("return telegramUrl.let { \"\${it.schema}://\${it.host}:\${it.port}\" + if (it.isTestServer) \"/test\" else \"\" }")
                .build()
        )
        classBuilder.addFunction(
            FunSpec.builder("getBotFileUrl")
                .addModifiers(KModifier.PRIVATE)
                .returns(String::class)
                .addStatement("return \"\${getTelegramUrl()}/file/bot\$token/\"")
                .build()
        )
        classBuilder.addFunction(
            FunSpec.builder("getBotUrl")
                .addModifiers(KModifier.PRIVATE)
                .returns(String::class)
                .addStatement("return \"\${getTelegramUrl()}/bot\$token/\"")
                .build()
        )

        fileSpecBuilder.addType(classBuilder.build())
        fileSpecBuilder.build().writeTo(outputDir.get().asFile)
    }

    private fun Method.getSignature(): String {
        return this.name + this.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.canonicalName }
    }

    private fun MethodDeclaration.getRefSignature(symbolSolver: SymbolResolver? = null): String {
        return this.nameAsString + this.parameters.mapNotNull { tryResolve(it.type, symbolSolver)?.describe() } .joinToString(prefix = "(", postfix = ")")
    }

    private fun generateCodeForNode(node: Statement, funSpec: FunSpec.Builder, paramName: String, paramClassDecl: ClassOrInterfaceDeclaration, inNonNullContext: Boolean, originalParamNameToReplace: String?) {
        val ignoredExpressions = setOf("buildUrl", "TelegramMultipartBuilder", "Request.Builder", "sendRequest", "assertParamNotNull")

        if (node is ExpressionStmt) {
            val exprStr = node.expression.toString()
            if (ignoredExpressions.any { exprStr.contains(it) }) {
                return
            }
            if (exprStr.contains("consumer.accept(builder)")) {
                return
            }
        }

        when (node) {
            is TryStmt -> {
                node.tryBlock.statements.forEach { generateCodeForNode(it, funSpec, paramName, paramClassDecl, inNonNullContext, originalParamNameToReplace) }
            }
            is IfStmt -> {
                val condition = resolveExpr(node.condition, paramName, paramClassDecl, originalParamNameToReplace)
                funSpec.beginControlFlow("if (%L)", condition)
                val thenStmt = node.thenStmt
                if (thenStmt.isBlockStmt) {
                    thenStmt.asBlockStmt().statements.forEach { child -> generateCodeForNode(child, funSpec, paramName, paramClassDecl, true, originalParamNameToReplace) }
                } else {
                    generateCodeForNode(thenStmt, funSpec, paramName, paramClassDecl, true, originalParamNameToReplace)
                }
                node.elseStmt.ifPresent { elseStmt ->
                    funSpec.nextControlFlow("else")
                    if (elseStmt.isBlockStmt) {
                        elseStmt.asBlockStmt().statements.forEach { child -> generateCodeForNode(child, funSpec, paramName, paramClassDecl, true, originalParamNameToReplace) }
                    } else {
                        generateCodeForNode(elseStmt, funSpec, paramName, paramClassDecl, true, originalParamNameToReplace)
                    }
                }
                funSpec.endControlFlow()
            }
            is ExpressionStmt -> {
                val expr = node.expression
                if (expr is VariableDeclarationExpr) {
                    for (v in expr.variables) {
                        v.initializer.ifPresent { initializer ->
                            val resolvedInitializer = resolveExpr(initializer, paramName, paramClassDecl, originalParamNameToReplace)
                            funSpec.addStatement("val %L = %L", v.nameAsString, resolvedInitializer)
                        }
                    }
                } else if (isBuilderCall(expr)) {
                    unrollAndHandleBuilderCalls(expr as MethodCallExpr, funSpec, paramName, paramClassDecl, inNonNullContext, originalParamNameToReplace)
                }
            }
        }
    }

    private fun isBuilderCall(expr: Expression): Boolean {
        var current = expr
        while (current is MethodCallExpr) {
            val scope = current.scope.orElse(null) ?: return false
            current = scope
        }
        return current.toString() == "builder"
    }

    private fun unrollAndHandleBuilderCalls(call: MethodCallExpr, funSpec: FunSpec.Builder, paramName: String, paramClassDecl: ClassOrInterfaceDeclaration, inNonNullContext: Boolean, originalParamNameToReplace: String?) {
        val callChain = mutableListOf<MethodCallExpr>()
        var current: Expression = call
        while (current is MethodCallExpr) {
            callChain.add(current)
            current = current.scope.orElse(null) ?: break
        }
        callChain.reverse()

        for (c in callChain) {
            handleBuilderMethodCall(c, funSpec, paramName, paramClassDecl, inNonNullContext, originalParamNameToReplace)
        }
    }

    private fun handleBuilderMethodCall(call: MethodCallExpr, funSpec: FunSpec.Builder, paramName: String, paramClassDecl: ClassOrInterfaceDeclaration, inNonNullContext: Boolean, originalParamNameToReplace: String?) {
        if (call.arguments.isEmpty()) return

        val fieldNameExpr = call.arguments[0]
        val fieldName = resolveExpr(fieldNameExpr, paramName, paramClassDecl, originalParamNameToReplace)
        
        if (call.arguments.size < 2) return

        val valueExpr = call.arguments[1]
        val valueAccessor = resolveExpr(valueExpr, paramName, paramClassDecl, originalParamNameToReplace)
        
        val propertyName = valueAccessor.toString().substringAfter('.')

        val nonNullFields = getNonNullFields(paramClassDecl)
        val isNonNull = propertyName in nonNullFields || inNonNullContext

        when (call.nameAsString) {
            "addPart", "addJsonPart" -> {
                val partType = if (call.nameAsString == "addPart") "StringPart" else "JsonPart"
                
                val propertyNameFromAccessor = valueAccessor.toString().substringAfterLast('.')
                val fieldInClass = paramClassDecl.fields.firstOrNull { field -> field.variables.any { it.nameAsString == propertyNameFromAccessor } }
                val isString = fieldInClass?.elementType?.isClassOrInterfaceType == true &&
                               fieldInClass.elementType.asClassOrInterfaceType().nameAsString == "String"

                if (isNonNull) {
                    val finalValue = if (isString || partType == "JsonPart") valueAccessor else CodeBlock.of("%L.toString()", valueAccessor)
                    funSpec.addStatement("parts.add(%T(%L, %L))", ClassName(targetPackageName, partType), fieldName, finalValue)
                } else {
                    funSpec.beginControlFlow("%L?.let", valueAccessor)
                    val finalValue = if (isString || partType == "JsonPart") CodeBlock.of("it") else CodeBlock.of("it.toString()")
                    funSpec.addStatement("parts.add(%T(%L, %L))", ClassName(targetPackageName, partType), fieldName, finalValue)
                    funSpec.endControlFlow()
                }
            }
            "addInputFile" -> {
                 if (isNonNull) {
                    funSpec.addStatement("parts.add(%T(%L, %L))", ClassName(targetPackageName, "FilePart"), fieldName, valueAccessor)
                 } else {
                    funSpec.beginControlFlow("%L?.let", valueAccessor)
                    funSpec.addStatement("parts.add(%T(%L, %L))", ClassName(targetPackageName, "FilePart"), fieldName, CodeBlock.of("it"))
                    funSpec.endControlFlow()
                 }
            }
        }
    }

    private fun getNonNullFields(classDecl: ClassOrInterfaceDeclaration): Set<String> {
        return nonNullFieldsCache.getOrPut(classDecl) {
            classDecl.findAll(FieldDeclaration::class.java)
                .filter { field -> field.annotations.any { it.name.identifier == "NonNull" } }
                .map { it.variables.first().nameAsString }
                .toSet()
        }
    }

    private fun resolveExpr(expr: Expression, paramName: String, paramClassDecl: ClassOrInterfaceDeclaration, originalParamNameToReplace: String?): CodeBlock {
        return when (expr) {
            is ObjectCreationExpr -> {
                val type = expr.type.toKotlinTypeName(symbolResolver = symbolResolver)
                val args = expr.arguments.map { resolveExpr(it, paramName, paramClassDecl, originalParamNameToReplace) }
                val format = List(args.size) { "%L" }.joinToString(", ")
                CodeBlock.builder().add("%T(", type).add(format, *args.toTypedArray()).add(")").build()
            }
            is CastExpr -> {
                val castType = expr.type.toKotlinTypeName(symbolResolver = symbolResolver)
                val innerExpr = resolveExpr(expr.expression, paramName, paramClassDecl, originalParamNameToReplace)
                CodeBlock.of("(%L as %T)", innerExpr, castType)
            }
            is MethodCallExpr -> {
                val scope = expr.scope.orElse(null)
                val args = expr.arguments.map { resolveExpr(it, paramName, paramClassDecl, originalParamNameToReplace) }
                val format = List(args.size) { "%L" }.joinToString(", ")
                val argsBlock = CodeBlock.builder().add(format, *args.toTypedArray()).build()

                if (scope != null) {
                    val resolvedScope = resolveExpr(scope, paramName, paramClassDecl, originalParamNameToReplace)
                    val getterName = expr.nameAsString
                    if (getterName.startsWith("get") && args.isEmpty()) {
                        val propertyName = getterName.removePrefix("get").replaceFirstChar { it.lowercase() }
                        CodeBlock.builder().add(resolvedScope).add(".%L", propertyName).build()
                    } else {
                        CodeBlock.builder().add(resolvedScope).add(".%L(%L)", getterName, argsBlock).build()
                    }
                } else {
                     CodeBlock.of("%L(%L)", expr.nameAsString, argsBlock)
                }
            }
            is FieldAccessExpr -> {
                try {
                    val resolved = expr.resolve()
                    if (resolved.isField) {
                        val decl = resolved.asField()
                        val typeName = ClassName.bestGuess(decl.declaringType().qualifiedName)
                        CodeBlock.of("%T.%L", typeName, decl.name)
                    } else {
                        CodeBlock.of(expr.toString().replace("$paramName.", "$paramName."))
                    }
                } catch (e: Exception) {
                    CodeBlock.of(expr.toString().replace("$paramName.", "$paramName."))
                }
            }
            is NameExpr -> {
                val name = expr.nameAsString
                if (originalParamNameToReplace != null && name == originalParamNameToReplace) {
                    CodeBlock.of("%L", paramName)
                } else {
                    CodeBlock.of("%L", name)
                }
            }
            is StringLiteralExpr -> CodeBlock.of("%S", expr.asString())
            else -> CodeBlock.of(expr.toString().replace("$paramName.", "$paramName."))
        }
    }
}
