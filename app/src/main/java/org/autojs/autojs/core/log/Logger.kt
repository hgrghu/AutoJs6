package org.autojs.autojs.core.log

import android.util.Log

/**
 * 统一的日志接口，用于替换项目中混乱的日志使用
 * 支持不同级别的日志输出，并提供统一的格式化
 */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun v(tag: String, message: String)
}

/**
 * Android 日志实现
 */
class AndroidLogger : Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    override fun v(tag: String, message: String) {
        Log.v(tag, message)
    }
}

/**
 * 日志管理器单例
 */
object LogManager {
    private var logger: Logger = AndroidLogger()
    
    fun setLogger(logger: Logger) {
        this.logger = logger
    }
    
    fun d(tag: String, message: String) = logger.d(tag, message)
    fun i(tag: String, message: String) = logger.i(tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = logger.w(tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = logger.e(tag, message, throwable)
    fun v(tag: String, message: String) = logger.v(tag, message)
}

/**
 * 便捷的日志扩展函数
 */
inline fun Any.logd(message: String) {
    LogManager.d(this::class.java.simpleName, message)
}

inline fun Any.logi(message: String) {
    LogManager.i(this::class.java.simpleName, message)
}

inline fun Any.logw(message: String, throwable: Throwable? = null) {
    LogManager.w(this::class.java.simpleName, message, throwable)
}

inline fun Any.loge(message: String, throwable: Throwable? = null) {
    LogManager.e(this::class.java.simpleName, message, throwable)
}

inline fun Any.logv(message: String) {
    LogManager.v(this::class.java.simpleName, message)
}