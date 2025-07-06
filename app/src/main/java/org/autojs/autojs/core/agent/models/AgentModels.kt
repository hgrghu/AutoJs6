package org.autojs.autojs.core.agent.models

import android.graphics.Bitmap
import android.graphics.Rect
import org.autojs.autojs.core.memory.MemoryMonitor
import org.autojs.autojs.core.script.OptimizedScriptExecutor

/**
 * Agent系统相关的数据模型
 */

// =============== 任务相关模型 ===============

/**
 * 任务定义
 */
data class Task(
    val id: String,
    val type: TaskType,
    val description: String,
    val priority: Priority = Priority.NORMAL,
    val context: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 任务类型
 */
enum class TaskType {
    UI_AUTOMATION,      // UI自动化操作
    DATA_EXTRACTION,    // 数据提取
    APP_INTERACTION,    // 应用间交互
    SYSTEM_OPERATION,   // 系统操作
    TEXT_INPUT,         // 文本输入
    IMAGE_PROCESSING,   // 图像处理
    CUSTOM             // 自定义任务
}

/**
 * 优先级
 */
enum class Priority {
    LOW, NORMAL, HIGH, URGENT
}

/**
 * 任务计划
 */
data class TaskPlan(
    val taskId: String,
    val steps: List<TaskStep>,
    val estimatedDuration: Long,
    val requiredPermissions: List<String>,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 任务步骤
 */
data class TaskStep(
    val id: String,
    val type: StepType,
    val action: String,
    val parameters: Map<String, Any>,
    val description: String,
    val estimatedDuration: Long = 1000L,
    val isOptional: Boolean = false
)

/**
 * 步骤类型
 */
enum class StepType {
    WAIT,           // 等待
    CLICK,          // 点击
    INPUT,          // 输入
    SCROLL,         // 滚动
    SWIPE,          // 滑动
    CAPTURE,        // 截图
    ANALYZE,        // 分析
    CONDITIONAL,    // 条件判断
    LOOP,           // 循环
    CUSTOM          // 自定义
}

// =============== UI分析相关模型 ===============

/**
 * UI分析结果
 */
data class UIAnalysisResult(
    val elements: List<UIElement>,
    val layout: LayoutInfo,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val screenSize: Pair<Int, Int>? = null
)

/**
 * UI元素
 */
data class UIElement(
    val id: String,
    val type: ElementType,
    val bounds: Rect,
    val text: String? = null,
    val description: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEditable: Boolean = false,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
    val confidence: Float = 1.0f,
    val children: List<UIElement> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * UI元素类型
 */
enum class ElementType {
    BUTTON,
    TEXT_VIEW,
    EDIT_TEXT,
    IMAGE_VIEW,
    IMAGE_BUTTON,
    CHECK_BOX,
    RADIO_BUTTON,
    SWITCH,
    PROGRESS_BAR,
    SEEK_BAR,
    LIST_VIEW,
    RECYCLER_VIEW,
    SCROLL_VIEW,
    TAB_LAYOUT,
    TOOLBAR,
    MENU,
    DIALOG,
    POPUP,
    CUSTOM,
    UNKNOWN
}

/**
 * 布局信息
 */
data class LayoutInfo(
    val type: LayoutType,
    val orientation: Orientation,
    val density: Float,
    val screenOrientation: ScreenOrientation
)

enum class LayoutType {
    LINEAR, RELATIVE, CONSTRAINT, FRAME, GRID, CUSTOM
}

enum class Orientation {
    HORIZONTAL, VERTICAL
}

enum class ScreenOrientation {
    PORTRAIT, LANDSCAPE
}

/**
 * UI分析报告
 */
data class UIAnalysisReport(
    val screenshot: Bitmap,
    val elements: List<UIElement>,
    val suggestedActions: List<ActionSuggestion>,
    val generatedCode: String,
    val confidence: Float,
    val timestamp: Long
)

/**
 * 操作建议
 */
data class ActionSuggestion(
    val action: String,
    val element: UIElement,
    val description: String,
    val confidence: Float,
    val code: String? = null
)

// =============== 脚本生成相关模型 ===============

/**
 * 生成的脚本
 */
data class GeneratedScript(
    val name: String,
    val content: String,
    val language: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    val metadata: ScriptMetadata,
    val estimatedExecutionTime: Long = 0L
)

/**
 * 脚本语言
 */
enum class ScriptLanguage {
    JAVASCRIPT, TYPESCRIPT
}

/**
 * 脚本元数据
 */
data class ScriptMetadata(
    val generatedBy: String,
    val timestamp: Long,
    val version: String,
    val dependencies: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val description: String? = null
)

/**
 * 优化的脚本
 */
data class OptimizedScript(
    val originalScript: String,
    val optimizedScript: String,
    val optimizations: List<OptimizationSuggestion>,
    val estimatedImprovement: Float
)

/**
 * 优化建议
 */
data class OptimizationSuggestion(
    val type: OptimizationType,
    val description: String,
    val impact: ImpactLevel,
    val code: String? = null
)

enum class OptimizationType {
    PERFORMANCE,
    MEMORY,
    READABILITY,
    ERROR_HANDLING,
    BEST_PRACTICE
}

enum class ImpactLevel {
    LOW, MEDIUM, HIGH
}

// =============== Agent执行结果 ===============

/**
 * Agent执行结果
 */
sealed class AgentResult {
    data class Success(
        val sessionId: String,
        val task: Task,
        val script: GeneratedScript,
        val executionResult: OptimizedScriptExecutor.ScriptResult?,
        val uiAnalysis: UIAnalysisResult?,
        val executionTime: Long = System.currentTimeMillis()
    ) : AgentResult()
    
    data class Error(
        val sessionId: String,
        val error: Throwable,
        val errorCode: String? = null,
        val suggestions: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    ) : AgentResult()
}

// =============== Agent状态 ===============

/**
 * Agent状态
 */
data class AgentStatus(
    val isInitialized: Boolean,
    val localModelsStatus: ModelsStatus,
    val cloudServiceStatus: ServiceStatus,
    val memoryUsage: MemoryMonitor.MemoryInfo,
    val activeSessionsCount: Int,
    val lastActivity: Long = System.currentTimeMillis()
)

/**
 * 模型状态
 */
data class ModelsStatus(
    val isLoaded: Boolean,
    val loadedModels: List<String>,
    val failedModels: List<String>,
    val totalMemoryUsage: Long
)

/**
 * 服务状态
 */
data class ServiceStatus(
    val isAvailable: Boolean,
    val isConnected: Boolean,
    val lastCheck: Long,
    val errorMessage: String? = null
)

// =============== 学习相关模型 ===============

/**
 * 学习会话
 */
data class LearningSession(
    val id: String,
    val startTime: Long,
    val isActive: Boolean,
    val recordedActions: MutableList<UserAction> = mutableListOf()
)

/**
 * 用户操作
 */
data class UserAction(
    val type: ActionType,
    val target: String,
    val parameters: Map<String, Any>,
    val timestamp: Long,
    val duration: Long = 0L,
    val success: Boolean = true
)

enum class ActionType {
    CLICK, LONG_CLICK, SWIPE, SCROLL, INPUT, KEY_PRESS, GESTURE
}

/**
 * 执行记录
 */
data class ExecutionRecord(
    val id: String,
    val task: Task,
    val script: GeneratedScript,
    val result: OptimizedScriptExecutor.ScriptResult,
    val executionTime: Long,
    val timestamp: Long,
    val userFeedback: UserFeedback? = null
)

/**
 * 用户反馈
 */
data class UserFeedback(
    val rating: Int, // 1-5
    val comments: String?,
    val improvements: List<String> = emptyList()
)

/**
 * 行为模式
 */
data class Pattern(
    val id: String,
    val name: String,
    val actions: List<UserAction>,
    val frequency: Int,
    val confidence: Float,
    val lastSeen: Long
)

// =============== AI模型相关 ===============

/**
 * 模型输入
 */
sealed class ModelInput {
    data class ImageInput(val bitmap: Bitmap) : ModelInput()
    data class TextInput(val text: String) : ModelInput()
    data class CompositeInput(val inputs: Map<String, Any>) : ModelInput()
}

/**
 * 模型输出
 */
sealed class ModelOutput {
    data class UIDetectionOutput(val elements: List<UIElement>) : ModelOutput()
    data class TextOutput(val text: String, val confidence: Float) : ModelOutput()
    data class ClassificationOutput(val classes: List<Classification>) : ModelOutput()
    data class CompositeOutput(val outputs: Map<String, Any>) : ModelOutput()
}

/**
 * 分类结果
 */
data class Classification(
    val label: String,
    val confidence: Float,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 模型信息
 */
data class ModelInfo(
    val name: String,
    val version: String,
    val type: ModelType,
    val inputFormat: String,
    val outputFormat: String,
    val size: Long,
    val accuracy: Float? = null
)

enum class ModelType {
    UI_DETECTION,
    TEXT_RECOGNITION,
    INTENT_CLASSIFICATION,
    IMAGE_CLASSIFICATION,
    OBJECT_DETECTION,
    CUSTOM
}

// =============== 意图理解 ===============

/**
 * 用户意图
 */
data class Intent(
    val type: IntentType,
    val confidence: Float,
    val parameters: Map<String, Any>,
    val entities: List<Entity>
)

enum class IntentType {
    CLICK_ELEMENT,
    INPUT_TEXT,
    NAVIGATE,
    EXTRACT_DATA,
    AUTOMATE_TASK,
    QUERY_INFO,
    CONTROL_APP,
    UNKNOWN
}

/**
 * 实体
 */
data class Entity(
    val type: EntityType,
    val value: String,
    val confidence: Float,
    val position: IntRange? = null
)

enum class EntityType {
    APP_NAME,
    UI_ELEMENT,
    TEXT_CONTENT,
    NUMBER,
    DATE,
    TIME,
    COLOR,
    COORDINATE,
    DURATION,
    CUSTOM
}

// =============== AI模型配置 ===============

/**
 * 模型提供商
 */
enum class ModelProvider(val displayName: String, val defaultBaseUrl: String) {
    OPENAI("OpenAI GPT", "https://api.openai.com"),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com"),
    GOOGLE("Google Gemini", "https://generativelanguage.googleapis.com"),
    CUSTOM("Custom Model", "")
}

/**
 * AI模型配置
 */
data class AIModelConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val provider: ModelProvider,
    val modelName: String,
    val baseUrl: String,
    val apiKey: String,
    val maxTokens: Int = 1000,
    val temperature: Float = 0.7f,
    val requestTimeout: Int = 60,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDisplayString(): String = "${provider.displayName} - $name"
    
    fun isValid(): Boolean {
        return name.isNotBlank() && 
               modelName.isNotBlank() && 
               baseUrl.isNotBlank() && 
               apiKey.isNotBlank()
    }
    
    companion object {
        fun createDefaultOpenAI(): AIModelConfig {
            return AIModelConfig(
                name = "GPT-4",
                provider = ModelProvider.OPENAI,
                modelName = "gpt-4",
                baseUrl = ModelProvider.OPENAI.defaultBaseUrl,
                apiKey = "",
                maxTokens = 2000,
                temperature = 0.7f
            )
        }
        
        fun createDefaultClaude(): AIModelConfig {
            return AIModelConfig(
                name = "Claude-3.5-Sonnet",
                provider = ModelProvider.ANTHROPIC,
                modelName = "claude-3-5-sonnet-20241022",
                baseUrl = ModelProvider.ANTHROPIC.defaultBaseUrl,
                apiKey = "",
                maxTokens = 1000,
                temperature = 0.7f
            )
        }
        
        fun createDefaultGemini(): AIModelConfig {
            return AIModelConfig(
                name = "Gemini-Pro",
                provider = ModelProvider.GOOGLE,
                modelName = "gemini-pro",
                baseUrl = ModelProvider.GOOGLE.defaultBaseUrl,
                apiKey = "",
                maxTokens = 1000,
                temperature = 0.7f
            )
        }
    }
}

/**
 * Agent执行选项
 */
data class AgentExecutionOptions(
    val autoExecute: Boolean = false,
    val requireUIAnalysis: Boolean = true,
    val modelConfig: AIModelConfig? = null,
    val maxRetries: Int = 3,
    val timeout: Long = 60000L,
    val enableLearning: Boolean = false
)