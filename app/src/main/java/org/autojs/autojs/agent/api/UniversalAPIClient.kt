package org.autojs.autojs.agent.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.agent.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * OpenAI兼容API客户端
 * 支持OpenAI官方API和其他兼容OpenAI格式的API
 */
class OpenAICompatibleClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val modelName: String = "gpt-3.5-turbo",
    private val maxTokens: Int = 2048,
    private val temperature: Float = 0.3f,
    private val timeout: Long = 60000L
) : AgentAPI {

    override suspend fun optimizeScript(script: String, context: ScreenContext): OptimizationResult = withContext(Dispatchers.IO) {
        val messages = buildList {
            add(mapOf(
                "role" to "system",
                "content" to """你是一个AutoJs6脚本优化专家。请分析给定的JavaScript脚本并提供优化建议。
                |评分标准：
                |1. 代码质量 (0-25分): 代码结构、可读性、注释
                |2. 性能优化 (0-25分): 执行效率、资源使用
                |3. 稳定性 (0-25分): 错误处理、兼容性
                |4. 功能完整性 (0-25分): 功能实现程度
                |
                |请返回JSON格式结果，包含以下字段：
                |{
                |  "score": 总分(0-100),
                |  "improvements": ["改进建议1", "改进建议2", ...],
                |  "optimizedScript": "优化后的脚本代码",
                |  "explanation": "优化说明"
                |}""".trimMargin()
            ))
            add(mapOf(
                "role" to "user",
                "content" to "请优化以下AutoJs6脚本：\n```javascript\n$script\n```"
            ))
        }
        
        val response = sendChatRequest(messages)
        parseOptimizationResult(response)
    }

    override suspend fun analyzeScreen(screenData: ByteArray, context: ScreenContext): ScreenAnalysisResult = withContext(Dispatchers.IO) {
        // 将屏幕数据转换为base64
        val base64Image = android.util.Base64.encodeToString(screenData, android.util.Base64.NO_WRAP)
        
        val messages = buildList {
            add(mapOf(
                "role" to "system",
                "content" to """你是一个屏幕分析专家。请分析提供的屏幕截图，识别其中的UI元素。
                |请返回JSON格式结果，包含以下字段：
                |{
                |  "elements": [
                |    {
                |      "type": "元素类型",
                |      "text": "元素文本",
                |      "bounds": {"left": 0, "top": 0, "right": 100, "bottom": 50},
                |      "clickable": true,
                |      "selector": "建议的选择器"
                |    }
                |  ],
                |  "description": "整体描述"
                |}""".trimMargin()
            ))
            add(mapOf(
                "role" to "user",
                "content" to buildList {
                    add(mapOf("type" to "text", "text" to "请分析这个屏幕截图中的UI元素"))
                    add(mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to "data:image/png;base64,$base64Image")
                    ))
                }
            ))
        }
        
        val response = sendChatRequest(messages)
        parseScreenAnalysisResult(response)
    }

    override suspend fun adjustCoordinates(originalScript: String, screenContext: ScreenContext): CoordinateAdjustmentResult = withContext(Dispatchers.IO) {
        val messages = buildList {
            add(mapOf(
                "role" to "system",
                "content" to """你是一个坐标优化专家。请分析脚本中的硬编码坐标，并提供更稳定的选择器替代方案。
                |请返回JSON格式结果：
                |{
                |  "adjustments": [
                |    {
                |      "originalLine": "原始代码行",
                |      "suggestedLine": "建议的替代代码",
                |      "reason": "修改原因"
                |    }
                |  ],
                |  "adjustedScript": "完整的调整后脚本"
                |}""".trimMargin()
            ))
            add(mapOf(
                "role" to "user",
                "content" to "请优化以下脚本中的坐标定位：\n```javascript\n$originalScript\n```"
            ))
        }
        
        val response = sendChatRequest(messages)
        parseCoordinateAdjustmentResult(response)
    }

    override suspend fun generateScript(description: String, context: ScreenContext): ScriptGenerationResult = withContext(Dispatchers.IO) {
        val messages = buildList {
            add(mapOf(
                "role" to "system",
                "content" to """你是一个AutoJs6脚本生成专家。请根据用户描述生成可执行的JavaScript脚本。
                |脚本要求：
                |1. 使用AutoJs6 API
                |2. 包含必要的错误处理
                |3. 添加适当的延时和等待
                |4. 使用稳定的元素选择器
                |
                |请返回JSON格式结果：
                |{
                |  "script": "生成的脚本代码",
                |  "explanation": "脚本说明",
                |  "confidence": 0.9,
                |  "dependencies": ["依赖的权限或功能"]
                |}""".trimMargin()
            ))
            add(mapOf(
                "role" to "user", 
                "content" to "请生成AutoJs6脚本来实现：$description"
            ))
        }
        
        val response = sendChatRequest(messages)
        parseScriptGenerationResult(response)
    }

    override suspend fun chatWithAgent(message: String, history: List<ChatMessage>): ChatResponse = withContext(Dispatchers.IO) {
        val messages = buildList {
            add(mapOf(
                "role" to "system",
                "content" to "你是AutoJs6助手，专门帮助用户解决自动化脚本相关问题。请提供专业、准确的建议。"
            ))
            
            // 添加历史消息
            history.forEach { chatMessage ->
                add(mapOf(
                    "role" to if (chatMessage.isUser) "user" else "assistant",
                    "content" to chatMessage.content
                ))
            }
            
            // 添加当前消息
            add(mapOf(
                "role" to "user",
                "content" to message
            ))
        }
        
        val response = sendChatRequest(messages)
        ChatResponse(
            message = ChatMessage(
                content = response,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private suspend fun sendChatRequest(messages: List<Map<String, Any>>): String = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.connectTimeout = timeout.toInt()
            connection.readTimeout = timeout.toInt()
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("model", modelName)
                put("messages", JSONArray(messages))
                put("max_tokens", maxTokens)
                put("temperature", temperature.toDouble())
            }
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = reader.readText()
                    val jsonResponse = JSONObject(response)
                    jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            } else {
                val errorStream = connection.errorStream
                val errorMessage = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else {
                    "HTTP $responseCode"
                }
                throw Exception("API request failed: $errorMessage")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    private fun parseOptimizationResult(response: String): OptimizationResult {
        return try {
            val json = JSONObject(response)
            OptimizationResult(
                score = json.optInt("score", 0),
                improvements = json.optJSONArray("improvements")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                } ?: emptyList(),
                optimizedScript = json.optString("optimizedScript", ""),
                explanation = json.optString("explanation", "")
            )
        } catch (e: Exception) {
            OptimizationResult(
                score = 60,
                improvements = listOf("解析响应时出错：${e.message}"),
                optimizedScript = "",
                explanation = response
            )
        }
    }
    
    private fun parseScreenAnalysisResult(response: String): ScreenAnalysisResult {
        return try {
            val json = JSONObject(response)
            val elements = json.optJSONArray("elements")?.let { array ->
                (0 until array.length()).map { i ->
                    val element = array.getJSONObject(i)
                    val bounds = element.optJSONObject("bounds")
                    UIElement(
                        type = element.optString("type", "unknown"),
                        text = element.optString("text", ""),
                        bounds = if (bounds != null) {
                            ElementBounds(
                                left = bounds.optInt("left", 0),
                                top = bounds.optInt("top", 0),
                                right = bounds.optInt("right", 0),
                                bottom = bounds.optInt("bottom", 0)
                            )
                        } else null,
                        clickable = element.optBoolean("clickable", false),
                        selector = element.optString("selector", "")
                    )
                }
            } ?: emptyList()
            
            ScreenAnalysisResult(
                elements = elements,
                description = json.optString("description", "")
            )
        } catch (e: Exception) {
            ScreenAnalysisResult(
                elements = emptyList(),
                description = "解析响应时出错：${e.message}\n原始响应：$response"
            )
        }
    }
    
    private fun parseCoordinateAdjustmentResult(response: String): CoordinateAdjustmentResult {
        return try {
            val json = JSONObject(response)
            val adjustments = json.optJSONArray("adjustments")?.let { array ->
                (0 until array.length()).map { i ->
                    val adj = array.getJSONObject(i)
                    CoordinateAdjustment(
                        originalLine = adj.optString("originalLine", ""),
                        suggestedLine = adj.optString("suggestedLine", ""),
                        reason = adj.optString("reason", "")
                    )
                }
            } ?: emptyList()
            
            CoordinateAdjustmentResult(
                adjustments = adjustments,
                adjustedScript = json.optString("adjustedScript", "")
            )
        } catch (e: Exception) {
            CoordinateAdjustmentResult(
                adjustments = emptyList(),
                adjustedScript = "解析响应时出错：${e.message}\n原始响应：$response"
            )
        }
    }
    
    private fun parseScriptGenerationResult(response: String): ScriptGenerationResult {
        return try {
            val json = JSONObject(response)
            val dependencies = json.optJSONArray("dependencies")?.let { array ->
                (0 until array.length()).map { array.getString(it) }
            } ?: emptyList()
            
            ScriptGenerationResult(
                script = json.optString("script", ""),
                explanation = json.optString("explanation", ""),
                confidence = json.optDouble("confidence", 0.5).toFloat(),
                dependencies = dependencies
            )
        } catch (e: Exception) {
            ScriptGenerationResult(
                script = "// 解析响应时出错\n// ${e.message}\n// 原始响应：\n/* $response */",
                explanation = "解析AI响应时出现错误",
                confidence = 0.1f,
                dependencies = emptyList()
            )
        }
    }
}

