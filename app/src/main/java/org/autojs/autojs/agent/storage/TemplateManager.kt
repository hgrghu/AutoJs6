package org.autojs.autojs.agent.storage

import android.content.Context
import org.autojs.autojs.agent.model.ScriptTemplate

/**
 * 模板管理器存根类
 */
class TemplateManager(private val context: Context) {
    
    suspend fun initialize() {
        // 存根实现
    }
    
    suspend fun findSimilarTemplates(request: String): List<ScriptTemplate> {
        return emptyList()
    }
    
    suspend fun getTemplates(category: String?, query: String?): List<ScriptTemplate> {
        return emptyList()
    }
    
    suspend fun saveTemplate(template: ScriptTemplate): Boolean {
        return true
    }
}