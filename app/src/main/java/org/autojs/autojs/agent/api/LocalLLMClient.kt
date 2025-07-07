package org.autojs.autojs.agent.api

import org.autojs.autojs.agent.model.*

/**
 * 本地LLM客户端存根类
 */
class LocalLLMClient(private val modelPath: String) : AgentAPI {
    
    override suspend fun analyzeScript(script: String, context: ScreenContext): OptimizationResult {
        return OptimizationResult(
            originalScript = script,
            optimizedScript = script,
            isSuccessful = false,
            warnings = listOf("本地模型暂未实现")
        )
    }
    
    override suspend fun generateScript(request: String, context: ScreenContext): ScriptGenerationResult {
        return ScriptGenerationResult(
            script = "// 本地模型暂未实现",
            explanation = "本地模型功能待开发",
            confidence = 0.0f,
            isExecutable = false
        )
    }
    
    override suspend fun optimizeCoordinates(elements: List<UIElement>): CoordinateOptimization {
        return CoordinateOptimization()
    }
    
    override suspend fun chatWithAgent(message: String, history: List<ChatMessage>): ChatResponse {
        return ChatResponse(
            message = ChatMessage(
                content = "本地模型暂未实现",
                role = ChatMessage.MessageRole.ASSISTANT
            )
        )
    }
    
    override suspend fun analyzeScreenRealtime(screenData: ScreenData): ActionSuggestion {
        return ActionSuggestion(
            reasoning = "本地模型暂未实现",
            confidence = 0.0f
        )
    }
    
    override suspend fun validateScript(script: String, context: ScreenContext): ValidationResult {
        return ValidationResult(isValid = false)
    }
    
    override suspend fun getSuggestions(script: String, executionResult: ExecutionResult?): List<Suggestion> {
        return emptyList()
    }
}