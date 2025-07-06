package org.autojs.autojs.core.agent.understanding

import org.autojs.autojs.core.agent.models.*
import org.autojs.autojs.core.agent.cloud.CloudAIService
import org.autojs.autojs.core.log.LogManager
import java.util.UUID

class TaskUnderstandingImpl(
    private val localModels: LocalAIModels,
    private val cloudService: CloudAIService
) {
    
    companion object {
        private const val TAG = "TaskUnderstanding"
    }
    
    suspend fun initialize() {
        LogManager.d(TAG, "任务理解模块初始化完成")
    }
    
    suspend fun parseNaturalLanguage(input: String): Task {
        LogManager.d(TAG, "解析自然语言: $input")
        
        // 使用本地模型进行意图分类
        val intent = localModels.classifyIntent(input)
        
        val taskType = when (intent.type) {
            IntentType.CLICK_ELEMENT -> TaskType.UI_AUTOMATION
            IntentType.INPUT_TEXT -> TaskType.TEXT_INPUT
            IntentType.EXTRACT_DATA -> TaskType.DATA_EXTRACTION
            IntentType.NAVIGATE -> TaskType.APP_INTERACTION
            else -> TaskType.CUSTOM
        }
        
        return Task(
            id = UUID.randomUUID().toString(),
            type = taskType,
            description = input,
            context = mapOf(
                "intent" to intent,
                "confidence" to intent.confidence,
                "entities" to intent.entities
            )
        )
    }
    
    suspend fun generateTaskPlan(task: Task, uiAnalysis: UIAnalysisResult?): TaskPlan {
        LogManager.d(TAG, "生成任务计划: ${task.type}")
        
        val steps = mutableListOf<TaskStep>()
        
        when (task.type) {
            TaskType.UI_AUTOMATION -> {
                generateUIAutomationSteps(task, uiAnalysis, steps)
            }
            TaskType.TEXT_INPUT -> {
                generateTextInputSteps(task, uiAnalysis, steps)
            }
            TaskType.DATA_EXTRACTION -> {
                generateDataExtractionSteps(task, uiAnalysis, steps)
            }
            else -> {
                generateGenericSteps(task, steps)
            }
        }
        
        return TaskPlan(
            taskId = task.id,
            steps = steps,
            estimatedDuration = steps.sumOf { it.estimatedDuration },
            requiredPermissions = listOf("android.permission.SYSTEM_ALERT_WINDOW")
        )
    }
    
    private fun generateUIAutomationSteps(
        task: Task,
        uiAnalysis: UIAnalysisResult?,
        steps: MutableList<TaskStep>
    ) {
        steps.add(
            TaskStep(
                id = "wait_ui_${UUID.randomUUID()}",
                type = StepType.WAIT,
                action = "等待界面加载",
                parameters = mapOf("duration" to 2000),
                description = "等待界面完全加载",
                estimatedDuration = 2000
            )
        )
        
        uiAnalysis?.elements?.forEach { element ->
            if (element.isClickable) {
                steps.add(
                    TaskStep(
                        id = "click_${element.id}",
                        type = StepType.CLICK,
                        action = "点击元素",
                        parameters = mapOf(
                            "element" to element,
                            "text" to element.text,
                            "bounds" to element.bounds
                        ),
                        description = "点击${element.text ?: "元素"}",
                        estimatedDuration = 1000
                    )
                )
            }
        }
    }
    
    private fun generateTextInputSteps(
        task: Task,
        uiAnalysis: UIAnalysisResult?,
        steps: MutableList<TaskStep>
    ) {
        steps.add(
            TaskStep(
                id = "input_${UUID.randomUUID()}",
                type = StepType.INPUT,
                action = "输入文本",
                parameters = mapOf("text" to "示例文本"),
                description = "在输入框中输入文本",
                estimatedDuration = 2000
            )
        )
    }
    
    private fun generateDataExtractionSteps(
        task: Task,
        uiAnalysis: UIAnalysisResult?,
        steps: MutableList<TaskStep>
    ) {
        steps.add(
            TaskStep(
                id = "capture_${UUID.randomUUID()}",
                type = StepType.CAPTURE,
                action = "截图分析",
                parameters = emptyMap(),
                description = "截取屏幕进行数据提取",
                estimatedDuration = 3000
            )
        )
    }
    
    private fun generateGenericSteps(
        task: Task,
        steps: MutableList<TaskStep>
    ) {
        steps.add(
            TaskStep(
                id = "generic_${UUID.randomUUID()}",
                type = StepType.CUSTOM,
                action = "自定义操作",
                parameters = mapOf("description" to task.description),
                description = "执行自定义任务",
                estimatedDuration = 5000
            )
        )
    }
}