package org.autojs.autojs.agent.model

import android.graphics.Bitmap
import android.graphics.Rect
import java.util.Date

/**
 * 屏幕上下文信息
 */
data class ScreenContext(
    val screenShot: Bitmap?,
    val layoutInfo: LayoutInfo?,
    val elements: List<UIElement> = emptyList(),
    val appPackage: String? = null,
    val activity: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * UI元素信息
 */
data class UIElement(
    val id: String?,
    val text: String?,
    val className: String?,
    val packageName: String?,
    val bounds: Rect,
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisible: Boolean = true,
    val description: String? = null,
    val attributes: Map<String, Any> = emptyMap()
)

/**
 * 布局信息
 */
data class LayoutInfo(
    val rootElement: UIElement?,
    val allElements: List<UIElement> = emptyList(),
    val clickableElements: List<UIElement> = emptyList(),
    val textElements: List<UIElement> = emptyList(),
    val inputElements: List<UIElement> = emptyList(),
    val hierarchy: String? = null
)

/**
 * 屏幕数据
 */
data class ScreenData(
    val context: ScreenContext,
    val previousContext: ScreenContext? = null,
    val changes: List<ScreenChange> = emptyList()
)

/**
 * 屏幕变化
 */
data class ScreenChange(
    val type: ChangeType,
    val element: UIElement?,
    val oldValue: Any? = null,
    val newValue: Any? = null
) {
    enum class ChangeType {
        ELEMENT_ADDED,
        ELEMENT_REMOVED,
        ELEMENT_MODIFIED,
        TEXT_CHANGED,
        POSITION_CHANGED,
        VISIBILITY_CHANGED
    }
}

/**
 * 脚本优化结果
 */
data class OptimizationResult(
    val originalScript: String,
    val optimizedScript: String,
    val improvements: List<Improvement> = emptyList(),
    val score: Float = 0.0f,
    val suggestions: List<Suggestion> = emptyList(),
    val warnings: List<String> = emptyList(),
    val isSuccessful: Boolean = true
)

/**
 * 改进项
 */
data class Improvement(
    val type: ImprovementType,
    val description: String,
    val impact: Impact = Impact.MEDIUM,
    val before: String? = null,
    val after: String? = null
) {
    enum class ImprovementType {
        COORDINATE_OPTIMIZATION,
        SELECTOR_IMPROVEMENT,
        PERFORMANCE_ENHANCEMENT,
        ERROR_HANDLING,
        CODE_SIMPLIFICATION,
        COMPATIBILITY_FIX
    }
    
    enum class Impact {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}

/**
 * 脚本生成结果
 */
data class ScriptGenerationResult(
    val script: String,
    val explanation: String,
    val confidence: Float = 0.0f,
    val requiredPermissions: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val testCases: List<TestCase> = emptyList(),
    val isExecutable: Boolean = true
)

/**
 * 测试用例
 */
data class TestCase(
    val description: String,
    val input: Map<String, Any> = emptyMap(),
    val expectedOutput: Any? = null,
    val preconditions: List<String> = emptyList()
)

/**
 * 坐标优化
 */
data class CoordinateOptimization(
    val optimizations: List<CoordinateChange> = emptyList(),
    val score: Float = 0.0f,
    val alternativeSelectors: List<SelectorSuggestion> = emptyList()
)

/**
 * 坐标变更
 */
data class CoordinateChange(
    val originalCoordinate: Pair<Int, Int>,
    val optimizedSelector: String,
    val reason: String,
    val confidence: Float = 0.0f
)

/**
 * 选择器建议
 */
data class SelectorSuggestion(
    val selector: String,
    val type: SelectorType,
    val reliability: Float = 0.0f,
    val description: String
) {
    enum class SelectorType {
        ID, TEXT, CLASS, XPATH, CSS, ACCESSIBILITY_ID, COMPOUND
    }
}

/**
 * 对话消息
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val context: ScreenContext? = null,
    val attachments: List<Attachment> = emptyList()
) {
    enum class MessageRole {
        USER, ASSISTANT, SYSTEM
    }
}

/**
 * 附件
 */
data class Attachment(
    val type: AttachmentType,
    val content: Any,
    val description: String? = null
) {
    enum class AttachmentType {
        SCREENSHOT, SCRIPT, LAYOUT_INFO, ERROR_LOG
    }
}

/**
 * 聊天回复
 */
data class ChatResponse(
    val message: ChatMessage,
    val suggestedActions: List<Action> = emptyList(),
    val generatedScript: String? = null,
    val needsMoreInfo: Boolean = false,
    val clarifyingQuestions: List<String> = emptyList()
)

/**
 * 操作建议
 */
data class ActionSuggestion(
    val actions: List<Action> = emptyList(),
    val confidence: Float = 0.0f,
    val reasoning: String,
    val alternativeActions: List<Action> = emptyList()
)

/**
 * 动作
 */
data class Action(
    val type: ActionType,
    val target: UIElement? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val description: String,
    val script: String? = null
) {
    enum class ActionType {
        CLICK, LONG_CLICK, SWIPE, TYPE_TEXT, SCROLL, WAIT, CAPTURE_SCREEN, NAVIGATE_BACK
    }
}

/**
 * 验证结果
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<String> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    val executionTime: Long? = null
)

/**
 * 验证错误
 */
data class ValidationError(
    val line: Int? = null,
    val column: Int? = null,
    val message: String,
    val severity: Severity = Severity.ERROR,
    val code: String? = null
) {
    enum class Severity {
        INFO, WARNING, ERROR, CRITICAL
    }
}

/**
 * 执行结果
 */
data class ExecutionResult(
    val isSuccess: Boolean,
    val executionTime: Long,
    val output: String? = null,
    val error: Throwable? = null,
    val logs: List<String> = emptyList(),
    val screenChanges: List<ScreenChange> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 建议
 */
data class Suggestion(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val type: SuggestionType,
    val priority: Priority = Priority.MEDIUM,
    val actionScript: String? = null,
    val learnMoreUrl: String? = null
) {
    enum class SuggestionType {
        OPTIMIZATION, BUG_FIX, FEATURE_ENHANCEMENT, BEST_PRACTICE, SECURITY
    }
    
    enum class Priority {
        LOW, MEDIUM, HIGH, URGENT
    }
}

/**
 * 脚本模板
 */
data class ScriptTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val script: String,
    val author: String? = null,
    val version: String = "1.0",
    val tags: List<String> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
    val compatibility: List<String> = emptyList(),
    val usageCount: Int = 0,
    val rating: Float = 0.0f,
    val lastUpdated: Date = Date(),
    val isPublic: Boolean = false
)