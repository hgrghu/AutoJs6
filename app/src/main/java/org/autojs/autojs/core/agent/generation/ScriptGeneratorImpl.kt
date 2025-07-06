package org.autojs.autojs.core.agent.generation

import org.autojs.autojs.core.agent.models.*
import org.autojs.autojs.core.agent.cloud.CloudAIService
import org.autojs.autojs.core.log.LogManager
import java.text.SimpleDateFormat
import java.util.*

class ScriptGeneratorImpl(
    private val localModels: LocalAIModels,
    private val cloudService: CloudAIService
) {
    
    companion object {
        private const val TAG = "ScriptGenerator"
    }
    
    suspend fun initialize() {
        LogManager.d(TAG, "脚本生成模块初始化完成")
    }
    
    suspend fun generateScript(
        taskPlan: TaskPlan,
        options: ScriptGenerationOptions
    ): GeneratedScript {
        LogManager.d(TAG, "生成脚本，步骤数量: ${taskPlan.steps.size}")
        
        val scriptContent = buildString {
            if (options.addComments) {
                appendLine("// AutoJs6 Agent 自动生成的脚本")
                appendLine("// 生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("// 任务ID: ${taskPlan.taskId}")
                appendLine()
            }
            
            if (options.addErrorHandling) {
                appendLine("// 权限检查")
                appendLine("if (!auto.service) {")
                appendLine("    toast('请先开启无障碍服务');")
                appendLine("    exit();")
                appendLine("}")
                appendLine()
            }
            
            appendLine("console.log('脚本开始执行');")
            appendLine()
            
            taskPlan.steps.forEach { step ->
                generateStepCode(step, options, this)
            }
            
            appendLine("console.log('脚本执行完成');")
        }
        
        return GeneratedScript(
            name = "agent_script_${System.currentTimeMillis()}",
            content = scriptContent,
            metadata = ScriptMetadata(
                generatedBy = "AutoJs6Agent",
                timestamp = System.currentTimeMillis(),
                version = "1.0.0",
                permissions = taskPlan.requiredPermissions,
                description = "Agent自动生成的脚本"
            ),
            estimatedExecutionTime = taskPlan.estimatedDuration
        )
    }
    
    suspend fun optimizeScript(
        script: String,
        suggestions: List<OptimizationSuggestion>
    ): String {
        // 尝试使用云端服务优化
        return try {
            cloudService.optimizeScript(script)
        } catch (e: Exception) {
            LogManager.w(TAG, "云端优化失败，使用本地优化: ${e.message}")
            applyLocalOptimizations(script, suggestions)
        }
    }
    
    private fun generateStepCode(
        step: TaskStep,
        options: ScriptGenerationOptions,
        builder: StringBuilder
    ) {
        if (options.addComments) {
            builder.appendLine("// ${step.description}")
        }
        
        when (step.type) {
            StepType.WAIT -> {
                val duration = step.parameters["duration"] as? Int ?: 1000
                builder.appendLine("sleep($duration);")
            }
            
            StepType.CLICK -> {
                val text = step.parameters["text"] as? String
                if (text?.isNotEmpty() == true) {
                    builder.appendLine("click('$text');")
                } else {
                    builder.appendLine("// TODO: 添加具体的点击操作")
                }
            }
            
            StepType.INPUT -> {
                val text = step.parameters["text"] as? String ?: "示例文本"
                builder.appendLine("setText('$text');")
            }
            
            StepType.SCROLL -> {
                builder.appendLine("scrollDown();")
            }
            
            StepType.CAPTURE -> {
                builder.appendLine("let img = captureScreen();")
                builder.appendLine("// TODO: 处理截图")
            }
            
            else -> {
                builder.appendLine("// TODO: 实现${step.action}")
            }
        }
        
        if (options.includeWaitMechanisms && step.type != StepType.WAIT) {
            builder.appendLine("sleep(500); // 等待操作完成")
        }
        
        builder.appendLine()
    }
    
    private fun applyLocalOptimizations(
        script: String,
        suggestions: List<OptimizationSuggestion>
    ): String {
        var optimizedScript = script
        
        suggestions.forEach { suggestion ->
            when (suggestion.type) {
                OptimizationType.PERFORMANCE -> {
                    // 简单的性能优化
                    optimizedScript = optimizedScript.replace("sleep(500)", "sleep(300)")
                }
                OptimizationType.ERROR_HANDLING -> {
                    // 添加更多错误处理
                }
                else -> {
                    // 其他优化
                }
            }
        }
        
        return optimizedScript
    }
}