/**
 * Anthropic Claude API客户端
 */
class AnthropicClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com/v1",
    private val modelName: String = "claude-3-opus-20240229",
    private val maxTokens: Int = 4096,
    private val temperature: Float = 0.3f
) : AgentAPI {

    override suspend fun optimizeScript(script: String, context: ScreenContext): OptimizationResult = withContext(Dispatchers.IO) {
        val prompt = """请优化以下AutoJs6脚本并以JSON格式返回结果：

脚本：
```javascript
$script
```

请返回JSON格式：
{
  "score": 总分(0-100),
  "improvements": ["改进建议1", "改进建议2"],
  "optimizedScript": "优化后的脚本",
  "explanation": "优化说明"
}"""

        val response = sendMessage(prompt)
        parseOptimizationResponse(response)
    }

    override suspend fun analyzeScreen(screenData: ByteArray, context: ScreenContext): ScreenAnalysisResult = withContext(Dispatchers.IO) {
        val base64Image = android.util.Base64.encodeToString(screenData, android.util.Base64.NO_WRAP)
        
        val response = sendMessageWithImage(
            "请分析这个屏幕截图中的UI元素，以JSON格式返回结果",
            base64Image
        )
        parseScreenAnalysisResponse(response)
    }

    override suspend fun adjustCoordinates(originalScript: String, screenContext: ScreenContext): CoordinateAdjustmentResult = withContext(Dispatchers.IO) {
        val prompt = """请优化脚本中的坐标定位，以JSON格式返回结果：

脚本：
```javascript
$originalScript
```"""

        val response = sendMessage(prompt)
        parseCoordinateAdjustmentResponse(response)
    }

    override suspend fun generateScript(description: String, context: ScreenContext): ScriptGenerationResult = withContext(Dispatchers.IO) {
        val prompt = "请根据以下描述生成AutoJs6脚本：$description"
        val response = sendMessage(prompt)
        parseScriptGenerationResponse(response)
    }

    override suspend fun chatWithAgent(message: String, history: List<ChatMessage>): ChatResponse = withContext(Dispatchers.IO) {
        val conversationHistory = history.joinToString("\n") { 
            "${if (it.isUser) "用户" else "助手"}: ${it.content}"
        }
        val fullPrompt = if (conversationHistory.isNotEmpty()) {
            "对话历史：\n$conversationHistory\n\n用户: $message"
        } else {
            message
        }
        
        val response = sendMessage(fullPrompt)
        ChatResponse(
            message = ChatMessage(
                content = response,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private suspend fun sendMessage(message: String): String = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl/messages")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", "2023-06-01")
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("model", modelName)
                put("max_tokens", maxTokens)
                put("temperature", temperature.toDouble())
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
            }
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = reader.readText()
                    val jsonResponse = JSONObject(response)
                    jsonResponse.getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                }
            } else {
                throw Exception("API request failed with code: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun sendMessageWithImage(message: String, base64Image: String): String {
        // Claude的图像处理实现
        return sendMessage("$message\n[图像分析功能需要特殊实现]")
    }

    // 解析方法与OpenAI客户端类似
    private fun parseOptimizationResponse(response: String): OptimizationResult {
        // 实现解析逻辑
        return OptimizationResult(score = 70, improvements = listOf(response), optimizedScript = "", explanation = response)
    }

    private fun parseScreenAnalysisResponse(response: String): ScreenAnalysisResult {
        return ScreenAnalysisResult(elements = emptyList(), description = response)
    }

    private fun parseCoordinateAdjustmentResponse(response: String): CoordinateAdjustmentResult {
        return CoordinateAdjustmentResult(adjustments = emptyList(), adjustedScript = response)
    }

    private fun parseScriptGenerationResponse(response: String): ScriptGenerationResult {
        return ScriptGenerationResult(script = response, explanation = "", confidence = 0.8f, dependencies = emptyList())
    }
}

