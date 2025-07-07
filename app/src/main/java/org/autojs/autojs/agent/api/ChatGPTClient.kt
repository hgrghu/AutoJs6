package org.autojs.autojs.agent.api

import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import org.autojs.autojs.agent.model.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap

/**
 * ChatGPT客户端
 * 负责与OpenAI API进行交互，提供脚本分析、生成和优化功能
 */
class ChatGPTClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4"
) : AgentAPI {
    
    private val maxRetries = 3
    private val timeoutMs = 30000
    
    override suspend fun analyzeScript(script: String, context: ScreenContext): OptimizationResult {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildScriptAnalysisPrompt(script, context)
                val response = callChatGPT(prompt, includeVision = context.screenShot != null)
                parseOptimizationResult(response, script)
            } catch (e: Exception) {
                OptimizationResult(
                    originalScript = script,
                    optimizedScript = script,
                    isSuccessful = false,
                    warnings = listOf("AI分析失败: ${e.message}")
                )
            }
        }
    }
    
    override suspend fun generateScript(request: String, context: ScreenContext): ScriptGenerationResult {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildScriptGenerationPrompt(request, context)
                val response = callChatGPT(prompt, includeVision = context.screenShot != null)
                parseScriptGenerationResult(response)
            } catch (e: Exception) {
                ScriptGenerationResult(
                    script = "// 脚本生成失败: ${e.message}",
                    explanation = "无法生成脚本",
                    confidence = 0.0f,
                    isExecutable = false
                )
            }
        }
    }
    
    override suspend fun optimizeCoordinates(elements: List<UIElement>): CoordinateOptimization {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildCoordinateOptimizationPrompt(elements)
                val response = callChatGPT(prompt)
                parseCoordinateOptimization(response)
            } catch (e: Exception) {
                CoordinateOptimization()
            }
        }
    }
    
    override suspend fun chatWithAgent(message: String, history: List<ChatMessage>): ChatResponse {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildChatPrompt(message, history)
                val response = callChatGPT(prompt)
                parseChatResponse(response, message)
            } catch (e: Exception) {
                ChatResponse(
                    message = ChatMessage(
                        content = "抱歉，我遇到了一些问题，请稍后再试。错误: ${e.message}",
                        role = ChatMessage.MessageRole.ASSISTANT
                    )
                )
            }
        }
    }
    
    override suspend fun analyzeScreenRealtime(screenData: ScreenData): ActionSuggestion {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildRealtimeAnalysisPrompt(screenData)
                val response = callChatGPT(prompt, includeVision = screenData.context.screenShot != null)
                parseActionSuggestion(response)
            } catch (e: Exception) {
                ActionSuggestion(
                    reasoning = "实时分析失败: ${e.message}",
                    confidence = 0.0f
                )
            }
        }
    }
    
    override suspend fun validateScript(script: String, context: ScreenContext): ValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildValidationPrompt(script, context)
                val response = callChatGPT(prompt)
                parseValidationResult(response)
            } catch (e: Exception) {
                ValidationResult(
                    isValid = false,
                    errors = listOf(ValidationError(message = "验证失败: ${e.message}"))
                )
            }
        }
    }
    
    override suspend fun getSuggestions(script: String, executionResult: ExecutionResult?): List<Suggestion> {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildSuggestionPrompt(script, executionResult)
                val response = callChatGPT(prompt)
                parseSuggestions(response)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * 调用ChatGPT API
     */
    private suspend fun callChatGPT(
        prompt: String, 
        includeVision: Boolean = false,
        systemPrompt: String = getSystemPrompt()
    ): String {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val url = URL("$baseUrl/chat/completions")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.doOutput = true
                
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        if (includeVision) {
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", prompt)
                                })
                                // 如果有截图，添加图像
                                // 这里需要根据实际需要添加图像处理逻辑
                            })
                        } else {
                            put("content", prompt)
                        }
                    })
                }
                
                val requestBody = JSONObject().apply {
                    put("model", if (includeVision) "gpt-4-vision-preview" else model)
                    put("messages", messages)
                    put("max_tokens", 2000)
                    put("temperature", 0.3)
                }
                
                // 发送请求
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody.toString())
                    writer.flush()
                }
                
                // 读取响应
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        val response = reader.readText()
                        val jsonResponse = JSONObject(response)
                        return jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                    }
                } else {
                    val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                    val errorResponse = errorReader.readText()
                    throw Exception("API调用失败 (HTTP $responseCode): $errorResponse")
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(1000 * (attempt + 1)) // 指数退避
                }
            }
        }
        
        throw lastException ?: Exception("API调用失败")
    }
    
    /**
     * 构建脚本分析提示
     */
    private fun buildScriptAnalysisPrompt(script: String, context: ScreenContext): String {
        return """
            请分析以下AutoJs脚本并提供优化建议：
            
            脚本内容：
            ```javascript
            $script
            ```
            
            当前屏幕信息：
            - 应用包名: ${context.appPackage}
            - Activity: ${context.activity}
            - 可点击元素数量: ${context.elements.count { it.isClickable }}
            - 文本元素数量: ${context.elements.count { !it.text.isNullOrBlank() }}
            
            请提供以下内容：
            1. 优化后的脚本代码
            2. 改进说明和理由
            3. 可能的问题和警告
            4. 优化评分(0-100)
            5. 进一步的建议
            
            请以JSON格式回复，包含字段：optimized_script, improvements, warnings, score, suggestions
        """.trimIndent()
    }
    
    /**
     * 构建脚本生成提示
     */
    private fun buildScriptGenerationPrompt(request: String, context: ScreenContext): String {
        val elementsInfo = context.elements.take(10).joinToString("\n") { element ->
            "- ${element.className} '${element.text}' at ${element.bounds} ${if (element.isClickable) "(可点击)" else ""}"
        }
        
        return """
            基于以下用户需求和当前屏幕信息，生成AutoJs脚本：
            
            用户需求：$request
            
            当前屏幕元素：
            $elementsInfo
            
            请生成一个完整的AutoJs脚本，要求：
            1. 使用选择器而不是硬编码坐标
            2. 包含适当的等待和错误处理
            3. 添加注释说明每个步骤
            4. 确保脚本的健壮性
            
            请以JSON格式回复，包含字段：script, explanation, confidence, required_permissions, dependencies
        """.trimIndent()
    }
    
    /**
     * 构建坐标优化提示
     */
    private fun buildCoordinateOptimizationPrompt(elements: List<UIElement>): String {
        val elementsInfo = elements.joinToString("\n") { element ->
            "ID: ${element.id}, Text: '${element.text}', Class: ${element.className}, Bounds: ${element.bounds}"
        }
        
        return """
            请为以下UI元素推荐最佳的选择器策略，避免使用硬编码坐标：
            
            $elementsInfo
            
            对于每个元素，请提供：
            1. 推荐的选择器语法
            2. 选择器类型（ID、文本、类名等）
            3. 可靠性评分
            4. 备选方案
            
            请以JSON格式回复。
        """.trimIndent()
    }
    
    /**
     * 构建聊天提示
     */
    private fun buildChatPrompt(message: String, history: List<ChatMessage>): String {
        val historyText = history.takeLast(5).joinToString("\n") { msg ->
            "${msg.role}: ${msg.content}"
        }
        
        return """
            对话历史：
            $historyText
            
            用户消息：$message
            
            请作为AutoJs脚本开发助手回复用户。如果用户询问脚本相关问题，请提供具体的代码示例和说明。
        """.trimIndent()
    }
    
    /**
     * 构建实时分析提示
     */
    private fun buildRealtimeAnalysisPrompt(screenData: ScreenData): String {
        val changesInfo = screenData.changes.joinToString("\n") { change ->
            "${change.type}: ${change.element?.text ?: change.element?.className}"
        }
        
        return """
            当前屏幕发生了以下变化：
            $changesInfo
            
            基于这些变化，请分析用户可能想要执行的操作，并提供建议。
            
            请以JSON格式回复，包含：actions, confidence, reasoning
        """.trimIndent()
    }
    
    /**
     * 构建验证提示
     */
    private fun buildValidationPrompt(script: String, context: ScreenContext): String {
        return """
            请验证以下AutoJs脚本的正确性：
            
            ```javascript
            $script
            ```
            
            检查项目：
            1. 语法错误
            2. 逻辑错误
            3. 性能问题
            4. 最佳实践
            5. 安全性
            
            请以JSON格式回复验证结果。
        """.trimIndent()
    }
    
    /**
     * 构建建议提示
     */
    private fun buildSuggestionPrompt(script: String, executionResult: ExecutionResult?): String {
        val resultInfo = executionResult?.let { result ->
            """
            执行结果：
            - 成功: ${result.isSuccess}
            - 执行时间: ${result.executionTime}ms
            - 错误: ${result.error?.message}
            """
        } ?: ""
        
        return """
            请为以下脚本提供改进建议：
            
            ```javascript
            $script
            ```
            
            $resultInfo
            
            请提供具体的改进建议，包括优化、错误修复、功能增强等。
        """.trimIndent()
    }
    
    /**
     * 获取系统提示
     */
    private fun getSystemPrompt(): String {
        return """
            你是AutoJs6的专业脚本开发助手。AutoJs6是基于Android无障碍服务的JavaScript自动化工具。
            
            你的职责：
            1. 分析和优化AutoJs脚本
            2. 根据需求生成脚本代码
            3. 提供脚本开发建议
            4. 帮助解决技术问题
            
            重要准则：
            - 优先使用选择器而非硬编码坐标
            - 添加适当的等待和错误处理
            - 确保脚本的健壮性和可维护性
            - 遵循AutoJs6的API规范
            - 提供清晰的代码注释
            
            请始终以JSON格式返回结构化的回复。
        """.trimIndent()
    }
    
    // 解析方法
    private fun parseOptimizationResult(response: String, originalScript: String): OptimizationResult {
        return try {
            val json = JSONObject(response)
            OptimizationResult(
                originalScript = originalScript,
                optimizedScript = json.optString("optimized_script", originalScript),
                score = json.optDouble("score", 0.0).toFloat(),
                warnings = parseJsonArray(json.optJSONArray("warnings")),
                isSuccessful = true
            )
        } catch (e: Exception) {
            OptimizationResult(
                originalScript = originalScript,
                optimizedScript = originalScript,
                isSuccessful = false,
                warnings = listOf("解析AI响应失败")
            )
        }
    }
    
    private fun parseScriptGenerationResult(response: String): ScriptGenerationResult {
        return try {
            val json = JSONObject(response)
            ScriptGenerationResult(
                script = json.optString("script", ""),
                explanation = json.optString("explanation", ""),
                confidence = json.optDouble("confidence", 0.0).toFloat(),
                requiredPermissions = parseJsonArray(json.optJSONArray("required_permissions")),
                dependencies = parseJsonArray(json.optJSONArray("dependencies"))
            )
        } catch (e: Exception) {
            ScriptGenerationResult(
                script = response,
                explanation = "AI生成的脚本",
                confidence = 0.5f
            )
        }
    }
    
    private fun parseCoordinateOptimization(response: String): CoordinateOptimization {
        // 实现坐标优化结果解析
        return CoordinateOptimization()
    }
    
    private fun parseChatResponse(response: String, userMessage: String): ChatResponse {
        return ChatResponse(
            message = ChatMessage(
                content = response,
                role = ChatMessage.MessageRole.ASSISTANT
            )
        )
    }
    
    private fun parseActionSuggestion(response: String): ActionSuggestion {
        return ActionSuggestion(
            reasoning = response,
            confidence = 0.7f
        )
    }
    
    private fun parseValidationResult(response: String): ValidationResult {
        return ValidationResult(isValid = true)
    }
    
    private fun parseSuggestions(response: String): List<Suggestion> {
        return emptyList()
    }
    
    private fun parseJsonArray(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            result.add(jsonArray.optString(i))
        }
        return result
    }
}