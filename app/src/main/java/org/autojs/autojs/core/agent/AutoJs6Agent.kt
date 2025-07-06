package org.autojs.autojs.core.agent

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.*
import org.autojs.autojs.core.log.LogManager
import org.autojs.autojs.core.memory.MemoryMonitor
import org.autojs.autojs.core.script.OptimizedScriptExecutor
import org.autojs.autojs.core.agent.models.*
import org.autojs.autojs.core.agent.understanding.TaskUnderstandingImpl
import org.autojs.autojs.core.agent.ui.UIAnalyzerImpl
import org.autojs.autojs.core.agent.generation.ScriptGeneratorImpl
import org.autojs.autojs.core.agent.learning.LearningModuleImpl
import org.autojs.autojs.core.agent.models.LocalAIModels
import org.autojs.autojs.core.agent.cloud.CloudAIService
import java.util.UUID
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * AutoJs6 智能Agent核心实现
 * 提供自然语言到脚本的转换，UI智能分析，自动化任务执行等功能
 */
class AutoJs6Agent private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AutoJs6Agent"
        
        @Volatile
        private var INSTANCE: AutoJs6Agent? = null
        
        fun getInstance(context: Context): AutoJs6Agent {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoJs6Agent(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 核心组件
    private val memoryMonitor = MemoryMonitor.getInstance()
    private val scriptExecutor = OptimizedScriptExecutor.getInstance(context)
    
    // AI组件
    private val localModels = LocalAIModels(context)
    private val cloudService = CloudAIService()
    
    // 功能模块
    private val taskUnderstanding = TaskUnderstandingImpl(localModels, cloudService)
    private val uiAnalyzer = UIAnalyzerImpl(localModels)
    private val scriptGenerator = ScriptGeneratorImpl(localModels, cloudService)
    private val learningModule = LearningModuleImpl(context)
    
    // Agent状态
    private var isInitialized = false
    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 状态管理
    private val _agentStatus = MutableLiveData<AgentStatus>()
    val agentStatus: LiveData<AgentStatus> = _agentStatus
    
    // 执行历史
    private val executionHistory = mutableListOf<ExecutionRecord>()
    private val activeSessions = mutableMapOf<String, LearningSession>()
    
    /**
     * 初始化Agent系统
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        
        try {
            LogManager.i(TAG, "正在初始化AutoJs6 Agent...")
            
            // 检查内存情况
            memoryMonitor.logMemoryUsage()
            
            // 初始化本地模型
            val localModelsLoaded = localModels.initialize()
            LogManager.i(TAG, "本地模型初始化结果: $localModelsLoaded")
            
            // 初始化云端服务（可选）
            val cloudServiceAvailable = cloudService.initialize()
            LogManager.i(TAG, "云端服务可用性: $cloudServiceAvailable")
            
            // 初始化各功能模块
            taskUnderstanding.initialize()
            uiAnalyzer.initialize()
            scriptGenerator.initialize()
            learningModule.initialize()
            
            isInitialized = true
            LogManager.i(TAG, "AutoJs6 Agent初始化完成")
            
            updateAgentStatus()
            
            true
        } catch (e: Exception) {
            LogManager.e(TAG, "Agent初始化失败", e)
            isInitialized = false
            updateAgentStatus()
            false
        }
    }
    
    /**
     * 主要功能：根据自然语言描述执行任务
     * @param description 用户的自然语言描述
     * @param options 执行选项
     * @return Agent执行结果
     */
    suspend fun executeNaturalLanguageTask(
        description: String,
        options: AgentExecutionOptions = AgentExecutionOptions()
    ): AgentResult {
        ensureInitialized()
        
        val sessionId = UUID.randomUUID().toString()
        LogManager.i(TAG, "开始执行任务 [$sessionId]: $description")
        
        return try {
            // 1. 任务理解阶段
            val task = taskUnderstanding.parseNaturalLanguage(description)
            LogManager.d(TAG, "任务解析完成: ${task.type}")
            
            // 2. UI分析阶段
            val screenshot = if (options.requireUIAnalysis) {
                captureScreenSafely()
            } else null
            
            val uiAnalysis = screenshot?.let { 
                uiAnalyzer.analyzeScreen(it)
            }
            
            LogManager.d(TAG, "UI分析完成，发现${uiAnalysis?.elements?.size ?: 0}个可操作元素")
            
            // 3. 任务计划生成
            val taskPlan = taskUnderstanding.generateTaskPlan(task, uiAnalysis)
            LogManager.d(TAG, "任务计划生成完成，包含${taskPlan.steps.size}个步骤")
            
            // 4. 脚本生成
            val script = scriptGenerator.generateScript(taskPlan, options.scriptOptions)
            LogManager.d(TAG, "脚本生成完成: ${script.name}")
            
            // 5. 脚本执行（可选）
            val executionResult = if (options.autoExecute) {
                executeScriptWithMonitoring(script)
            } else null
            
            // 6. 学习记录
            learningModule.recordExecution(task, script, executionResult)
            
            // 7. 记录执行历史
            val executionRecord = ExecutionRecord(
                id = generateExecutionId(),
                task = task,
                script = script,
                result = executionResult ?: OptimizedScriptExecutor.ScriptResult.success("Script generated but not executed"),
                executionTime = System.currentTimeMillis(),
                timestamp = System.currentTimeMillis()
            )
            executionHistory.add(executionRecord)
            
            // 8. 学习模式记录
            if (options.enableLearning) {
                recordExecution(sessionId, executionRecord)
            }
            
            AgentResult.Success(
                sessionId = sessionId,
                task = task,
                script = script,
                executionResult = executionResult,
                uiAnalysis = uiAnalysis
            )
            
        } catch (e: Exception) {
            LogManager.e(TAG, "任务执行失败 [$sessionId]", e)
            AgentResult.Error(
                sessionId = sessionId,
                error = e,
                suggestions = generateErrorSuggestions(e)
            )
        }
    }
    
    /**
     * UI分析模式：分析当前界面并生成操作建议
     */
    suspend fun analyzeCurrentUI(): UIAnalysisReport {
        ensureInitialized()
        
        return try {
            val screenshot = captureScreenSafely()
            val analysis = uiAnalyzer.analyzeScreen(screenshot)
            
            val suggestedActions = generateActionSuggestions(analysis)
            val generatedCode = generateUIOperationCode(analysis)
            
            UIAnalysisReport(
                screenshot = screenshot,
                elements = analysis.elements,
                suggestedActions = suggestedActions,
                generatedCode = generatedCode,
                confidence = analysis.confidence,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            LogManager.e(TAG, "UI分析失败", e)
            throw AgentException("UI分析失败: ${e.message}", e)
        }
    }
    
    /**
     * 开始学习模式
     */
    suspend fun startLearningMode(): LearningSession {
        ensureInitialized()
        return learningModule.startLearningSession()
    }
    
    /**
     * 获取Agent状态
     */
    fun getAgentStatus(): AgentStatus {
        return AgentStatus(
            isInitialized = isInitialized,
            localModelsStatus = localModels.getStatus(),
            cloudServiceStatus = cloudService.getStatus(),
            memoryUsage = memoryMonitor.getDetailedMemoryInfo(context),
            activeSessionsCount = getActiveSessionsCount()
        )
    }
    
    /**
     * 获取脚本执行历史
     */
    suspend fun getExecutionHistory(limit: Int = 50): List<ExecutionRecord> {
        return executionHistory.takeLast(limit).reversed()
    }
    
    /**
     * 优化现有脚本
     */
    suspend fun optimizeScript(script: String): OptimizedScript {
        ensureInitialized()
        
        return try {
            val suggestions = learningModule.suggestOptimizations(script)
            val optimizedCode = scriptGenerator.optimizeScript(script, suggestions)
            
            OptimizedScript(
                originalScript = script,
                optimizedScript = optimizedCode,
                optimizations = suggestions,
                estimatedImprovement = calculateImprovement(script, optimizedCode)
            )
        } catch (e: Exception) {
            LogManager.e(TAG, "脚本优化失败", e)
            throw AgentException("脚本优化失败: ${e.message}", e)
        }
    }
    
    /**
     * 清理Agent资源
     */
    fun cleanup() {
        LogManager.i(TAG, "正在清理Agent资源...")
        
        agentScope.cancel()
        localModels.cleanup()
        cloudService.cleanup()
        learningModule.cleanup()
        
        isInitialized = false
        LogManager.i(TAG, "Agent资源清理完成")
    }
    
    // 私有方法
    
    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("Agent未初始化，请先调用initialize()")
        }
    }
    
    private suspend fun captureScreenSafely(): Bitmap {
        return try {
            // 这里应该调用AutoJs6的截图功能
            // 暂时返回一个占位实现
            TODO("实现截图功能集成")
        } catch (e: Exception) {
            LogManager.e(TAG, "截图失败", e)
            throw AgentException("无法获取屏幕截图", e)
        }
    }
    
    private suspend fun executeScriptWithMonitoring(script: GeneratedScript): OptimizedScriptExecutor.ScriptResult {
        return scriptExecutor.executeScript(script.content, script.name)
    }
    
    private fun generateActionSuggestions(analysis: UIAnalysisResult): List<ActionSuggestion> {
        return analysis.elements.mapNotNull { element ->
            when {
                element.isClickable && element.text?.isNotEmpty() == true -> {
                    ActionSuggestion(
                        action = "click",
                        element = element,
                        description = "点击 ${element.text}",
                        confidence = element.confidence
                    )
                }
                element.type == ElementType.EDIT_TEXT -> {
                    ActionSuggestion(
                        action = "input",
                        element = element,
                        description = "在输入框中输入文本",
                        confidence = element.confidence
                    )
                }
                element.isScrollable -> {
                    ActionSuggestion(
                        action = "scroll",
                        element = element,
                        description = "滚动列表",
                        confidence = element.confidence
                    )
                }
                else -> null
            }
        }.sortedByDescending { it.confidence }
    }
    
    private fun generateUIOperationCode(analysis: UIAnalysisResult): String {
        return buildString {
            appendLine("// 自动生成的UI操作代码")
            appendLine("// 生成时间: ${java.util.Date()}")
            appendLine()
            
            analysis.elements.forEach { element ->
                when {
                    element.isClickable && element.text?.isNotEmpty() == true -> {
                        appendLine("// 点击元素: ${element.text}")
                        appendLine("click(\"${element.text}\");")
                        appendLine()
                    }
                    element.type == ElementType.EDIT_TEXT -> {
                        appendLine("// 输入文本到输入框")
                        appendLine("setText(\"your_text_here\");")
                        appendLine()
                    }
                }
            }
        }
    }
    
    private fun generateErrorSuggestions(error: Throwable): List<String> {
        return when (error) {
            is SecurityException -> listOf(
                "检查应用权限设置",
                "确保无障碍服务已启用",
                "检查悬浮窗权限"
            )
            is OutOfMemoryError -> listOf(
                "尝试减少并发任务",
                "清理不必要的资源",
                "重启应用"
            )
            else -> listOf(
                "检查网络连接",
                "重试操作",
                "查看详细错误日志"
            )
        }
    }
    
    private fun getActiveSessionsCount(): Int {
        // 实现获取活跃会话数量的逻辑
        return 0
    }
    
    private fun calculateImprovement(original: String, optimized: String): Float {
        // 简单的改进估算，实际应该更复杂
        val originalLines = original.lines().size
        val optimizedLines = optimized.lines().size
        return if (originalLines > 0) {
            (originalLines - optimizedLines).toFloat() / originalLines * 100f
        } else 0f
    }
    
    private fun updateAgentStatus() {
        agentScope.launch {
            _agentStatus.postValue(getAgentStatus())
        }
    }
    
    private fun recordExecution(sessionId: String, record: ExecutionRecord) {
        activeSessions[sessionId]?.let { session ->
            // 这里可以添加学习逻辑，分析用户行为模式
            learningModule.recordExecution(record)
        }
    }
    
    private fun generateExecutionId(): String = "exec_${System.currentTimeMillis()}_${(1000..9999).random()}"
}

/**
 * Agent执行选项
 */
data class AgentExecutionOptions(
    val autoExecute: Boolean = false,          // 是否自动执行生成的脚本
    val requireUIAnalysis: Boolean = true,     // 是否需要UI分析
    val scriptOptions: ScriptGenerationOptions = ScriptGenerationOptions(),
    val timeout: Long = 30000L                 // 超时时间(毫秒)
)

/**
 * 脚本生成选项
 */
data class ScriptGenerationOptions(
    val addComments: Boolean = true,           // 添加注释
    val addErrorHandling: Boolean = true,      // 添加错误处理
    val optimizePerformance: Boolean = true,   // 性能优化
    val includeWaitMechanisms: Boolean = true  // 包含等待机制
)

/**
 * Agent异常
 */
class AgentException(message: String, cause: Throwable? = null) : Exception(message, cause)