package org.autojs.autojs.core.memory

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import org.autojs.autojs.core.log.LogManager
import java.text.DecimalFormat

/**
 * 内存监控工具类
 * 用于监控应用内存使用情况，帮助发现内存泄漏和优化内存使用
 */
class MemoryMonitor private constructor() {
    
    companion object {
        private const val TAG = "MemoryMonitor"
        private val INSTANCE = MemoryMonitor()
        
        @JvmStatic
        fun getInstance(): MemoryMonitor = INSTANCE
    }
    
    private val formatter = DecimalFormat("#.##")
    
    /**
     * 记录当前内存使用情况
     */
    fun logMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val availableMemory = maxMemory - usedMemory
        
        LogManager.i(TAG, "Memory Usage:")
        LogManager.i(TAG, "  Used: ${formatBytes(usedMemory)}")
        LogManager.i(TAG, "  Available: ${formatBytes(availableMemory)}")
        LogManager.i(TAG, "  Max: ${formatBytes(maxMemory)}")
        LogManager.i(TAG, "  Usage: ${formatter.format(usedMemory.toDouble() / maxMemory * 100)}%")
    }
    
    /**
     * 获取详细的内存信息
     */
    fun getDetailedMemoryInfo(context: Context): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        // App 内存信息
        val appUsedMemory = runtime.totalMemory() - runtime.freeMemory()
        val appMaxMemory = runtime.maxMemory()
        
        // 系统内存信息
        val systemMemoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(systemMemoryInfo)
        
        // 堆内存信息
        val heapSize = Debug.getNativeHeapSize()
        val heapAllocated = Debug.getNativeHeapAllocatedSize()
        val heapFree = Debug.getNativeHeapFreeSize()
        
        return MemoryInfo(
            appUsedMemory = appUsedMemory,
            appMaxMemory = appMaxMemory,
            appAvailableMemory = appMaxMemory - appUsedMemory,
            systemTotalMemory = systemMemoryInfo.totalMem,
            systemAvailableMemory = systemMemoryInfo.availMem,
            isLowMemory = systemMemoryInfo.lowMemory,
            nativeHeapSize = heapSize,
            nativeHeapAllocated = heapAllocated,
            nativeHeapFree = heapFree
        )
    }
    
    /**
     * 检查是否处于低内存状态
     */
    fun isLowMemory(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }
    
    /**
     * 强制垃圾回收并记录回收前后的内存使用
     */
    fun forceGCAndLog() {
        val beforeGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        System.gc()
        Thread.sleep(100) // 给GC一些时间
        val afterGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        LogManager.i(TAG, "GC performed:")
        LogManager.i(TAG, "  Before GC: ${formatBytes(beforeGC)}")
        LogManager.i(TAG, "  After GC: ${formatBytes(afterGC)}")
        LogManager.i(TAG, "  Freed: ${formatBytes(beforeGC - afterGC)}")
    }
    
    /**
     * 格式化字节数
     */
    private fun formatBytes(bytes: Long): String {
        when {
            bytes >= 1024 * 1024 * 1024 -> return "${formatter.format(bytes.toDouble() / 1024 / 1024 / 1024)} GB"
            bytes >= 1024 * 1024 -> return "${formatter.format(bytes.toDouble() / 1024 / 1024)} MB"
            bytes >= 1024 -> return "${formatter.format(bytes.toDouble() / 1024)} KB"
            else -> return "$bytes B"
        }
    }
    
    /**
     * 内存信息数据类
     */
    data class MemoryInfo(
        val appUsedMemory: Long,
        val appMaxMemory: Long,
        val appAvailableMemory: Long,
        val systemTotalMemory: Long,
        val systemAvailableMemory: Long,
        val isLowMemory: Boolean,
        val nativeHeapSize: Long,
        val nativeHeapAllocated: Long,
        val nativeHeapFree: Long
    ) {
        fun getAppMemoryUsagePercentage(): Double {
            return appUsedMemory.toDouble() / appMaxMemory * 100
        }
        
        fun getSystemMemoryUsagePercentage(): Double {
            return (systemTotalMemory - systemAvailableMemory).toDouble() / systemTotalMemory * 100
        }
    }
}