package org.autojs.autojs.agent.api

import org.autojs.autojs.agent.model.*

/**
 * AI Agent核心接口
 * 提供脚本分析、优化、生成和对话功能
 */
interface AgentAPI {
    
    /**
     * 分析并优化脚本
     * @param script 原始脚本内容
     * @param context 屏幕上下文信息
     * @return 优化结果
     */
    suspend fun analyzeScript(script: String, context: ScreenContext): OptimizationResult
    
    /**
     * 根据自然语言请求生成脚本
     * @param request 用户请求描述
     * @param context 当前屏幕上下文
     * @return 脚本生成结果
     */
    suspend fun generateScript(request: String, context: ScreenContext): ScriptGenerationResult
    
    /**
     * 优化坐标定位方式
     * @param elements 屏幕元素列表
     * @return 坐标优化建议
     */
    suspend fun optimizeCoordinates(elements: List<UIElement>): CoordinateOptimization
    
    /**
     * 与AI助手对话
     * @param message 用户消息
     * @param history 对话历史
     * @return AI回复
     */
    suspend fun chatWithAgent(message: String, history: List<ChatMessage>): ChatResponse
    
    /**
     * 实时分析屏幕并提供操作建议
     * @param screenData 当前屏幕数据
     * @return 操作建议
     */
    suspend fun analyzeScreenRealtime(screenData: ScreenData): ActionSuggestion
    
    /**
     * 验证脚本正确性
     * @param script 脚本内容
     * @param context 执行上下文
     * @return 验证结果
     */
    suspend fun validateScript(script: String, context: ScreenContext): ValidationResult
    
    /**
     * 获取脚本改进建议
     * @param script 脚本内容
     * @param executionResult 执行结果
     * @return 改进建议列表
     */
    suspend fun getSuggestions(script: String, executionResult: ExecutionResult?): List<Suggestion>
}