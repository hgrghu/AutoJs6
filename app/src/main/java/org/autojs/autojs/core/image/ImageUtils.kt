package org.autojs.autojs.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import org.autojs.autojs.core.log.LogManager
import org.autojs.autojs.core.memory.MemoryMonitor
import org.opencv.core.Mat
import java.io.Closeable

/**
 * 优化的图像处理工具类
 * 确保内存安全释放，防止内存泄漏
 */
object ImageUtils {
    private const val TAG = "ImageUtils"
    private val memoryMonitor = MemoryMonitor.getInstance()
    
    /**
     * 安全的图像处理包装器
     */
    class SafeImageProcessor(private val image: Mat) : Closeable {
        private var closed = false
        
        fun process(operation: (Mat) -> Unit): SafeImageProcessor {
            checkNotClosed()
            try {
                operation(image)
            } catch (e: Exception) {
                LogManager.e(TAG, "Image processing failed", e)
                throw e
            }
            return this
        }
        
        fun <T> processWithResult(operation: (Mat) -> T): T {
            checkNotClosed()
            return try {
                operation(image)
            } catch (e: Exception) {
                LogManager.e(TAG, "Image processing with result failed", e)
                throw e
            }
        }
        
        override fun close() {
            if (!closed) {
                image.release()
                closed = true
                LogManager.v(TAG, "Image memory released")
            }
        }
        
        private fun checkNotClosed() {
            if (closed) {
                throw IllegalStateException("ImageProcessor has been closed")
            }
        }
        
        protected fun finalize() {
            if (!closed) {
                LogManager.w(TAG, "SafeImageProcessor was not properly closed, releasing memory in finalizer")
                close()
            }
        }
    }
    
    /**
     * 创建安全的图像处理器
     */
    fun createSafeProcessor(image: Mat): SafeImageProcessor {
        return SafeImageProcessor(image)
    }
    
    /**
     * 安全执行图像操作
     */
    inline fun <T> safeImageOperation(
        image: Mat,
        operation: (Mat) -> T
    ): T {
        return try {
            operation(image)
        } finally {
            // 确保释放图像内存
            if (!image.empty()) {
                image.release()
            }
        }
    }
    
    /**
     * 批量处理图像，自动管理内存
     */
    fun batchProcessImages(
        images: List<Mat>,
        operation: (Mat, Int) -> Unit
    ) {
        memoryMonitor.logMemoryUsage()
        
        try {
            images.forEachIndexed { index, image ->
                createSafeProcessor(image).use { processor ->
                    processor.process { mat ->
                        operation(mat, index)
                    }
                }
                
                // 每处理几张图片检查一次内存
                if (index % 5 == 0) {
                    val runtime = Runtime.getRuntime()
                    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                    val maxMemory = runtime.maxMemory()
                    
                    if (usedMemory > maxMemory * 0.8) {
                        LogManager.w(TAG, "High memory usage detected during batch processing, forcing GC")
                        System.gc()
                        Thread.sleep(50) // 给GC一些时间
                    }
                }
            }
        } finally {
            memoryMonitor.logMemoryUsage()
        }
    }
    
    /**
     * 安全的Bitmap处理
     */
    fun processBitmapSafely(
        bitmap: Bitmap,
        operation: (Bitmap) -> Bitmap?
    ): Bitmap? {
        if (bitmap.isRecycled) {
            LogManager.w(TAG, "Attempt to process recycled bitmap")
            return null
        }
        
        return try {
            val result = operation(bitmap)
            LogManager.v(TAG, "Bitmap processed successfully")
            result
        } catch (e: OutOfMemoryError) {
            LogManager.e(TAG, "OutOfMemoryError during bitmap processing", e)
            // 强制GC并重试一次
            System.gc()
            Thread.sleep(100)
            try {
                operation(bitmap)
            } catch (e2: OutOfMemoryError) {
                LogManager.e(TAG, "OutOfMemoryError on retry, giving up", e2)
                null
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error during bitmap processing", e)
            null
        }
    }
    
    /**
     * 优化的Bitmap缩放
     */
    fun scaleBitmapOptimized(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        filter: Boolean = true
    ): Bitmap? {
        if (source.isRecycled) {
            LogManager.w(TAG, "Cannot scale recycled bitmap")
            return null
        }
        
        return processBitmapSafely(source) { bitmap ->
            val matrix = Matrix()
            val scaleX = targetWidth.toFloat() / bitmap.width
            val scaleY = targetHeight.toFloat() / bitmap.height
            matrix.setScale(scaleX, scaleY)
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, filter)
        }
    }
    
    /**
     * 创建优化的Canvas绘制操作
     */
    fun drawOnBitmapSafely(
        bitmap: Bitmap,
        drawOperation: (Canvas) -> Unit
    ): Boolean {
        if (bitmap.isRecycled) {
            LogManager.w(TAG, "Cannot draw on recycled bitmap")
            return false
        }
        
        return try {
            val canvas = Canvas(bitmap)
            drawOperation(canvas)
            true
        } catch (e: Exception) {
            LogManager.e(TAG, "Error during canvas drawing", e)
            false
        }
    }
    
    /**
     * 安全释放Bitmap
     */
    fun recycleBitmapSafely(bitmap: Bitmap?) {
        bitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
                LogManager.v(TAG, "Bitmap recycled safely")
            }
        }
    }
    
    /**
     * 获取Bitmap内存使用情况
     */
    fun getBitmapMemoryUsage(bitmap: Bitmap): Long {
        return if (bitmap.isRecycled) {
            0L
        } else {
            bitmap.allocationByteCount.toLong()
        }
    }
    
    /**
     * 检查是否可以安全创建指定大小的Bitmap
     */
    fun canCreateBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Boolean {
        val bytesPerPixel = when (config) {
            Bitmap.Config.ARGB_8888 -> 4
            Bitmap.Config.RGB_565 -> 2
            Bitmap.Config.ARGB_4444 -> 2
            Bitmap.Config.ALPHA_8 -> 1
            else -> 4
        }
        
        val requiredMemory = width * height * bytesPerPixel
        val runtime = Runtime.getRuntime()
        val availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        
        return requiredMemory <= availableMemory * 0.8 // 保留20%的缓冲
    }
}