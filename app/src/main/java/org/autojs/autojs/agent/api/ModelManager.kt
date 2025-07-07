package org.autojs.autojs.agent.api

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.HashMap

/**
 * AI模型管理器
 * 支持自定义添加、修改、删除各种AI模型
 */
class ModelManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: ModelManager? = null
        private const val PREF_NAME = "model_manager"
        private const val KEY_CUSTOM_MODELS = "custom_models"
        
        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    private val customModels = mutableMapOf<String, AIModel>()
    private val predefinedModels = mutableMapOf<String, AIModel>()
    
    init {
        initializePredefinedModels()
        loadCustomModels()
    }
    
    /**
     * 初始化预定义模型
     */
    private fun initializePredefinedModels() {
        // OpenAI models
        predefinedModels["openai-gpt-4"] = AIModel(
            id = "openai-gpt-4",
            name = "GPT-4",
            provider = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiType = APIType.OPENAI,
            modelName = "gpt-4",
            maxTokens = 8192,
            supportVision = true,
            description = "OpenAI GPT-4 模型"
        )
        
        predefinedModels["openai-gpt-3.5-turbo"] = AIModel(
            id = "openai-gpt-3.5-turbo",
            name = "GPT-3.5 Turbo",
            provider = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiType = APIType.OPENAI,
            modelName = "gpt-3.5-turbo",
            maxTokens = 4096,
            supportVision = false,
            description = "OpenAI GPT-3.5 Turbo 模型"
        )
        
        // Claude models
        predefinedModels["anthropic-claude-3"] = AIModel(
            id = "anthropic-claude-3",
            name = "Claude 3",
            provider = "Anthropic",
            baseUrl = "https://api.anthropic.com/v1",
            apiType = APIType.ANTHROPIC,
            modelName = "claude-3-opus-20240229",
            maxTokens = 4096,
            supportVision = true,
            description = "Anthropic Claude 3 模型"
        )
        
        // Google Gemini
        predefinedModels["google-gemini-pro"] = AIModel(
            id = "google-gemini-pro",
            name = "Gemini Pro",
            provider = "Google",
            baseUrl = "https://generativelanguage.googleapis.com/v1",
            apiType = APIType.GOOGLE,
            modelName = "gemini-pro",
            maxTokens = 2048,
            supportVision = false,
            description = "Google Gemini Pro 模型"
        )
        
        // 本地模型示例
        predefinedModels["local-ollama"] = AIModel(
            id = "local-ollama",
            name = "Ollama Local",
            provider = "Local",
            baseUrl = "http://localhost:11434/v1",
            apiType = APIType.OPENAI_COMPATIBLE,
            modelName = "llama2",
            maxTokens = 2048,
            supportVision = false,
            description = "本地 Ollama 模型"
        )
    }
    
    /**
     * 加载自定义模型
     */
    private fun loadCustomModels() {
        val json = sharedPreferences.getString(KEY_CUSTOM_MODELS, null)
        if (json != null) {
            try {
                val jsonArray = JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val modelJson = jsonArray.getJSONObject(i)
                    val model = AIModel.fromJson(modelJson)
                    customModels[model.id] = model
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 保存自定义模型
     */
    private suspend fun saveCustomModels() = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        customModels.values.forEach { model ->
            jsonArray.put(model.toJson())
        }
        sharedPreferences.edit()
            .putString(KEY_CUSTOM_MODELS, jsonArray.toString())
            .apply()
    }
    
    /**
     * 添加自定义模型
     */
    suspend fun addCustomModel(model: AIModel): Boolean {
        return try {
            customModels[model.id] = model
            saveCustomModels()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 更新自定义模型
     */
    suspend fun updateCustomModel(model: AIModel): Boolean {
        return try {
            if (customModels.containsKey(model.id)) {
                customModels[model.id] = model
                saveCustomModels()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 删除自定义模型
     */
    suspend fun deleteCustomModel(modelId: String): Boolean {
        return try {
            if (customModels.containsKey(modelId)) {
                customModels.remove(modelId)
                saveCustomModels()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 获取所有模型
     */
    fun getAllModels(): List<AIModel> {
        return (predefinedModels.values + customModels.values).sortedBy { it.provider + it.name }
    }
    
    /**
     * 获取预定义模型
     */
    fun getPredefinedModels(): List<AIModel> {
        return predefinedModels.values.toList()
    }
    
    /**
     * 获取自定义模型
     */
    fun getCustomModels(): List<AIModel> {
        return customModels.values.toList()
    }
    
    /**
     * 根据ID获取模型
     */
    fun getModelById(modelId: String): AIModel? {
        return customModels[modelId] ?: predefinedModels[modelId]
    }
    
    /**
     * 根据提供商获取模型
     */
    fun getModelsByProvider(provider: String): List<AIModel> {
        return getAllModels().filter { it.provider.equals(provider, ignoreCase = true) }
    }
    
    /**
     * 测试模型连接
     */
    suspend fun testModelConnection(model: AIModel, apiKey: String): TestResult {
        return try {
            val client = createClientForModel(model, apiKey)
            val testMessage = "Hello, this is a test message."
            
            // 创建简单的测试上下文
            val testContext = org.autojs.autojs.agent.model.ScreenContext(null, null)
            
            // 发送测试消息
            val response = client.chatWithAgent(testMessage, emptyList())
            
            TestResult(
                success = true,
                message = "连接成功",
                responseTime = System.currentTimeMillis(),
                modelResponse = response.message.content
            )
        } catch (e: Exception) {
            TestResult(
                success = false,
                message = "连接失败: ${e.message}",
                responseTime = System.currentTimeMillis(),
                error = e
            )
        }
    }
    
    /**
     * 为模型创建对应的客户端
     */
    fun createClientForModel(model: AIModel, apiKey: String): AgentAPI {
        return when (model.apiType) {
            APIType.OPENAI -> OpenAICompatibleClient(
                apiKey = apiKey,
                baseUrl = model.baseUrl,
                modelName = model.modelName,
                maxTokens = model.maxTokens
            )
            APIType.OPENAI_COMPATIBLE -> OpenAICompatibleClient(
                apiKey = apiKey,
                baseUrl = model.baseUrl,
                modelName = model.modelName,
                maxTokens = model.maxTokens
            )
            APIType.ANTHROPIC -> AnthropicClient(
                apiKey = apiKey,
                baseUrl = model.baseUrl,
                modelName = model.modelName,
                maxTokens = model.maxTokens
            )
            APIType.GOOGLE -> GoogleGeminiClient(
                apiKey = apiKey,
                baseUrl = model.baseUrl,
                modelName = model.modelName,
                maxTokens = model.maxTokens
            )
            APIType.CUSTOM -> CustomAPIClient(
                apiKey = apiKey,
                config = model.customConfig ?: emptyMap(),
                baseUrl = model.baseUrl,
                modelName = model.modelName
            )
        }
    }
    
    /**
     * 导出模型配置
     */
    fun exportModels(): String {
        val exportData = JSONObject().apply {
            put("version", "1.0")
            put("exportTime", System.currentTimeMillis())
            put("customModels", JSONArray().apply {
                customModels.values.forEach { model ->
                    put(model.toJson())
                }
            })
        }
        return exportData.toString(2)
    }
    
    /**
     * 导入模型配置
     */
    suspend fun importModels(jsonData: String): ImportResult {
        return try {
            val importData = JSONObject(jsonData)
            val modelsArray = importData.getJSONArray("customModels")
            val importedModels = mutableListOf<AIModel>()
            val skippedModels = mutableListOf<String>()
            
            for (i in 0 until modelsArray.length()) {
                try {
                    val modelJson = modelsArray.getJSONObject(i)
                    val model = AIModel.fromJson(modelJson)
                    
                    if (!customModels.containsKey(model.id)) {
                        customModels[model.id] = model
                        importedModels.add(model)
                    } else {
                        skippedModels.add(model.name)
                    }
                } catch (e: Exception) {
                    skippedModels.add("解析错误的模型")
                }
            }
            
            if (importedModels.isNotEmpty()) {
                saveCustomModels()
            }
            
            ImportResult(
                success = true,
                importedCount = importedModels.size,
                skippedCount = skippedModels.size,
                importedModels = importedModels,
                skippedModels = skippedModels
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                error = e.message ?: "导入失败"
            )
        }
    }
}

/**
 * AI模型数据类
 */
data class AIModel(
    val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val apiType: APIType,
    val modelName: String,
    val maxTokens: Int = 2048,
    val temperature: Float = 0.3f,
    val supportVision: Boolean = false,
    val description: String = "",
    val customConfig: Map<String, Any>? = null,
    val isCustom: Boolean = true,
    val createdTime: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("provider", provider)
            put("baseUrl", baseUrl)
            put("apiType", apiType.name)
            put("modelName", modelName)
            put("maxTokens", maxTokens)
            put("temperature", temperature.toDouble())
            put("supportVision", supportVision)
            put("description", description)
            put("isCustom", isCustom)
            put("createdTime", createdTime)
            put("lastUsed", lastUsed)
            
            customConfig?.let { config ->
                val configJson = JSONObject()
                config.forEach { (key, value) ->
                    configJson.put(key, value)
                }
                put("customConfig", configJson)
            }
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): AIModel {
            val customConfig = json.optJSONObject("customConfig")?.let { configJson ->
                val config = mutableMapOf<String, Any>()
                configJson.keys().forEach { key ->
                    config[key] = configJson.get(key)
                }
                config
            }
            
            return AIModel(
                id = json.getString("id"),
                name = json.getString("name"),
                provider = json.getString("provider"),
                baseUrl = json.getString("baseUrl"),
                apiType = APIType.valueOf(json.getString("apiType")),
                modelName = json.getString("modelName"),
                maxTokens = json.optInt("maxTokens", 2048),
                temperature = json.optDouble("temperature", 0.3).toFloat(),
                supportVision = json.optBoolean("supportVision", false),
                description = json.optString("description", ""),
                customConfig = customConfig,
                isCustom = json.optBoolean("isCustom", true),
                createdTime = json.optLong("createdTime", System.currentTimeMillis()),
                lastUsed = json.optLong("lastUsed", 0)
            )
        }
    }
}

/**
 * API类型枚举
 */
enum class APIType {
    OPENAI,              // OpenAI 官方API
    OPENAI_COMPATIBLE,   // OpenAI 兼容API (如Ollama、vLLM等)
    ANTHROPIC,           // Anthropic Claude
    GOOGLE,              // Google Gemini
    CUSTOM               // 自定义API格式
}

/**
 * 测试结果
 */
data class TestResult(
    val success: Boolean,
    val message: String,
    val responseTime: Long,
    val modelResponse: String? = null,
    val error: Throwable? = null
)

/**
 * 导入结果
 */
data class ImportResult(
    val success: Boolean,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val importedModels: List<AIModel> = emptyList(),
    val skippedModels: List<String> = emptyList(),
    val error: String? = null
)