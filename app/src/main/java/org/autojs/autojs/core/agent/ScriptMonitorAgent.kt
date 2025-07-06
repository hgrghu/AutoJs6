package org.autojs.autojs.core.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import org.autojs.autojs.core.agent.models.*
import org.autojs.autojs.core.script.OptimizedScriptExecutor
import org.autojs.autojs.core.accessibility.AccessibilityTool
import java.util.concurrent.ConcurrentHashMap

/**
 * 脚本监控Agent - 智能脚本执行监控和自动修复
 * 
 * 功能：
 * 1. 监控用户脚本执行状态
 * 2. 检测执行失败并分析原因
 * 3. 自动截图和UI元素检测
 * 4. 基于AI分析重新优化代码
 * 5. 自动修正坐标和选择器
 */
class ScriptMonitorAgent private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: ScriptMonitorAgent? = null

        fun getInstance(context: Context): ScriptMonitorAgent {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScriptMonitorAgent(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val TAG = "ScriptMonitorAgent"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val SCREENSHOT_DELAY = 1000L
    }

    // 核心组件
    private val cloudAIService = CloudAIService(context)
    private val uiAnalyzer = UIAnalyzerImpl(context, LocalAIModels(context))
    private val autoJs6Agent = AutoJs6Agent.getInstance(context)
    private val accessibilityTool = AccessibilityTool(context)

    // 监控状态
    private val _monitoringStatus = MutableLiveData<MonitoringStatus>()
    val monitoringStatus: LiveData<MonitoringStatus> = _monitoringStatus

    // 活跃的监控会话
    private val activeMonitoringSessions = ConcurrentHashMap<String, MonitoringSession>()
    
    // 协程作用域
    private val monitorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 启动脚本监控
     */
    fun startScriptMonitoring(
        scriptContent: String,
        userReference: UserReference,
        options: MonitoringOptions = MonitoringOptions()
    ): String {
        val sessionId = generateSessionId()
        
        val session = MonitoringSession(
            id = sessionId,
            originalScript = scriptContent,
            userReference = userReference,
            options = options,
            startTime = System.currentTimeMillis(),
            status = SessionStatus.MONITORING
        )
        
        activeMonitoringSessions[sessionId] = session
        
        monitorScope.launch {
            executeScriptWithMonitoring(session)
        }
        
        return sessionId
    }

    /**
     * 停止脚本监控
     */
    fun stopScriptMonitoring(sessionId: String) {
        activeMonitoringSessions[sessionId]?.let { session ->
            activeMonitoringSessions[sessionId] = session.copy(
                status = SessionStatus.STOPPED,
                endTime = System.currentTimeMillis()
            )
        }
    }

    /**
     * 获取监控会话信息
     */
    fun getMonitoringSession(sessionId: String): MonitoringSession? {
        return activeMonitoringSessions[sessionId]
    }

    /**
     * 获取所有活跃会话
     */
    fun getActiveSessions(): List<MonitoringSession> {
        return activeMonitoringSessions.values.filter { 
            it.status == SessionStatus.MONITORING || it.status == SessionStatus.ANALYZING 
        }
    }

    // =============== 私有方法 ===============

    /**
     * 执行脚本并监控
     */
    private suspend fun executeScriptWithMonitoring(session: MonitoringSession) {
        var currentScript = session.originalScript
        var attemptCount = 0
        
        while (attemptCount < MAX_RETRY_ATTEMPTS && 
               session.status == SessionStatus.MONITORING) {
            
            attemptCount++
            updateSessionStatus(session.id, SessionStatus.EXECUTING)
            
            try {
                // 执行脚本前截图
                val beforeScreenshot = captureScreenshot()
                
                // 执行脚本
                val executionResult = executeScript(currentScript)
                
                if (executionResult.isSuccess) {
                    // 脚本执行成功
                    handleSuccessfulExecution(session, executionResult, attemptCount)
                    break
                } else {
                    // 脚本执行失败，开始分析和修复
                    currentScript = handleExecutionFailure(
                        session = session,
                        failedScript = currentScript,
                        executionResult = executionResult,
                        beforeScreenshot = beforeScreenshot,
                        attemptCount = attemptCount
                    )
                }
                
            } catch (e: Exception) {
                // 执行异常
                handleExecutionException(session, e, attemptCount)
                if (attemptCount >= MAX_RETRY_ATTEMPTS) break
            }
            
            // 等待一段时间再重试
            delay(2000)
        }
        
        // 标记会话结束
        updateSessionStatus(session.id, SessionStatus.COMPLETED)
    }

    /**
     * 处理脚本执行成功
     */
    private suspend fun handleSuccessfulExecution(
        session: MonitoringSession,
        result: OptimizedScriptExecutor.ScriptResult,
        attemptCount: Int
    ) {
        val updatedSession = session.copy(
            status = SessionStatus.COMPLETED,
            endTime = System.currentTimeMillis(),
            finalScript = session.currentScript ?: session.originalScript,
            executionAttempts = attemptCount,
            isSuccessful = true
        )
        
        activeMonitoringSessions[session.id] = updatedSession
        
        // 如果脚本被修改过，保存优化后的版本
        if (session.modifications.isNotEmpty()) {
            saveOptimizedScript(session)
        }
        
        updateMonitoringStatus(
            MonitoringStatus.success(
                sessionId = session.id,
                message = "脚本执行成功${if (attemptCount > 1) "，经过${attemptCount}次优化" else ""}",
                executionTime = System.currentTimeMillis() - session.startTime
            )
        )
    }

    /**
     * 处理脚本执行失败
     */
    private suspend fun handleExecutionFailure(
        session: MonitoringSession,
        failedScript: String,
        executionResult: OptimizedScriptExecutor.ScriptResult,
        beforeScreenshot: Bitmap?,
        attemptCount: Int
    ): String {
        updateSessionStatus(session.id, SessionStatus.ANALYZING)
        
        // 执行失败后截图
        delay(SCREENSHOT_DELAY)
        val afterScreenshot = captureScreenshot()
        
        // 分析当前屏幕状态
        val currentUIAnalysis = uiAnalyzer.analyzeCurrentScreen()
        
        // 分析失败原因
        val failureAnalysis = analyzeExecutionFailure(
            script = failedScript,
            executionResult = executionResult,
            beforeScreenshot = beforeScreenshot,
            afterScreenshot = afterScreenshot,
            uiAnalysis = currentUIAnalysis,
            userReference = session.userReference
        )
        
        // 生成修复后的脚本
        val fixedScript = generateFixedScript(
            originalScript = failedScript,
            failureAnalysis = failureAnalysis,
            uiAnalysis = currentUIAnalysis,
            userReference = session.userReference,
            attemptCount = attemptCount
        )
        
        // 记录修改
        val modification = ScriptModification(
            attemptNumber = attemptCount,
            originalCode = failedScript,
            modifiedCode = fixedScript,
            reason = failureAnalysis.primaryCause,
            changes = failureAnalysis.suggestedFixes,
            timestamp = System.currentTimeMillis()
        )
        
        val updatedSession = session.copy(
            currentScript = fixedScript,
            modifications = session.modifications + modification
        )
        activeMonitoringSessions[session.id] = updatedSession
        
        updateMonitoringStatus(
            MonitoringStatus.analyzing(
                sessionId = session.id,
                message = "第${attemptCount}次执行失败，正在分析和修复...",
                failureReason = failureAnalysis.primaryCause
            )
        )
        
        return fixedScript
    }

    /**
     * 分析脚本执行失败原因
     */
    private suspend fun analyzeExecutionFailure(
        script: String,
        executionResult: OptimizedScriptExecutor.ScriptResult,
        beforeScreenshot: Bitmap?,
        afterScreenshot: Bitmap?,
        uiAnalysis: UIAnalysisResult,
        userReference: UserReference
    ): FailureAnalysis {
        
        // 构建分析提示
        val analysisPrompt = buildFailureAnalysisPrompt(
            script = script,
            errorMessage = executionResult.errorMessage ?: "未知错误",
            uiElements = uiAnalysis.elements,
            userReference = userReference
        )
        
        // 使用AI分析失败原因
        val modelConfig = getDefaultModelConfig()
        val response = cloudAIService.callModel(
            modelConfig = modelConfig,
            prompt = analysisPrompt,
            systemPrompt = getFailureAnalysisSystemPrompt(),
            maxTokens = 1500,
            temperature = 0.3f
        )
        
        return if (response.isSuccess) {
            parseFailureAnalysisResponse(response.content)
        } else {
            // AI分析失败，使用规则基础分析
            performRuleBasedFailureAnalysis(script, executionResult, uiAnalysis)
        }
    }

    /**
     * 生成修复后的脚本
     */
    private suspend fun generateFixedScript(
        originalScript: String,
        failureAnalysis: FailureAnalysis,
        uiAnalysis: UIAnalysisResult,
        userReference: UserReference,
        attemptCount: Int
    ): String {
        
        val fixPrompt = buildScriptFixPrompt(
            originalScript = originalScript,
            failureAnalysis = failureAnalysis,
            uiElements = uiAnalysis.elements,
            userReference = userReference,
            attemptCount = attemptCount
        )
        
        val modelConfig = getDefaultModelConfig()
        val response = cloudAIService.callModel(
            modelConfig = modelConfig,
            prompt = fixPrompt,
            systemPrompt = getScriptFixSystemPrompt(),
            maxTokens = 2000,
            temperature = 0.2f
        )
        
        return if (response.isSuccess) {
            cleanupGeneratedScript(response.content)
        } else {
            // AI修复失败，使用规则基础修复
            performRuleBasedScriptFix(originalScript, failureAnalysis, uiAnalysis)
        }
    }

    /**
     * 构建失败分析提示
     */
    private fun buildFailureAnalysisPrompt(
        script: String,
        errorMessage: String,
        uiElements: List<UIElement>,
        userReference: UserReference
    ): String = buildString {
        appendLine("请分析以下AutoJs6脚本执行失败的原因：")
        appendLine()
        appendLine("执行的脚本：")
        appendLine("```javascript")
        appendLine(script)
        appendLine("```")
        appendLine()
        appendLine("错误信息：$errorMessage")
        appendLine()
        appendLine("当前屏幕UI元素（前10个）：")
        uiElements.take(10).forEach { element ->
            appendLine("- ${element.type}: ${element.text ?: element.description ?: "无文本"} " +
                      "位置:(${element.bounds.left},${element.bounds.top}) " +
                      "可点击:${element.isClickable}")
        }
        appendLine()
        
        when (userReference) {
            is UserReference.TextDescription -> {
                appendLine("用户期望行为：${userReference.description}")
            }
            is UserReference.ImageReference -> {
                appendLine("用户提供了参考图片，期望找到相似的UI元素")
            }
            is UserReference.Mixed -> {
                appendLine("用户期望行为：${userReference.description}")
                appendLine("用户提供了参考图片作为辅助")
            }
        }
        appendLine()
        appendLine("请分析失败原因并提供修复建议。")
    }

    /**
     * 构建脚本修复提示
     */
    private fun buildScriptFixPrompt(
        originalScript: String,
        failureAnalysis: FailureAnalysis,
        uiElements: List<UIElement>,
        userReference: UserReference,
        attemptCount: Int
    ): String = buildString {
        appendLine("请修复以下AutoJs6脚本：")
        appendLine()
        appendLine("原始脚本：")
        appendLine("```javascript")
        appendLine(originalScript)
        appendLine("```")
        appendLine()
        appendLine("失败原因：${failureAnalysis.primaryCause}")
        appendLine("建议修复：")
        failureAnalysis.suggestedFixes.forEach { fix ->
            appendLine("- $fix")
        }
        appendLine()
        appendLine("当前可用的UI元素：")
        uiElements.filter { it.isClickable || it.isEditable }.take(10).forEach { element ->
            appendLine("- ${element.type}: ${element.text ?: element.description ?: "无文本"}")
            appendLine("  选择器建议: ${generateSelectorSuggestion(element)}")
            appendLine("  坐标: (${element.bounds.centerX()}, ${element.bounds.centerY()})")
        }
        appendLine()
        appendLine("这是第${attemptCount}次修复，请生成更准确的脚本。只返回修复后的JavaScript代码。")
    }

    /**
     * 生成选择器建议
     */
    private fun generateSelectorSuggestion(element: UIElement): String {
        val suggestions = mutableListOf<String>()
        
        element.text?.let { text ->
            if (text.isNotBlank()) {
                suggestions.add("text(\"$text\")")
                if (text.length > 10) {
                    suggestions.add("textContains(\"${text.take(8)}\")")
                }
            }
        }
        
        element.description?.let { desc ->
            if (desc.isNotBlank()) {
                suggestions.add("desc(\"$desc\")")
            }
        }
        
        element.resourceId?.let { id ->
            if (id.isNotBlank()) {
                suggestions.add("id(\"${id.substringAfterLast(':')}\")")
            }
        }
        
        element.className?.let { className ->
            val simpleClassName = className.substringAfterLast('.')
            suggestions.add("className(\"$simpleClassName\")")
        }
        
        return suggestions.joinToString(" 或 ")
    }

    /**
     * 规则基础的失败分析
     */
    private fun performRuleBasedFailureAnalysis(
        script: String,
        executionResult: OptimizedScriptExecutor.ScriptResult,
        uiAnalysis: UIAnalysisResult
    ): FailureAnalysis {
        val errorMessage = executionResult.errorMessage ?: ""
        
        val primaryCause = when {
            errorMessage.contains("找不到") || errorMessage.contains("null") -> "UI元素未找到"
            errorMessage.contains("坐标") || errorMessage.contains("bounds") -> "坐标位置错误"
            errorMessage.contains("权限") -> "权限不足"
            errorMessage.contains("超时") -> "操作超时"
            script.contains("click(") && !script.contains("findOne") -> "缺少元素查找"
            else -> "脚本逻辑错误"
        }
        
        val suggestedFixes = when (primaryCause) {
            "UI元素未找到" -> listOf(
                "使用更准确的选择器",
                "添加等待时间",
                "检查界面是否已加载完成"
            )
            "坐标位置错误" -> listOf(
                "使用动态元素查找替代固定坐标",
                "更新坐标位置",
                "添加坐标有效性检查"
            )
            "权限不足" -> listOf(
                "检查无障碍权限",
                "添加权限检查代码"
            )
            else -> listOf("优化脚本逻辑", "添加错误处理")
        }
        
        return FailureAnalysis(
            primaryCause = primaryCause,
            suggestedFixes = suggestedFixes,
            confidence = 0.7f
        )
    }

    /**
     * 规则基础的脚本修复
     */
    private fun performRuleBasedScriptFix(
        originalScript: String,
        failureAnalysis: FailureAnalysis,
        uiAnalysis: UIAnalysisResult
    ): String {
        var fixedScript = originalScript
        
        // 替换固定坐标为动态查找
        if (fixedScript.contains("click(") && fixedScript.contains(Regex("\\d+,\\s*\\d+"))) {
            val availableElements = uiAnalysis.elements.filter { it.isClickable && !it.text.isNullOrBlank() }
            if (availableElements.isNotEmpty()) {
                val element = availableElements.first()
                val selector = element.text?.let { "text(\"$it\")" } 
                    ?: element.description?.let { "desc(\"$it\")" }
                    ?: "className(\"${element.className?.substringAfterLast('.') ?: "View"}\")"
                
                fixedScript = fixedScript.replace(
                    Regex("click\\s*\\(\\s*\\d+\\s*,\\s*\\d+\\s*\\)"),
                    "$selector.findOne().click()"
                )
            }
        }
        
        // 添加等待时间
        if (!fixedScript.contains("sleep") && !fixedScript.contains("waitFor")) {
            fixedScript = "sleep(1000);\n$fixedScript"
        }
        
        // 添加错误处理
        if (!fixedScript.contains("try") && !fixedScript.contains("findOne()")) {
            fixedScript = fixedScript.replace(
                Regex("(\\w+)\\s*\\.click\\(\\)"),
                "let element = $1.findOne();\nif (element) {\n    element.click();\n} else {\n    toast('未找到元素');\n}"
            )
        }
        
        return fixedScript
    }

    /**
     * 解析失败分析响应
     */
    private fun parseFailureAnalysisResponse(response: String): FailureAnalysis {
        // 简单解析AI响应，实际应该使用更复杂的解析逻辑
        val lines = response.lines()
        val primaryCause = lines.find { it.contains("原因") || it.contains("问题") }
            ?.substringAfter("：")?.trim() ?: "未知原因"
        
        val suggestedFixes = lines.filter { 
            it.trim().startsWith("-") || it.trim().startsWith("•") || it.trim().startsWith("*")
        }.map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
        
        return FailureAnalysis(
            primaryCause = primaryCause,
            suggestedFixes = suggestedFixes.ifEmpty { listOf("请检查脚本逻辑") },
            confidence = 0.8f
        )
    }

    // =============== 辅助方法 ===============

    private suspend fun captureScreenshot(): Bitmap? {
        return try {
            // 实现截图功能
            // 这里需要使用MediaProjection或无障碍服务
            null // TODO: 实现截图
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun executeScript(script: String): OptimizedScriptExecutor.ScriptResult {
        val executor = OptimizedScriptExecutor()
        return executor.executeScript(script)
    }

    private fun updateSessionStatus(sessionId: String, status: SessionStatus) {
        activeMonitoringSessions[sessionId]?.let { session ->
            activeMonitoringSessions[sessionId] = session.copy(status = status)
        }
    }

    private fun updateMonitoringStatus(status: MonitoringStatus) {
        _monitoringStatus.postValue(status)
    }

    private fun getDefaultModelConfig(): AIModelConfig {
        // 获取用户设置的默认模型，或使用OpenAI
        return AIModelConfig.createDefaultOpenAI()
    }

    private fun getFailureAnalysisSystemPrompt(): String = """
        你是AutoJs6脚本调试专家。请分析脚本执行失败的原因并提供具体的修复建议。
        
        重点关注：
        1. UI元素选择器是否正确
        2. 坐标是否准确
        3. 等待时间是否充足
        4. 权限是否足够
        5. 脚本逻辑是否合理
        
        请简洁明确地指出主要原因和具体的修复建议。
    """.trimIndent()

    private fun getScriptFixSystemPrompt(): String = """
        你是AutoJs6脚本修复专家。根据失败分析和当前UI状态，修复脚本中的问题。
        
        修复原则：
        1. 优先使用text()、desc()等选择器而非坐标
        2. 添加适当的等待时间
        3. 增加错误处理和容错机制
        4. 使用findOne()前检查元素是否存在
        5. 保持代码简洁可读
        
        只返回修复后的JavaScript代码，不要包含其他说明。
    """.trimIndent()

    private fun cleanupGeneratedScript(content: String): String {
        return content
            .replace("```javascript", "")
            .replace("```js", "")
            .replace("```", "")
            .trim()
    }

    private fun saveOptimizedScript(session: MonitoringSession) {
        // TODO: 保存优化后的脚本到文件或数据库
    }

    private fun generateSessionId(): String = 
        "monitor_${System.currentTimeMillis()}_${(1000..9999).random()}"

    private fun handleExecutionException(
        session: MonitoringSession, 
        exception: Exception, 
        attemptCount: Int
    ) {
        updateMonitoringStatus(
            MonitoringStatus.error(
                sessionId = session.id,
                error = exception,
                message = "第${attemptCount}次执行异常: ${exception.message}"
            )
        )
    }
}

// =============== 数据模型 ===============

/**
 * 用户参考信息
 */
sealed class UserReference {
    data class TextDescription(val description: String) : UserReference()
    data class ImageReference(val image: Bitmap, val description: String? = null) : UserReference()
    data class Mixed(val description: String, val image: Bitmap) : UserReference()
}

/**
 * 监控选项
 */
data class MonitoringOptions(
    val enableAutoFix: Boolean = true,
    val maxRetryAttempts: Int = 3,
    val screenshotOnFailure: Boolean = true,
    val saveOptimizedScript: Boolean = true,
    val modelConfig: AIModelConfig? = null
)

/**
 * 监控会话
 */
data class MonitoringSession(
    val id: String,
    val originalScript: String,
    val userReference: UserReference,
    val options: MonitoringOptions,
    val startTime: Long,
    val endTime: Long? = null,
    val status: SessionStatus,
    val currentScript: String? = null,
    val finalScript: String? = null,
    val modifications: List<ScriptModification> = emptyList(),
    val executionAttempts: Int = 0,
    val isSuccessful: Boolean = false
)

/**
 * 会话状态
 */
enum class SessionStatus {
    MONITORING, EXECUTING, ANALYZING, COMPLETED, STOPPED, ERROR
}

/**
 * 脚本修改记录
 */
data class ScriptModification(
    val attemptNumber: Int,
    val originalCode: String,
    val modifiedCode: String,
    val reason: String,
    val changes: List<String>,
    val timestamp: Long
)

/**
 * 失败分析结果
 */
data class FailureAnalysis(
    val primaryCause: String,
    val suggestedFixes: List<String>,
    val confidence: Float
)

/**
 * 监控状态
 */
sealed class MonitoringStatus {
    data class Success(
        val sessionId: String,
        val message: String,
        val executionTime: Long
    ) : MonitoringStatus()
    
    data class Analyzing(
        val sessionId: String,
        val message: String,
        val failureReason: String
    ) : MonitoringStatus()
    
    data class Error(
        val sessionId: String,
        val error: Throwable,
        val message: String
    ) : MonitoringStatus()
    
    companion object {
        fun success(sessionId: String, message: String, executionTime: Long) = 
            Success(sessionId, message, executionTime)
        
        fun analyzing(sessionId: String, message: String, failureReason: String) = 
            Analyzing(sessionId, message, failureReason)
        
        fun error(sessionId: String, error: Throwable, message: String) = 
            Error(sessionId, error, message)
    }
}