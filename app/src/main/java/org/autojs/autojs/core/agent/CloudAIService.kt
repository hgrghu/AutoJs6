package org.autojs.autojs.core.agent

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.autojs.autojs.core.agent.models.AIModelConfig
import org.autojs.autojs.core.agent.models.ModelProvider
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 云端AI服务 - 支持多种在线AI模型调用
 * 通过URL、API Key、模型名称进行统一调用
 */
class CloudAIService(private val context: Context) {

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 调用云端AI模型
     */
    suspend fun callModel(
        modelConfig: AIModelConfig,
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int? = null,
        temperature: Float? = null
    ): CloudAIResponse = withContext(Dispatchers.IO) {
        
        when (modelConfig.provider) {
            ModelProvider.OPENAI -> callOpenAI(modelConfig, prompt, systemPrompt, maxTokens, temperature)
            ModelProvider.ANTHROPIC -> callAnthropic(modelConfig, prompt, systemPrompt, maxTokens, temperature)
            ModelProvider.GOOGLE -> callGoogle(modelConfig, prompt, systemPrompt, maxTokens, temperature)
            ModelProvider.CUSTOM -> callCustomModel(modelConfig, prompt, systemPrompt, maxTokens, temperature)
        }
    }

    /**
     * 测试模型连接
     */
    suspend fun testConnection(modelConfig: AIModelConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val testResponse = callModel(
                modelConfig = modelConfig,
                prompt = "Hello! Please respond with 'OK' to confirm connection.",
                maxTokens = 10
            )
            testResponse.isSuccess && testResponse.content.contains("OK", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * OpenAI API调用
     */
    private suspend fun callOpenAI(
        config: AIModelConfig,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int?,
        temperature: Float?
    ): CloudAIResponse {
        val messages = mutableListOf<JsonObject>()
        
        // 添加系统提示
        systemPrompt?.let {
            messages.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", it)
            })
        }
        
        // 添加用户消息
        messages.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", prompt)
        })

        val requestBody = JsonObject().apply {
            addProperty("model", config.modelName)
            add("messages", gson.toJsonTree(messages))
            maxTokens?.let { addProperty("max_tokens", it) }
            temperature?.let { addProperty("temperature", it) }
        }

        val request = Request.Builder()
            .url("${config.baseUrl}/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(request)
    }

    /**
     * Anthropic Claude API调用
     */
    private suspend fun callAnthropic(
        config: AIModelConfig,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int?,
        temperature: Float?
    ): CloudAIResponse {
        val requestBody = JsonObject().apply {
            addProperty("model", config.modelName)
            addProperty("max_tokens", maxTokens ?: 1000)
            temperature?.let { addProperty("temperature", it) }
            
            // Anthropic使用不同的消息格式
            val messages = mutableListOf<JsonObject>()
            messages.add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", prompt)
            })
            add("messages", gson.toJsonTree(messages))
            
            systemPrompt?.let { addProperty("system", it) }
        }

        val request = Request.Builder()
            .url("${config.baseUrl}/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("anthropic-version", "2023-06-01")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(request, isAnthropic = true)
    }

    /**
     * Google Gemini API调用
     */
    private suspend fun callGoogle(
        config: AIModelConfig,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int?,
        temperature: Float?
    ): CloudAIResponse {
        val parts = mutableListOf<JsonObject>()
        
        // Google Gemini格式
        systemPrompt?.let {
            parts.add(JsonObject().apply {
                addProperty("text", "System: $it")
            })
        }
        
        parts.add(JsonObject().apply {
            addProperty("text", prompt)
        })

        val requestBody = JsonObject().apply {
            val contents = JsonObject().apply {
                add("parts", gson.toJsonTree(parts))
            }
            add("contents", gson.toJsonTree(listOf(contents)))
            
            val generationConfig = JsonObject().apply {
                maxTokens?.let { addProperty("maxOutputTokens", it) }
                temperature?.let { addProperty("temperature", it) }
            }
            add("generationConfig", generationConfig)
        }

        val url = "${config.baseUrl}/v1/models/${config.modelName}:generateContent?key=${config.apiKey}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(request, isGoogle = true)
    }

    /**
     * 自定义模型API调用
     */
    private suspend fun callCustomModel(
        config: AIModelConfig,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int?,
        temperature: Float?
    ): CloudAIResponse {
        // 使用通用的OpenAI兼容格式作为默认
        val messages = mutableListOf<JsonObject>()
        
        systemPrompt?.let {
            messages.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", it)
            })
        }
        
        messages.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", prompt)
        })

        val requestBody = JsonObject().apply {
            addProperty("model", config.modelName)
            add("messages", gson.toJsonTree(messages))
            maxTokens?.let { addProperty("max_tokens", it) }
            temperature?.let { addProperty("temperature", it) }
        }

        val request = Request.Builder()
            .url("${config.baseUrl}/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(request)
    }

    /**
     * 执行HTTP请求并解析响应
     */
    private suspend fun executeRequest(
        request: Request,
        isAnthropic: Boolean = false,
        isGoogle: Boolean = false
    ): CloudAIResponse {
        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                parseSuccessResponse(responseBody, isAnthropic, isGoogle)
            } else {
                CloudAIResponse(
                    isSuccess = false,
                    content = "",
                    error = "HTTP ${response.code}: $responseBody",
                    usage = null
                )
            }
        } catch (e: IOException) {
            CloudAIResponse(
                isSuccess = false,
                content = "",
                error = "Network error: ${e.message}",
                usage = null
            )
        } catch (e: Exception) {
            CloudAIResponse(
                isSuccess = false,
                content = "",
                error = "Unexpected error: ${e.message}",
                usage = null
            )
        }
    }

    /**
     * 解析成功的响应
     */
    private fun parseSuccessResponse(
        responseBody: String,
        isAnthropic: Boolean,
        isGoogle: Boolean
    ): CloudAIResponse {
        return try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            
            when {
                isAnthropic -> parseAnthropicResponse(jsonResponse)
                isGoogle -> parseGoogleResponse(jsonResponse)
                else -> parseOpenAIResponse(jsonResponse)
            }
        } catch (e: Exception) {
            CloudAIResponse(
                isSuccess = false,
                content = "",
                error = "Failed to parse response: ${e.message}",
                usage = null
            )
        }
    }

    /**
     * 解析OpenAI格式响应
     */
    private fun parseOpenAIResponse(jsonResponse: JsonObject): CloudAIResponse {
        val choices = jsonResponse.getAsJsonArray("choices")
        val content = if (choices.size() > 0) {
            val choice = choices[0].asJsonObject
            choice.getAsJsonObject("message")?.get("content")?.asString ?: ""
        } else {
            ""
        }

        val usage = jsonResponse.getAsJsonObject("usage")?.let { usageObj ->
            TokenUsage(
                promptTokens = usageObj.get("prompt_tokens")?.asInt ?: 0,
                completionTokens = usageObj.get("completion_tokens")?.asInt ?: 0,
                totalTokens = usageObj.get("total_tokens")?.asInt ?: 0
            )
        }

        return CloudAIResponse(
            isSuccess = true,
            content = content,
            error = null,
            usage = usage
        )
    }

    /**
     * 解析Anthropic格式响应
     */
    private fun parseAnthropicResponse(jsonResponse: JsonObject): CloudAIResponse {
        val content = jsonResponse.getAsJsonArray("content")?.let { contentArray ->
            if (contentArray.size() > 0) {
                contentArray[0].asJsonObject.get("text")?.asString ?: ""
            } else ""
        } ?: ""

        val usage = jsonResponse.getAsJsonObject("usage")?.let { usageObj ->
            TokenUsage(
                promptTokens = usageObj.get("input_tokens")?.asInt ?: 0,
                completionTokens = usageObj.get("output_tokens")?.asInt ?: 0,
                totalTokens = (usageObj.get("input_tokens")?.asInt ?: 0) + (usageObj.get("output_tokens")?.asInt ?: 0)
            )
        }

        return CloudAIResponse(
            isSuccess = true,
            content = content,
            error = null,
            usage = usage
        )
    }

    /**
     * 解析Google格式响应
     */
    private fun parseGoogleResponse(jsonResponse: JsonObject): CloudAIResponse {
        val candidates = jsonResponse.getAsJsonArray("candidates")
        val content = if (candidates.size() > 0) {
            val candidate = candidates[0].asJsonObject
            val contentObj = candidate.getAsJsonObject("content")
            val parts = contentObj.getAsJsonArray("parts")
            if (parts.size() > 0) {
                parts[0].asJsonObject.get("text")?.asString ?: ""
            } else ""
        } else ""

        val usage = jsonResponse.getAsJsonObject("usageMetadata")?.let { usageObj ->
            TokenUsage(
                promptTokens = usageObj.get("promptTokenCount")?.asInt ?: 0,
                completionTokens = usageObj.get("candidatesTokenCount")?.asInt ?: 0,
                totalTokens = usageObj.get("totalTokenCount")?.asInt ?: 0
            )
        }

        return CloudAIResponse(
            isSuccess = true,
            content = content,
            error = null,
            usage = usage
        )
    }
}

/**
 * 云端AI响应
 */
data class CloudAIResponse(
    val isSuccess: Boolean,
    val content: String,
    val error: String? = null,
    val usage: TokenUsage? = null
)

/**
 * Token使用情况
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)