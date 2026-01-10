package io.github.kochkaev.kotlin.telegrambots.client.okhttp

import io.github.kochkaev.kotlin.telegrambots.core.BotSerializer
import io.github.kochkaev.kotlin.telegrambots.core.FilePart
import io.github.kochkaev.kotlin.telegrambots.core.HttpExecutor
import io.github.kochkaev.kotlin.telegrambots.core.JsonPart
import io.github.kochkaev.kotlin.telegrambots.core.Part
import io.github.kochkaev.kotlin.telegrambots.core.StringPart
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.telegram.telegrambots.meta.api.objects.InputFile
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture

class OkHttpExecutor(
    private val serializer: BotSerializer,
    private val client: OkHttpClient = OkHttpClient(),
) : HttpExecutor {

    override fun executeJson(url: String, json: String): CompletableFuture<String> {
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        return executeRequest(request)
    }

    override fun executeMultipart(url: String, parts: List<Part>): CompletableFuture<String> {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        for (part in parts) {
            when (part) {
                is StringPart -> bodyBuilder.addFormDataPart(part.key, part.value)
                is JsonPart -> bodyBuilder.addFormDataPart(part.key, serializer.serialize(part.value))
                is FilePart -> {
                    val inputFile = part.value as InputFile
                    if (inputFile.isNew) {
                        val requestBody = when {
                            inputFile.newMediaFile != null -> inputFile.newMediaFile.asRequestBody("application/octet-stream".toMediaType())
                            inputFile.newMediaStream != null -> inputFile.newMediaStream.readBytes().toRequestBody("application/octet-stream".toMediaType())
                            else -> error("New InputFile must have a file or a stream")
                        }
                        bodyBuilder.addFormDataPart(part.key, inputFile.mediaName, requestBody)
                    } else {
                        bodyBuilder.addFormDataPart(part.key, inputFile.attachName)
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyBuilder.build())
            .build()
        return executeRequest(request)
    }

    override fun downloadFile(url: String): CompletableFuture<InputStream> {
        val request = Request.Builder().url(url).build()
        val future = CompletableFuture<InputStream>()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    future.complete(response.body?.byteStream())
                } else {
                    future.completeExceptionally(IOException("Failed to download file: ${response.code} ${response.message}"))
                }
            }
        })
        return future
    }

    private fun executeRequest(request: Request): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    future.complete(response.body?.string())
                } else {
                    future.completeExceptionally(IOException("Request failed: ${response.code} ${response.message}"))
                }
            }
        })
        return future
    }
}
