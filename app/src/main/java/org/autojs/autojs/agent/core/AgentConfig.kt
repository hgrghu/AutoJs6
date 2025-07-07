package org.autojs.autojs.agent.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Agent配置管理类
 * 负责管理AI模型配置、缓存设置、界面偏好等
 */
class AgentConfig(private val context: Context) {
    
    companion object {
        private const val PREF_NAME = "agent_config"
        private const val KEY_AI_CONFIG = "ai_config"
        private const val KEY_CACHE_CONFIG = "cache_config"
        private const val KEY_UI_CONFIG = "ui_config"
        private const val KEY_FEATURE_FLAGS = "feature_flags"
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    private var currentAIConfig: AIConfig? = null
    private var currentCacheConfig: CacheConfig? = null
    private var currentUIConfig: UIConfig? = null
    private var currentFeatureFlags: FeatureFlags? = null
    
    /**
     * 加载配置
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        currentAIConfig = loadAIConfig()
        currentCacheConfig = loadCacheConfig()
        currentUIConfig = loadUIConfig()
        currentFeatureFlags = loadFeatureFlags()
    }
    
    /**
     * 获取AI配置
     */
    fun getAIConfig(): AIConfig {
        return currentAIConfig ?: AIConfig().also { currentAIConfig = it }
    }
    
    /**
     * 更新AI配置
     */
    suspend fun updateAIConfig(config: AIConfig) = withContext(Dispatchers.IO) {
        currentAIConfig = config
        saveAIConfig(config)
    }
    
    /**
     * 获取缓存配置
     */
    fun getCacheConfig(): CacheConfig {
        return currentCacheConfig ?: CacheConfig().also { currentCacheConfig = it }
    }
    
    /**
     * 更新缓存配置
     */
    suspend fun updateCacheConfig(config: CacheConfig) = withContext(Dispatchers.IO) {
        currentCacheConfig = config
        saveCacheConfig(config)
    }
    
    /**
     * 获取UI配置
     */
    fun getUIConfig(): UIConfig {
        return currentUIConfig ?: UIConfig().also { currentUIConfig = it }
    }
    
    /**
     * 更新UI配置
     */
    suspend fun updateUIConfig(config: UIConfig) = withContext(Dispatchers.IO) {
        currentUIConfig = config
        saveUIConfig(config)
    }
    
    /**
     * 获取功能标志
     */
    fun getFeatureFlags(): FeatureFlags {
        return currentFeatureFlags ?: FeatureFlags().also { currentFeatureFlags = it }
    }
    
    /**
     * 更新功能标志
     */
    suspend fun updateFeatureFlags(flags: FeatureFlags) = withContext(Dispatchers.IO) {
        currentFeatureFlags = flags
        saveFeatureFlags(flags)
    }
    
    // 私有方法：加载配置
    
    private fun loadAIConfig(): AIConfig {
        val json = sharedPreferences.getString(KEY_AI_CONFIG, null)
        return if (json != null) {
            try {
                AIConfig.fromJson(json)
            } catch (e: Exception) {
                AIConfig() // 返回默认配置
            }
        } else {
            AIConfig()
        }
    }
    
    private fun loadCacheConfig(): CacheConfig {
        val json = sharedPreferences.getString(KEY_CACHE_CONFIG, null)
        return if (json != null) {
            try {
                CacheConfig.fromJson(json)
            } catch (e: Exception) {
                CacheConfig()
            }
        } else {
            CacheConfig()
        }
    }
    
    private fun loadUIConfig(): UIConfig {
        val json = sharedPreferences.getString(KEY_UI_CONFIG, null)
        return if (json != null) {
            try {
                UIConfig.fromJson(json)
            } catch (e: Exception) {
                UIConfig()
            }
        } else {
            UIConfig()
        }
    }
    
    private fun loadFeatureFlags(): FeatureFlags {
        val json = sharedPreferences.getString(KEY_FEATURE_FLAGS, null)
        return if (json != null) {
            try {
                FeatureFlags.fromJson(json)
            } catch (e: Exception) {
                FeatureFlags()
            }
        } else {
            FeatureFlags()
        }
    }
    
    // 私有方法：保存配置
    
    private fun saveAIConfig(config: AIConfig) {
        sharedPreferences.edit()
            .putString(KEY_AI_CONFIG, config.toJson())
            .apply()
    }
    
    private fun saveCacheConfig(config: CacheConfig) {
        sharedPreferences.edit()
            .putString(KEY_CACHE_CONFIG, config.toJson())
            .apply()
    }
    
    private fun saveUIConfig(config: UIConfig) {
        sharedPreferences.edit()
            .putString(KEY_UI_CONFIG, config.toJson())
            .apply()
    }
    
    private fun saveFeatureFlags(flags: FeatureFlags) {
        sharedPreferences.edit()
            .putString(KEY_FEATURE_FLAGS, flags.toJson())
            .apply()
    }
    
    /**
     * AI配置
     */
    data class AIConfig(
        val modelType: ModelType = ModelType.CHATGPT,
        val apiKey: String = "",
        val baseUrl: String? = null,
        val model: String? = null,
        val localModelPath: String? = null,
        val maxTokens: Int = 2000,
        val temperature: Float = 0.3f,
        val timeout: Long = 30000,
        val maxRetries: Int = 3,
        val enableVision: Boolean = true,
        val enableStreaming: Boolean = false
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("modelType", modelType.name)
                put("apiKey", apiKey)
                put("baseUrl", baseUrl)
                put("model", model)
                put("localModelPath", localModelPath)
                put("maxTokens", maxTokens)
                put("temperature", temperature.toDouble())
                put("timeout", timeout)
                put("maxRetries", maxRetries)
                put("enableVision", enableVision)
                put("enableStreaming", enableStreaming)
            }.toString()
        }
        
