package org.autojs.autojs.core.script

import android.content.Context
import kotlinx.coroutines.*
import org.autojs.autojs.core.log.LogManager
import org.autojs.autojs.core.memory.MemoryMonitor
import org.autojs.autojs.runtime.ScriptRuntime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 优化的脚本执行器
 * 提供异步执行、内存监控、性能统计等功能
 */
class OptimizedScriptExecutor private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "OptimizedScriptExecutor"
        private const val MAX_CONCURRENT_SCRIPTS = 10
        
        @Volatile
        private var INSTANCE: OptimizedScriptExecutor? = null
        
        fun getInstance(context: Context): OptimizedScriptExecutor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OptimizedScriptExecutor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val executorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val memoryMonitor = MemoryMonitor.getInstance()
    private val runningScripts = ConcurrentHashMap<String, Job>()
    private val scriptCounter = AtomicInteger(0)
    private val performanceStats = mutableMapOf<String, ExecutionStats>()
    
    /**
     * 异步执行脚本
     */
    fun executeScriptAsync(
        script: String,
        scriptName: String = "Script_${scriptCounter.incrementAndGet()}"
    ): Deferred<ScriptResult> {
        return executorScope.async {
            executeScriptInternal(script, scriptName)
        }
    }
    
    /**
     * 同步执行脚本（在后台线程）
     */
    suspend fun executeScript(
        script: String,
        scriptName: String = "Script_${scriptCounter.incrementAndGet()}"
    ): ScriptResult = withContext(Dispatchers.Default) {
        executeScriptInternal(script, scriptName)
    }
    
    /**
     * 批量执行脚本
     */
    suspend fun executeScriptsBatch(scripts: List<ScriptInfo>): List<ScriptResult> {
        // 检查内存状态
        if (memoryMonitor.isLowMemory(context)) {
            LogManager.w(TAG, "Low memory detected, forcing GC before batch execution")
            memoryMonitor.forceGCAndLog()
        }
        
        return scripts.chunked(MAX_CONCURRENT_SCRIPTS).flatMap { chunk ->
            chunk.map { scriptInfo ->
                async { executeScriptInternal(scriptInfo.content, scriptInfo.name) }
            }.awaitAll()
        }
    }
    
    /**
     * 停止指定脚本
     */
    fun stopScript(scriptName: String): Boolean {
        return runningScripts[scriptName]?.let { job ->
            job.cancel()
            runningScripts.remove(scriptName)
            LogManager.i(TAG, "Script '$scriptName' stopped")
            true
        } ?: false
    }
    
    /**
     * 停止所有脚本
     */
    fun stopAllScripts() {
        val count = runningScripts.size
        runningScripts.values.forEach { it.cancel() }
        runningScripts.clear()
        LogManager.i(TAG, "Stopped $count running scripts")
    }
    
    /**
     * 获取当前运行的脚本列表
     */
    fun getRunningScripts(): List<String> {
        return runningScripts.keys.toList()
    }
    
    /**
     * 获取性能统计信息
     */
    fun getPerformanceStats(): Map<String, ExecutionStats> {
        return performanceStats.toMap()
    }
    
    /**
     * 清理性能统计数据
     */
    fun clearPerformanceStats() {
        performanceStats.clear()
        LogManager.i(TAG, "Performance stats cleared")
    }
    
    /**
     * 内部脚本执行方法
     */
    private suspend fun executeScriptInternal(script: String, scriptName: String): ScriptResult {
        val startTime = System.currentTimeMillis()
        val job = coroutineContext[Job]
        
        return try {
            runningScripts[scriptName] = job!!
            
            LogManager.d(TAG, "Starting script execution: $scriptName")
            
            // 预检查内存状态
            val memoryInfo = memoryMonitor.getDetailedMemoryInfo(context)
            if (memoryInfo.getAppMemoryUsagePercentage() > 80) {
                LogManager.w(TAG, "High memory usage detected (${memoryInfo.getAppMemoryUsagePercentage()}%), consider optimizing")
            }
            
            // 执行脚本前记录内存
            val memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            
            // 在IO线程执行脚本以避免阻塞
            val result = withContext(Dispatchers.IO) {
                executeScriptWithRuntime(script)
            }
            
            // 执行后记录内存和性能
            val memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val executionTime = System.currentTimeMillis() - startTime
            
            // 更新性能统计
            updatePerformanceStats(scriptName, executionTime, memoryBefore, memoryAfter)
            
            LogManager.i(TAG, "Script '$scriptName' completed in ${executionTime}ms")
            
            ScriptResult.Success(result, executionTime, memoryAfter - memoryBefore)
            
        } catch (e: CancellationException) {
            LogManager.i(TAG, "Script '$scriptName' was cancelled")
            ScriptResult.Cancelled(scriptName)
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            LogManager.e(TAG, "Script '$scriptName' failed after ${executionTime}ms", e)
            ScriptResult.Error(e, executionTime)
        } finally {
            runningScripts.remove(scriptName)
        }
    }
    
    /**
     * 使用 ScriptRuntime 执行脚本
     */
    private fun executeScriptWithRuntime(script: String): Any? {
        // 这里需要根据实际的 ScriptRuntime 实现来调整
        // 暂时返回一个模拟结果
        return try {
            // TODO: 集成实际的 Rhino 引擎执行
            // val runtime = ScriptRuntime.createRuntime()
            // runtime.execute(script)
            "Script executed successfully"
        } catch (e: Exception) {
            throw ScriptExecutionException("Script execution failed", e)
        }
    }
    
    /**
     * 更新性能统计
     */
    private fun updatePerformanceStats(scriptName: String, executionTime: Long, memoryBefore: Long, memoryAfter: Long) {
        val stats = performanceStats.getOrPut(scriptName) { ExecutionStats() }
        stats.apply {
            totalExecutions++
            totalExecutionTime += executionTime
            averageExecutionTime = totalExecutionTime / totalExecutions
            lastExecutionTime = executionTime
            memoryDelta = memoryAfter - memoryBefore
            
            if (executionTime < fastestExecution || fastestExecution == 0L) {
                fastestExecution = executionTime
            }
            if (executionTime > slowestExecution) {
                slowestExecution = executionTime
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopAllScripts()
        executorScope.cancel()
        clearPerformanceStats()
        LogManager.i(TAG, "OptimizedScriptExecutor cleaned up")
    }
    
    /**
     * 脚本信息数据类
     */
    data class ScriptInfo(
        val name: String,
        val content: String
    )
    
    /**
     * 脚本执行结果
     */
    sealed class ScriptResult {
        data class Success(
            val result: Any?,
            val executionTime: Long,
            val memoryUsed: Long
        ) : ScriptResult()
        
        data class Error(
            val exception: Exception,
            val executionTime: Long
        ) : ScriptResult()
        
        data class Cancelled(
            val scriptName: String
        ) : ScriptResult()
    }
    
    /**
     * 执行统计数据
     */
    data class ExecutionStats(
        var totalExecutions: Long = 0,
        var totalExecutionTime: Long = 0,
        var averageExecutionTime: Long = 0,
        var lastExecutionTime: Long = 0,
        var fastestExecution: Long = 0,
        var slowestExecution: Long = 0,
        var memoryDelta: Long = 0
    )
    
    /**
     * 脚本执行异常
     */
    class ScriptExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)
}