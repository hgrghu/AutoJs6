package org.autojs.autojs.core.agent.cloud

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.log.LogManager
import org.autojs.autojs.core.agent.models.*
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 云端AI服务
 * 集成OpenAI、Claude等云端模型提供强大的AI能力
 */
class CloudAIService {
    
    companion object {
        private const val TAG = "CloudAIService"
        
        // API配置
        private const val OPENAI_BASE_URL = "https://api.openai.com/v1"
        private const val CLAUDE_BASE_URL = "https://api.anthropic.com/v1"
        
        // 模型名称
        private const val GPT_4_VISION = "gpt-4-vision-preview"
        private const val GPT_4_TURBO = "gpt-4-turbo-preview"
        private const val CLAUDE_3_OPUS = "claude-3-opus-20240229"
        
        // 超时时间
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private var openAIApiKey: String? = null
    private var claudeApiKey: String? = null
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private var isInitialized = false
    
    /**
     * 初始化云端服务
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 从配置或SharedPreferences中读取API密钥
            loadApiKeys()
            
            // 测试连接性
            val openAIAvailable = testOpenAIConnection()
            val claudeAvailable = testClaudeConnection()
            
            isInitialized = openAIAvailable || claudeAvailable
            
            LogManager.i(TAG, "云端AI服务初始化完成，OpenAI: $openAIAvailable, Claude: $claudeAvailable")
            
            isInitialized
        } catch (e: Exception) {
            LogManager.e(TAG, "云端AI服务初始化失败", e)
            false
        }
    }
    
    /**
     * 根据自然语言描述生成脚本
     */
    suspend fun generateScriptFromDescription(description: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = buildScriptGenerationPrompt(description)
            
            // 优先使用OpenAI
            if (openAIApiKey != null) {
                return@withContext generateWithOpenAI(prompt)
            }
            
            // 备选方案使用Claude
            if (claudeApiKey != null) {
                return@withContext generateWithClaude(prompt)
            }
            
            throw IllegalStateException("没有可用的云端AI服务")
            
        } catch (e: Exception) {
            LogManager.e(TAG, "脚本生成失败", e)
            generateFallbackScript(description)
        }
    }
    
    /**
     * 分析复杂UI界面
     */
    suspend fun analyzeComplexUI(image: Bitmap): DetailedUIAnalysis = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(image)
            val prompt = buildUIAnalysisPrompt()
            
            val response = if (openAIApiKey != null) {
                analyzeUIWithOpenAI(prompt, base64Image)
            } else if (claudeApiKey != null) {
                analyzeUIWithClaude(prompt, base64Image)
            } else {
                throw IllegalStateException("没有可用的视觉分析服务")
            }
            
