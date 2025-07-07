package org.autojs.autojs.agent.storage

import android.content.Context
import org.autojs.autojs.agent.core.ScriptExecutionRecord
import org.autojs.autojs.agent.model.*

/**
 * 脚本仓库存根类
 */
class ScriptRepository(private val context: Context) {
    
    suspend fun initialize() {
        // 存根实现
    }
    
    suspend fun saveOptimizationRecord(script: String, result: OptimizationResult) {
        // 存根实现
    }
    
    suspend fun saveGenerationRecord(request: String, result: ScriptGenerationResult) {
        // 存根实现
    }
    
    suspend fun saveChatHistory(sessionId: String, userMessage: ChatMessage, aiMessage: ChatMessage) {
        // 存根实现
    }
    
    suspend fun getExecutionHistory(limit: Int): List<ScriptExecutionRecord> {
        return emptyList()
    }
}