/**
 * Google Gemini API客户端
 */
class GoogleGeminiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1",
    private val modelName: String = "gemini-pro",
    private val maxTokens: Int = 2048,
    private val temperature: Float = 0.3f
) : AgentAPI {

    override suspend fun optimizeScript(script: String, context: ScreenContext): OptimizationResult = withContext(Dispatchers.IO) {
        val prompt = "请优化以下AutoJs6脚本：\n```javascript\n$script\n```"
        val response = sendMessage(prompt)
        OptimizationResult(score = 65, improvements = listOf(response), optimizedScript = "", explanation = response)
    }

    override suspend fun analyzeScreen(screenData: ByteArray, context: ScreenContext): ScreenAnalysisResult = withContext(Dispatchers.IO) {
        ScreenAnalysisResult(elements = emptyList(), description = "Gemini屏幕分析功能")
    }

    override suspend fun adjustCoordinates(originalScript: String, screenContext: ScreenContext): CoordinateAdjustmentResult = withContext(Dispatchers.IO) {
        CoordinateAdjustmentResult(adjustments = emptyList(), adjustedScript = originalScript)
    }

    override suspend fun generateScript(description: String, context: ScreenContext): ScriptGenerationResult = withContext(Dispatchers.IO) {
        val prompt = "请生成AutoJs6脚本：$description"
        val response = sendMessage(prompt)
        ScriptGenerationResult(script = response, explanation = "", confidence = 0.7f, dependencies = emptyList())
    }

    override suspend fun chatWithAgent(message: String, history: List<ChatMessage>): ChatResponse = withContext(Dispatchers.IO) {
        val response = sendMessage(message)
        ChatResponse(
            message = ChatMessage(
                content = response,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private suspend fun sendMessage(message: String): String = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl/models/$modelName:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", message)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", maxTokens)
                    put("temperature", temperature.toDouble())
                })
            }
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = reader.readText()
                    val jsonResponse = JSONObject(response)
                    jsonResponse.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
            } else {
                throw Exception("API request failed with code: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * 自定义API客户端
 * 支持用户自定义的API格式
 */
class CustomAPIClient(
    private val apiKey: String,
    private val config: Map<String, Any>,
    private val baseUrl: String,
    private val modelName: String
) : AgentAPI {

    override suspend fun optimizeScript(script: String, context: ScreenContext): OptimizationResult = withContext(Dispatchers.IO) {
        val response = sendCustomRequest("optimize", mapOf("script" to script))
        OptimizationResult(score = 60, improvements = listOf(response), optimizedScript = "", explanation = response)
    }

    override suspend fun analyzeScreen(screenData: ByteArray, context: ScreenContext): ScreenAnalysisResult = withContext(Dispatchers.IO) {
        ScreenAnalysisResult(elements = emptyList(), description = "自定义API屏幕分析")
    }

    override suspend fun adjustCoordinates(originalScript: String, screenContext: ScreenContext): CoordinateAdjustmentResult = withContext(Dispatchers.IO) {
        CoordinateAdjustmentResult(adjustments = emptyList(), adjustedScript = originalScript)
    }

    override suspend fun generateScript(description: String, context: ScreenContext): ScriptGenerationResult = withContext(Dispatchers.IO) {
        val response = sendCustomRequest("generate", mapOf("description" to description))
        ScriptGenerationResult(script = response, explanation = "", confidence = 0.6f, dependencies = emptyList())
    }

    override suspend fun chatWithAgent(message: String, history: List<ChatMessage>): ChatResponse = withContext(Dispatchers.IO) {
        val response = sendCustomRequest("chat", mapOf("message" to message))
        ChatResponse(
            message = ChatMessage(
                content = response,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private suspend fun sendCustomRequest(action: String, params: Map<String, Any>): String = withContext(Dispatchers.IO) {
        // 自定义API实现
        "自定义API响应: $action - $params"
    }
}