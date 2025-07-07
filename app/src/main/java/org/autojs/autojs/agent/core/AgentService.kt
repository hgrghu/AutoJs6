package org.autojs.autojs.agent.core

import android.content.Context
import kotlinx.coroutines.*
import org.autojs.autojs.agent.api.*
import org.autojs.autojs.agent.analysis.*
import org.autojs.autojs.agent.model.*
import org.autojs.autojs.agent.storage.*
import org.autojs.autojs.agent.optimization.*
import java.util.concurrent.ConcurrentHashMap

/**
 * AI Agent核心服务
 * 协调各个模块，提供统一的Agent功能接口
 */
class AgentService private constructor(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var INSTANCE: AgentService? = null
        
        fun getInstance(context: Context): AgentService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 核心组件
    private var agentAPI: AgentAPI? = null
    private val screenAnalyzer = ScreenAnalyzer()
    private val scriptOptimizer = ScriptOptimizer()
    private val scriptRepository = ScriptRepository(context)
    private val templateManager = TemplateManager(context)
    private val cacheManager = CacheManager(context)
    
    // 新增组件
    private val modelManager = ModelManager.getInstance(context)
    private val githubManager = GitHubManager.getInstance(context)
    
    // 配置和状态
    private val agentConfig = AgentConfig(context)
    private val contextManager = ContextManager()
    private val taskExecutor = TaskExecutor()
    
    // 监控和缓存
    private var realtimeMonitoringJob: Job? = null
    private val activeChats = ConcurrentHashMap<String, List<ChatMessage>>()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 状态标志
    private var isInitialized = false
    private var isRealtimeMonitoringEnabled = false
    
    /**
     * 初始化Agent服务
     */
    suspend fun initialize() {
        if (isInitialized) return
        
        try {
            // 初始化AI API客户端
            initializeAIClient()
            
            // 初始化存储组件
            scriptRepository.initialize()
            templateManager.initialize()
            cacheManager.initialize()
            
            // 加载配置
            agentConfig.load()
            
            isInitialized = true
        } catch (e: Exception) {
            throw AgentException("Agent服务初始化失败", e)
        }
    }
    
    /**
     * 初始化AI客户端
     */
    private fun initializeAIClient() {
        val config = agentConfig.getAIConfig()
        val selectedModelId = config.selectedModelId
        val apiKey = config.apiKey
        
        if (selectedModelId.isNotEmpty() && apiKey.isNotEmpty()) {
            val model = modelManager.getModelById(selectedModelId)
            if (model != null) {
                agentAPI = modelManager.createClientForModel(model, apiKey)
            }
        } else {
            // 回退到旧的配置方式
            agentAPI = when (config.modelType) {
                AgentConfig.ModelType.CHATGPT -> {
                    ChatGPTClient(
                        apiKey = config.apiKey,
                        baseUrl = config.baseUrl ?: "https://api.openai.com/v1",
                        model = config.model ?: "gpt-4"
                    )
                }
                AgentConfig.ModelType.LOCAL -> {
                    LocalLLMClient(config.localModelPath ?: "")
                }
                AgentConfig.ModelType.CUSTOM -> {
                    // 支持自定义模型接口
                    null
                }
            }
        }
    }
    
    /**
     * 分析并优化脚本
     */
    suspend fun analyzeAndOptimizeScript(script: String): OptimizationResult {
        requireInitialized()
        
        return try {
            // 获取当前屏幕上下文
            val screenContext = screenAnalyzer.getScreenContext()
            
            // 检查缓存
            val cacheKey = cacheManager.generateCacheKey(script, screenContext)
            cacheManager.getOptimizationResult(cacheKey)?.let { cached ->
                return cached
            }
            
            // 使用AI分析脚本
            val aiResult = agentAPI?.analyzeScript(script, screenContext ?: ScreenContext(null, null))
                ?: throw AgentException("AI客户端未初始化")
            
            // 本地脚本优化
            val localOptimization = scriptOptimizer.optimizeScript(script, screenContext)
            
            // 合并结果
            val combinedResult = combineOptimizationResults(aiResult, localOptimization)
            
            // 缓存结果
            cacheManager.cacheOptimizationResult(cacheKey, combinedResult)
            
            // 记录到历史
            scriptRepository.saveOptimizationRecord(script, combinedResult)
            
            combinedResult
        } catch (e: Exception) {
            OptimizationResult(
                originalScript = script,
                optimizedScript = script,
                isSuccessful = false,
                warnings = listOf("优化失败: ${e.message}")
            )
        }
    }
    
    /**
     * 根据自然语言生成脚本
     */
    suspend fun generateScript(request: String): ScriptGenerationResult {
        requireInitialized()
        
        return try {
            // 获取屏幕上下文
            val screenContext = screenAnalyzer.getScreenContext()
            
            // 检查模板库中是否有相似的脚本
            val similarTemplates = templateManager.findSimilarTemplates(request)
            if (similarTemplates.isNotEmpty()) {
                val template = similarTemplates.first()
                // 基于模板生成脚本
                return adaptTemplateToRequest(template, request, screenContext)
            }
            
            // 使用AI生成脚本
            val result = agentAPI?.generateScript(request, screenContext ?: ScreenContext(null, null))
                ?: throw AgentException("AI客户端未初始化")
            
            // 验证生成的脚本
            val validationResult = validateGeneratedScript(result.script, screenContext)
            
            // 如果验证失败，尝试修复
            val finalScript = if (!validationResult.isValid) {
                repairScript(result.script, validationResult.errors)
            } else {
                result.script
            }
            
            val finalResult = result.copy(
                script = finalScript,
                isExecutable = validationResult.isValid
            )
            
            // 保存到历史记录
            scriptRepository.saveGenerationRecord(request, finalResult)
            
            finalResult
        } catch (e: Exception) {
            ScriptGenerationResult(
                script = "// 脚本生成失败: ${e.message}",
                explanation = "无法生成脚本，请检查网络连接和API配置",
                confidence = 0.0f,
                isExecutable = false
            )
        }
    }
    
    /**
     * 启动实时屏幕分析
     */
    fun startRealtimeAnalysis(callback: (ActionSuggestion) -> Unit) {
        if (isRealtimeMonitoringEnabled) return
        
        isRealtimeMonitoringEnabled = true
        realtimeMonitoringJob = screenAnalyzer.startRealtimeMonitoring { screenData ->
            serviceScope.launch {
                try {
                    val suggestion = agentAPI?.analyzeScreenRealtime(screenData)
                    suggestion?.let { callback(it) }
                } catch (e: Exception) {
                    // 静默处理错误，避免频繁报错
                }
            }
        }
    }
    
    /**
     * 停止实时屏幕分析
     */
    fun stopRealtimeAnalysis() {
        isRealtimeMonitoringEnabled = false
        realtimeMonitoringJob?.cancel()
        realtimeMonitoringJob = null
    }
    
    /**
     * 与AI助手聊天
     */
    suspend fun chatWithAgent(
        message: String, 
        sessionId: String = "default"
    ): ChatResponse {
        requireInitialized()
        
        return try {
            // 获取对话历史
            val history = activeChats[sessionId] ?: emptyList()
            
            // 创建用户消息
            val userMessage = ChatMessage(
                content = message,
                role = ChatMessage.MessageRole.USER,
                context = screenAnalyzer.getScreenContext()
            )
            
            // 调用AI
            val response = agentAPI?.chatWithAgent(message, history)
                ?: throw AgentException("AI客户端未初始化")
            
            // 更新对话历史
            val updatedHistory = history + listOf(userMessage, response.message)
            activeChats[sessionId] = updatedHistory.takeLast(20) // 保留最近20条消息
            
            // 保存对话记录
            scriptRepository.saveChatHistory(sessionId, userMessage, response.message)
            
            response
        } catch (e: Exception) {
            ChatResponse(
                message = ChatMessage(
                    content = "抱歉，我遇到了问题: ${e.message}",
                    role = ChatMessage.MessageRole.ASSISTANT
                )
            )
        }
    }
    
    /**
     * 获取脚本建议
     */
    suspend fun getScriptSuggestions(script: String, executionResult: ExecutionResult? = null): List<Suggestion> {
        requireInitialized()
        
        return try {
            // 从AI获取建议
            val aiSuggestions = agentAPI?.getSuggestions(script, executionResult) ?: emptyList()
            
            // 从本地分析获取建议
            val localSuggestions = scriptOptimizer.analyzeSuggestions(script, executionResult)
            
            // 合并并去重建议
            val allSuggestions = (aiSuggestions + localSuggestions).distinctBy { it.title }
            
            // 按优先级排序
            allSuggestions.sortedByDescending { it.priority.ordinal }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取脚本模板列表
     */
    suspend fun getScriptTemplates(category: String? = null, query: String? = null): List<ScriptTemplate> {
        return templateManager.getTemplates(category, query)
    }
    
    /**
     * 保存脚本模板
     */
    suspend fun saveScriptTemplate(template: ScriptTemplate): Boolean {
        return try {
            templateManager.saveTemplate(template)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取脚本执行历史
     */
    suspend fun getScriptHistory(limit: Int = 50): List<ScriptExecutionRecord> {
        return scriptRepository.getExecutionHistory(limit)
    }
    
    /**
     * 更新Agent配置
     */
    suspend fun updateConfig(config: AgentConfig.AIConfig) {
        agentConfig.updateAIConfig(config)
        initializeAIClient() // 重新初始化客户端
    }
    
    /**
     * 获取当前配置
     */
    fun getConfig(): AgentConfig.AIConfig {
        return agentConfig.getAIConfig()
    }
    
    /**
     * 获取模型管理器
     */
    fun getModelManager(): ModelManager {
        return modelManager
    }
    
    /**
     * 获取GitHub管理器
     */
    fun getGitHubManager(): GitHubManager {
        return githubManager
    }
    
    /**
     * 切换使用的AI模型
     */
    suspend fun switchModel(modelId: String, apiKey: String): Boolean {
        return try {
            val model = modelManager.getModelById(modelId)
            if (model != null) {
                // 测试模型连接
                val testResult = modelManager.testModelConnection(model, apiKey)
                if (testResult.success) {
                    // 更新配置
                    agentConfig.updateAIConfig(
                        agentConfig.getAIConfig().copy(
                            selectedModelId = modelId,
                            apiKey = apiKey
                        )
                    )
                    // 重新初始化客户端
                    initializeAIClient()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 自动同步优化结果到GitHub
     */
    suspend fun autoSyncOptimizationResult(
        scriptName: String,
        originalScript: String,
        optimizationResult: OptimizationResult
    ) {
        if (githubManager.isAutoSyncEnabled() && optimizationResult.isSuccessful) {
            try {
                githubManager.autoSyncIfEnabled(
                    scriptName = scriptName,
                    optimizedContent = optimizationResult.optimizedScript,
                    originalScore = 60, // 默认原始分数
                    newScore = optimizationResult.score
                )
            } catch (e: Exception) {
                // 静默处理错误，不影响主要功能
            }
        }
    }
    
    /**
     * 手动推送脚本到GitHub
     */
    suspend fun pushScriptToGitHub(
        scriptName: String,
        scriptContent: String,
        commitMessage: String? = null
    ): Boolean {
        return try {
            val result = githubManager.pushScript(
                scriptName = scriptName,
                scriptContent = scriptContent,
                commitMessage = commitMessage ?: "Update script via AutoJs6 Agent"
            )
            result.success
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 从GitHub拉取脚本
     */
    suspend fun pullScriptFromGitHub(filePath: String): String? {
        return try {
            val result = githubManager.pullScript(filePath)
            if (result.success) result.content else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopRealtimeAnalysis()
        screenAnalyzer.cleanup()
        serviceScope.cancel()
        activeChats.clear()
    }
    
    // 辅助方法
    
    private fun requireInitialized() {
        if (!isInitialized) {
            throw AgentException("Agent服务未初始化")
        }
    }
    
    private fun combineOptimizationResults(
        aiResult: OptimizationResult,
        localResult: OptimizationResult
    ): OptimizationResult {
        return OptimizationResult(
            originalScript = aiResult.originalScript,
            optimizedScript = if (aiResult.score > localResult.score) aiResult.optimizedScript else localResult.optimizedScript,
            improvements = aiResult.improvements + localResult.improvements,
            score = maxOf(aiResult.score, localResult.score),
            suggestions = (aiResult.suggestions + localResult.suggestions).distinctBy { it.title },
            warnings = (aiResult.warnings + localResult.warnings).distinct(),
            isSuccessful = aiResult.isSuccessful && localResult.isSuccessful
        )
    }
    
    private suspend fun adaptTemplateToRequest(
        template: ScriptTemplate,
        request: String,
        context: ScreenContext?
    ): ScriptGenerationResult {
        // 基于模板和请求生成定制化脚本
        val adaptedScript = scriptOptimizer.adaptTemplate(template, request, context)
        
        return ScriptGenerationResult(
            script = adaptedScript,
            explanation = "基于模板 '${template.name}' 生成的脚本",
            confidence = 0.8f,
            requiredPermissions = template.requiredPermissions,
            isExecutable = true
        )
    }
    
    private suspend fun validateGeneratedScript(
        script: String,
        context: ScreenContext?
    ): ValidationResult {
        return agentAPI?.validateScript(script, context ?: ScreenContext(null, null))
            ?: ValidationResult(isValid = false, errors = listOf(ValidationError(message = "无法验证脚本")))
    }
    
    private suspend fun repairScript(
        script: String,
        errors: List<ValidationError>
    ): String {
        // 尝试修复脚本中的常见错误
        return scriptOptimizer.repairScript(script, errors)
    }
}

/**
 * Agent异常类
 */
class AgentException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 脚本执行记录
 */
data class ScriptExecutionRecord(
    val id: String,
    val script: String,
    val executionTime: Long,
    val isSuccess: Boolean,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val context: ScreenContext? = null
)