        companion object {
            fun fromJson(json: String): AIConfig {
                val obj = JSONObject(json)
                return AIConfig(
                    modelType = ModelType.valueOf(obj.optString("modelType", ModelType.CHATGPT.name)),
                    apiKey = obj.optString("apiKey", ""),
                    baseUrl = obj.optString("baseUrl", null),
                    model = obj.optString("model", null),
                    localModelPath = obj.optString("localModelPath", null),
                    maxTokens = obj.optInt("maxTokens", 2000),
                    temperature = obj.optDouble("temperature", 0.3).toFloat(),
                    timeout = obj.optLong("timeout", 30000),
                    maxRetries = obj.optInt("maxRetries", 3),
                    enableVision = obj.optBoolean("enableVision", true),
                    enableStreaming = obj.optBoolean("enableStreaming", false)
                )
            }
        }
    }
    
    /**
     * 缓存配置
     */
    data class CacheConfig(
        val enableCache: Boolean = true,
        val maxCacheSize: Long = 100 * 1024 * 1024, // 100MB
        val cacheExpiry: Long = 24 * 60 * 60 * 1000, // 24小时
        val enableScreenshotCache: Boolean = true,
        val maxScreenshotCache: Int = 50,
        val enableOptimizationCache: Boolean = true,
        val maxOptimizationCache: Int = 100
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("enableCache", enableCache)
                put("maxCacheSize", maxCacheSize)
                put("cacheExpiry", cacheExpiry)
                put("enableScreenshotCache", enableScreenshotCache)
                put("maxScreenshotCache", maxScreenshotCache)
                put("enableOptimizationCache", enableOptimizationCache)
                put("maxOptimizationCache", maxOptimizationCache)
            }.toString()
        }
        
        companion object {
            fun fromJson(json: String): CacheConfig {
                val obj = JSONObject(json)
                return CacheConfig(
                    enableCache = obj.optBoolean("enableCache", true),
                    maxCacheSize = obj.optLong("maxCacheSize", 100 * 1024 * 1024),
                    cacheExpiry = obj.optLong("cacheExpiry", 24 * 60 * 60 * 1000),
                    enableScreenshotCache = obj.optBoolean("enableScreenshotCache", true),
                    maxScreenshotCache = obj.optInt("maxScreenshotCache", 50),
                    enableOptimizationCache = obj.optBoolean("enableOptimizationCache", true),
                    maxOptimizationCache = obj.optInt("maxOptimizationCache", 100)
                )
            }
        }
    }
    
    /**
     * UI配置
     */
    data class UIConfig(
        val showAgentButton: Boolean = true,
        val enableFloatingChat: Boolean = true,
        val autoShowSuggestions: Boolean = true,
        val suggestionDelay: Long = 2000,
        val enableRealTimeAnalysis: Boolean = false,
        val analysisInterval: Long = 1000,
        val showOptimizationDialog: Boolean = true,
        val enableVoiceInput: Boolean = false,
        val chatWindowPosition: WindowPosition = WindowPosition.BOTTOM_RIGHT,
        val chatWindowSize: WindowSize = WindowSize.MEDIUM
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("showAgentButton", showAgentButton)
                put("enableFloatingChat", enableFloatingChat)
                put("autoShowSuggestions", autoShowSuggestions)
                put("suggestionDelay", suggestionDelay)
                put("enableRealTimeAnalysis", enableRealTimeAnalysis)
                put("analysisInterval", analysisInterval)
                put("showOptimizationDialog", showOptimizationDialog)
                put("enableVoiceInput", enableVoiceInput)
                put("chatWindowPosition", chatWindowPosition.name)
                put("chatWindowSize", chatWindowSize.name)
            }.toString()
        }
        
        companion object {
            fun fromJson(json: String): UIConfig {
                val obj = JSONObject(json)
                return UIConfig(
                    showAgentButton = obj.optBoolean("showAgentButton", true),
                    enableFloatingChat = obj.optBoolean("enableFloatingChat", true),
                    autoShowSuggestions = obj.optBoolean("autoShowSuggestions", true),
                    suggestionDelay = obj.optLong("suggestionDelay", 2000),
                    enableRealTimeAnalysis = obj.optBoolean("enableRealTimeAnalysis", false),
                    analysisInterval = obj.optLong("analysisInterval", 1000),
                    showOptimizationDialog = obj.optBoolean("showOptimizationDialog", true),
                    enableVoiceInput = obj.optBoolean("enableVoiceInput", false),
                    chatWindowPosition = WindowPosition.valueOf(obj.optString("chatWindowPosition", WindowPosition.BOTTOM_RIGHT.name)),
                    chatWindowSize = WindowSize.valueOf(obj.optString("chatWindowSize", WindowSize.MEDIUM.name))
                )
            }
        }
    }
    
    /**
     * 功能标志
     */
    data class FeatureFlags(
        val enableAgent: Boolean = true,
        val enableRealtimeOptimization: Boolean = false,
        val enableAutoScriptGeneration: Boolean = true,
        val enableCoordinateOptimization: Boolean = true,
        val enableChatInterface: Boolean = true,
        val enableTemplateLibrary: Boolean = true,
        val enableScriptValidation: Boolean = true,
        val enablePerformanceAnalysis: Boolean = false,
        val enableAdvancedSuggestions: Boolean = true,
        val enableBetaFeatures: Boolean = false
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("enableAgent", enableAgent)
                put("enableRealtimeOptimization", enableRealtimeOptimization)
                put("enableAutoScriptGeneration", enableAutoScriptGeneration)
                put("enableCoordinateOptimization", enableCoordinateOptimization)
                put("enableChatInterface", enableChatInterface)
                put("enableTemplateLibrary", enableTemplateLibrary)
                put("enableScriptValidation", enableScriptValidation)
                put("enablePerformanceAnalysis", enablePerformanceAnalysis)
                put("enableAdvancedSuggestions", enableAdvancedSuggestions)
                put("enableBetaFeatures", enableBetaFeatures)
            }.toString()
        }
        
        companion object {
            fun fromJson(json: String): FeatureFlags {
                val obj = JSONObject(json)
                return FeatureFlags(
                    enableAgent = obj.optBoolean("enableAgent", true),
                    enableRealtimeOptimization = obj.optBoolean("enableRealtimeOptimization", false),
                    enableAutoScriptGeneration = obj.optBoolean("enableAutoScriptGeneration", true),
                    enableCoordinateOptimization = obj.optBoolean("enableCoordinateOptimization", true),
                    enableChatInterface = obj.optBoolean("enableChatInterface", true),
                    enableTemplateLibrary = obj.optBoolean("enableTemplateLibrary", true),
                    enableScriptValidation = obj.optBoolean("enableScriptValidation", true),
                    enablePerformanceAnalysis = obj.optBoolean("enablePerformanceAnalysis", false),
                    enableAdvancedSuggestions = obj.optBoolean("enableAdvancedSuggestions", true),
                    enableBetaFeatures = obj.optBoolean("enableBetaFeatures", false)
                )
            }
        }
    }
    
    /**
     * 模型类型枚举
     */
    enum class ModelType {
        CHATGPT,      // OpenAI ChatGPT
        LOCAL,        // 本地模型
        CUSTOM        // 自定义模型
    }
    
    /**
     * 窗口位置枚举
     */
    enum class WindowPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }
    
    /**
     * 窗口大小枚举
     */
    enum class WindowSize {
        SMALL, MEDIUM, LARGE, FULLSCREEN
    }
}