            parseUIAnalysisResponse(response)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "UI分析失败", e)
            createFallbackUIAnalysis()
        }
    }
    
    /**
     * 优化脚本代码
     */
    suspend fun optimizeScript(script: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = buildOptimizationPrompt(script)
            
            val optimizedCode = if (openAIApiKey != null) {
                generateWithOpenAI(prompt)
            } else if (claudeApiKey != null) {
                generateWithClaude(prompt)
            } else {
                script // 返回原脚本
            }
            
            optimizedCode
            
        } catch (e: Exception) {
            LogManager.e(TAG, "脚本优化失败", e)
            script
        }
    }
    
    /**
     * 解释错误并提供建议
     */
    suspend fun explainError(error: String, context: String): ErrorExplanation = withContext(Dispatchers.IO) {
        try {
            val prompt = buildErrorExplanationPrompt(error, context)
            
            val explanation = if (openAIApiKey != null) {
                generateWithOpenAI(prompt)
            } else if (claudeApiKey != null) {
                generateWithClaude(prompt)
            } else {
                "无法连接到AI服务"
            }
            
            parseErrorExplanation(explanation)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "错误解释失败", e)
            ErrorExplanation(
                summary = "未知错误",
                possibleCauses = listOf("网络连接问题", "服务不可用"),
                suggestions = listOf("检查网络连接", "重试操作")
            )
        }
    }
    
    /**
     * 获取服务状态
     */
    fun getStatus(): ServiceStatus {
        return ServiceStatus(
            isAvailable = isInitialized,
            isConnected = openAIApiKey != null || claudeApiKey != null,
            lastCheck = System.currentTimeMillis()
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        // 清理HTTP客户端等资源
        LogManager.i(TAG, "云端AI服务资源清理完成")
    }
    
    // 私有方法
    
    private fun loadApiKeys() {
        // 从配置文件或环境变量中加载API密钥
        // 实际实现中应该从安全的存储位置读取
        openAIApiKey = System.getenv("OPENAI_API_KEY")
        claudeApiKey = System.getenv("CLAUDE_API_KEY")
        
        // 如果环境变量没有，尝试从应用配置读取
        if (openAIApiKey == null) {
            openAIApiKey = loadFromConfig("openai_api_key")
        }
        if (claudeApiKey == null) {
            claudeApiKey = loadFromConfig("claude_api_key")
        }
    }
    
    private fun loadFromConfig(key: String): String? {
        // 从应用配置中读取API密钥
        // 实际实现应该使用更安全的存储方式
        return null
    }
    
    private suspend fun testOpenAIConnection(): Boolean {
        return openAIApiKey?.let {
            try {
                val testPrompt = "Hello"
                generateWithOpenAI(testPrompt)
                true
            } catch (e: Exception) {
                LogManager.w(TAG, "OpenAI连接测试失败: ${e.message}")
                false
            }
        } ?: false
    }
    
    private suspend fun testClaudeConnection(): Boolean {
        return claudeApiKey?.let {
            try {
                val testPrompt = "Hello"
                generateWithClaude(testPrompt)
                true
            } catch (e: Exception) {
                LogManager.w(TAG, "Claude连接测试失败: ${e.message}")
                false
            }
        } ?: false
    }
    
    private suspend fun generateWithOpenAI(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", GPT_4_TURBO)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 2000)
            put("temperature", 0.7)
        }
        
        val request = Request.Builder()
            .url("$OPENAI_BASE_URL/chat/completions")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $openAIApiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("OpenAI API调用失败: ${response.code}")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("响应体为空")
        val jsonResponse = JSONObject(responseBody)
        
        return jsonResponse
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
    
    private suspend fun generateWithClaude(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", CLAUDE_3_OPUS)
            put("max_tokens", 2000)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        
        val request = Request.Builder()
            .url("$CLAUDE_BASE_URL/messages")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", claudeApiKey!!)
            .addHeader("Content-Type", "application/json")
            .addHeader("anthropic-version", "2023-06-01")
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Claude API调用失败: ${response.code}")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("响应体为空")
        val jsonResponse = JSONObject(responseBody)
        
        return jsonResponse
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }
    
    private suspend fun analyzeUIWithOpenAI(prompt: String, base64Image: String): String {
        val requestBody = JSONObject().apply {
            put("model", GPT_4_VISION)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                    })
                })
            })
            put("max_tokens", 1000)
        }
        
        val request = Request.Builder()
            .url("$OPENAI_BASE_URL/chat/completions")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $openAIApiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("OpenAI Vision API调用失败: ${response.code}")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("响应体为空")
        val jsonResponse = JSONObject(responseBody)
        
        return jsonResponse
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
    
    private suspend fun analyzeUIWithClaude(prompt: String, base64Image: String): String {
        // Claude的视觉API实现
        // 这里是简化实现，实际需要根据Claude的API文档调整
        return generateWithClaude("$prompt\n[图像分析功能暂未完全实现]")
    }
    
    private fun buildScriptGenerationPrompt(description: String): String {
        return """
        你是一个AutoJs6脚本生成专家。请根据用户描述生成对应的JavaScript代码。
        
        用户描述: $description
        
        请生成标准的AutoJs6脚本，要求：
        1. 包含必要的权限检查
        2. 添加适当的错误处理
        3. 使用合适的等待机制
        4. 添加清晰的中文注释
        5. 遵循AutoJs6的API规范
        
        请直接返回可执行的JavaScript代码，不要包含任何解释文字。
        """.trimIndent()
    }
    
    private fun buildUIAnalysisPrompt(): String {
        return """
        请分析这个Android界面截图，识别出：
        1. 可点击的按钮和元素
        2. 输入框和文本字段
        3. 列表和滚动视图
        4. 重要的文本内容
        5. 界面的整体布局结构
        
        请以JSON格式返回分析结果，包含元素类型、位置、文本内容等信息。
        """.trimIndent()
    }
    
    private fun buildOptimizationPrompt(script: String): String {
        return """
        请优化以下AutoJs6脚本代码：
        
        ```javascript
        $script
        ```
        
        优化要求：
        1. 提升执行效率
        2. 增强错误处理
        3. 改善代码可读性
        4. 添加性能优化
        5. 保持功能完整性
        
        请返回优化后的代码。
        """.trimIndent()
    }
    
    private fun buildErrorExplanationPrompt(error: String, context: String): String {
        return """
        请分析以下AutoJs6脚本执行错误：
        
        错误信息: $error
        执行上下文: $context
        
        请提供：
        1. 错误的可能原因
        2. 解决建议
        3. 预防措施
        
        请用中文回答，格式清晰。
        """.trimIndent()
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }
    
    private fun parseUIAnalysisResponse(response: String): DetailedUIAnalysis {
        // 解析AI返回的UI分析结果
        // 这里是简化实现，实际应该解析JSON结果
        return DetailedUIAnalysis(
            elements = emptyList(),
            description = response,
            suggestions = listOf("基于AI分析的操作建议")
        )
    }
    
    private fun parseErrorExplanation(explanation: String): ErrorExplanation {
        // 解析错误解释
        // 实际实现应该解析结构化的响应
        return ErrorExplanation(
            summary = "AI分析的错误说明",
            possibleCauses = listOf("可能的原因1", "可能的原因2"),
            suggestions = listOf("建议1", "建议2")
        )
    }
    
    private fun generateFallbackScript(description: String): String {
        return """
        // 基于描述生成的基础脚本模板
        // 用户描述: $description
        
        // 检查无障碍权限
        if (!auto.service) {
            toast("请先开启无障碍服务");
            exit();
        }
        
        // 等待界面加载
        sleep(2000);
        
        // TODO: 根据具体需求实现功能
        console.log("脚本开始执行");
        
        // 示例操作
        // click("按钮文本");
        // setText("输入内容");
        
        console.log("脚本执行完成");
        """.trimIndent()
    }
    
    private fun createFallbackUIAnalysis(): DetailedUIAnalysis {
        return DetailedUIAnalysis(
            elements = emptyList(),
            description = "无法连接到云端分析服务",
            suggestions = listOf("使用本地UI分析功能")
        )
    }
}

/**
 * 详细UI分析结果
 */
data class DetailedUIAnalysis(
    val elements: List<UIElement>,
    val description: String,
    val suggestions: List<String>
)

/**
 * 错误解释
 */
data class ErrorExplanation(
    val summary: String,
    val possibleCauses: List<String>,
    val suggestions: List<String>
)