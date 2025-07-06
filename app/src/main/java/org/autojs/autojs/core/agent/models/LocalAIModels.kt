package org.autojs.autojs.core.agent.models

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.log.LogManager
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

/**
 * 本地AI模型集成
 * 提供离线的UI元素识别、文本识别、意图分类等功能
 */
class LocalAIModels(private val context: Context) {
    
    companion object {
        private const val TAG = "LocalAIModels"
        
        // 模型文件名
        private const val UI_DETECTOR_MODEL = "ui_element_detector.tflite"
        private const val TEXT_CLASSIFIER_MODEL = "text_classifier.tflite"
        private const val INTENT_CLASSIFIER_MODEL = "intent_classifier.tflite"
        
        // 输入尺寸
        private const val IMAGE_INPUT_SIZE = 416
        private const val TEXT_MAX_LENGTH = 128
    }
    
    // TensorFlow Lite 解释器
    private var uiDetectorInterpreter: Interpreter? = null
    private var textClassifierInterpreter: Interpreter? = null
    private var intentClassifierInterpreter: Interpreter? = null
    
    // 模型状态
    private var isInitialized = false
    private val loadedModels = mutableSetOf<String>()
    private val failedModels = mutableSetOf<String>()
    
    /**
     * 初始化所有本地模型
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        
        LogManager.i(TAG, "开始初始化本地AI模型...")
        
        try {
            // 初始化UI检测模型
            initializeUIDetector()
            
            // 初始化文本分类模型
            initializeTextClassifier()
            
            // 初始化意图分类模型
            initializeIntentClassifier()
            
            isInitialized = loadedModels.isNotEmpty()
            LogManager.i(TAG, "本地模型初始化完成，成功加载: $loadedModels, 失败: $failedModels")
            
            isInitialized
        } catch (e: Exception) {
            LogManager.e(TAG, "本地模型初始化失败", e)
            false
        }
    }
    
    /**
     * 检测UI元素
     */
    suspend fun detectUIElements(image: Bitmap): List<UIElement> = withContext(Dispatchers.Default) {
        val interpreter = uiDetectorInterpreter ?: return@withContext emptyList()
        
        try {
            // 预处理图像
            val inputBuffer = preprocessImageForUIDetection(image)
            
            // 模型推理
            val outputArray = Array(1) { Array(100) { FloatArray(6) } } // [batch, detections, (x,y,w,h,conf,class)]
            interpreter.run(inputBuffer, outputArray)
            
            // 后处理结果
            parseUIDetectionResults(outputArray[0], image.width, image.height)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "UI元素检测失败", e)
            emptyList()
        }
    }
    
    /**
     * 识别文本内容
     */
    suspend fun recognizeText(image: Bitmap): String = withContext(Dispatchers.Default) {
        return@withContext try {
            // 使用MLKit的OCR功能 (AutoJs6项目中已有)
            // 这里是简化实现，实际应该集成MLKit OCR
            "识别的文本内容" // 占位实现
        } catch (e: Exception) {
            LogManager.e(TAG, "文本识别失败", e)
            ""
        }
    }
    
    /**
     * 分类用户意图
     */
    suspend fun classifyIntent(text: String): Intent = withContext(Dispatchers.Default) {
        val interpreter = intentClassifierInterpreter ?: return@withContext createDefaultIntent()
        
        try {
            // 预处理文本
            val inputBuffer = preprocessTextForClassification(text)
            
            // 模型推理
            val outputArray = Array(1) { FloatArray(IntentType.values().size) }
            interpreter.run(inputBuffer, outputArray)
            
            // 解析结果
            parseIntentClassificationResult(outputArray[0], text)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "意图分类失败", e)
            createDefaultIntent()
        }
    }
    
    /**
     * 获取模型状态
     */
    fun getStatus(): ModelsStatus {
        return ModelsStatus(
            isLoaded = isInitialized,
            loadedModels = loadedModels.toList(),
            failedModels = failedModels.toList(),
            totalMemoryUsage = calculateMemoryUsage()
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        LogManager.i(TAG, "清理本地模型资源...")
        
        uiDetectorInterpreter?.close()
        textClassifierInterpreter?.close()
        intentClassifierInterpreter?.close()
        
        uiDetectorInterpreter = null
        textClassifierInterpreter = null
        intentClassifierInterpreter = null
        
        loadedModels.clear()
        failedModels.clear()
        isInitialized = false
        
        LogManager.i(TAG, "本地模型资源清理完成")
    }
    
    // 私有方法
    
    private fun initializeUIDetector() {
        try {
            val modelBuffer = loadModelFile(UI_DETECTOR_MODEL)
            uiDetectorInterpreter = Interpreter(modelBuffer)
            loadedModels.add("UI_DETECTOR")
            LogManager.d(TAG, "UI检测模型加载成功")
        } catch (e: Exception) {
            LogManager.w(TAG, "UI检测模型加载失败: ${e.message}")
            failedModels.add("UI_DETECTOR")
        }
    }
    
    private fun initializeTextClassifier() {
        try {
            val modelBuffer = loadModelFile(TEXT_CLASSIFIER_MODEL)
            textClassifierInterpreter = Interpreter(modelBuffer)
            loadedModels.add("TEXT_CLASSIFIER")
            LogManager.d(TAG, "文本分类模型加载成功")
        } catch (e: Exception) {
            LogManager.w(TAG, "文本分类模型加载失败: ${e.message}")
            failedModels.add("TEXT_CLASSIFIER")
        }
    }
    
    private fun initializeIntentClassifier() {
        try {
            val modelBuffer = loadModelFile(INTENT_CLASSIFIER_MODEL)
            intentClassifierInterpreter = Interpreter(modelBuffer)
            loadedModels.add("INTENT_CLASSIFIER")
            LogManager.d(TAG, "意图分类模型加载成功")
        } catch (e: Exception) {
            LogManager.w(TAG, "意图分类模型加载失败: ${e.message}")
            failedModels.add("INTENT_CLASSIFIER")
        }
    }
    
    private fun loadModelFile(fileName: String): MappedByteBuffer {
        return try {
            val fileDescriptor = context.assets.openFd("models/$fileName")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            LogManager.e(TAG, "模型文件加载失败: $fileName", e)
            throw e
        }
    }
    
    private fun preprocessImageForUIDetection(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_INPUT_SIZE, IMAGE_INPUT_SIZE, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * IMAGE_INPUT_SIZE * IMAGE_INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(IMAGE_INPUT_SIZE * IMAGE_INPUT_SIZE)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)
        
        var pixel = 0
        for (i in 0 until IMAGE_INPUT_SIZE) {
            for (j in 0 until IMAGE_INPUT_SIZE) {
                val pixelValue = intValues[pixel++]
                // 归一化到 [0, 1]
                byteBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f) // R
                byteBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)  // G
                byteBuffer.putFloat((pixelValue and 0xFF) / 255.0f)          // B
            }
        }
        
        return byteBuffer
    }
    
    private fun parseUIDetectionResults(
        output: Array<FloatArray>,
        imageWidth: Int,
        imageHeight: Int
    ): List<UIElement> {
        val elements = mutableListOf<UIElement>()
        
        output.forEachIndexed { index, detection ->
            val confidence = detection[4]
            if (confidence > 0.5f) { // 置信度阈值
                val x = (detection[0] * imageWidth).toInt()
                val y = (detection[1] * imageHeight).toInt()
                val width = (detection[2] * imageWidth).toInt()
                val height = (detection[3] * imageHeight).toInt()
                val classIndex = detection[5].toInt()
                
                val elementType = if (classIndex < ElementType.values().size) {
                    ElementType.values()[classIndex]
                } else {
                    ElementType.UNKNOWN
                }
                
                elements.add(
                    UIElement(
                        id = "detected_$index",
                        type = elementType,
                        bounds = Rect(x, y, x + width, y + height),
                        confidence = confidence,
                        isClickable = isClickableType(elementType),
                        isScrollable = isScrollableType(elementType)
                    )
                )
            }
        }
        
        return elements
    }
    
    private fun preprocessTextForClassification(text: String): ByteBuffer {
        // 简化的文本预处理
        val tokens = tokenizeText(text)
        val byteBuffer = ByteBuffer.allocateDirect(4 * TEXT_MAX_LENGTH)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        repeat(TEXT_MAX_LENGTH) { i ->
            val tokenId = if (i < tokens.size) tokens[i] else 0
            byteBuffer.putFloat(tokenId.toFloat())
        }
        
        return byteBuffer
    }
    
    private fun tokenizeText(text: String): List<Int> {
        // 简化的分词实现，实际应该使用更复杂的分词器
        return text.lowercase()
            .split(" ", "，", "。", "！", "？")
            .filter { it.isNotBlank() }
            .map { it.hashCode() % 10000 } // 简单的hash映射
            .take(TEXT_MAX_LENGTH)
    }
    
    private fun parseIntentClassificationResult(output: FloatArray, originalText: String): Intent {
        val maxIndex = output.indices.maxByOrNull { output[it] } ?: 0
        val confidence = output[maxIndex]
        val intentType = IntentType.values().getOrNull(maxIndex) ?: IntentType.UNKNOWN
        
        return Intent(
            type = intentType,
            confidence = confidence,
            parameters = mapOf("original_text" to originalText),
            entities = extractEntities(originalText)
        )
    }
    
    private fun extractEntities(text: String): List<Entity> {
        val entities = mutableListOf<Entity>()
        
        // 简单的实体提取规则
        if (text.contains("点击", ignoreCase = true)) {
            entities.add(Entity(EntityType.UI_ELEMENT, "button", 0.8f))
        }
        
        if (text.contains("输入", ignoreCase = true)) {
            entities.add(Entity(EntityType.UI_ELEMENT, "input_field", 0.8f))
        }
        
        // 提取数字
        val numberRegex = "\\d+".toRegex()
        numberRegex.findAll(text).forEach { match ->
            entities.add(Entity(EntityType.NUMBER, match.value, 0.9f, match.range))
        }
        
        return entities
    }
    
    private fun isClickableType(type: ElementType): Boolean {
        return when (type) {
            ElementType.BUTTON, ElementType.IMAGE_BUTTON, 
            ElementType.CHECK_BOX, ElementType.RADIO_BUTTON,
            ElementType.SWITCH, ElementType.TEXT_VIEW -> true
            else -> false
        }
    }
    
    private fun isScrollableType(type: ElementType): Boolean {
        return when (type) {
            ElementType.LIST_VIEW, ElementType.RECYCLER_VIEW,
            ElementType.SCROLL_VIEW -> true
            else -> false
        }
    }
    
    private fun createDefaultIntent(): Intent {
        return Intent(
            type = IntentType.UNKNOWN,
            confidence = 0.0f,
            parameters = emptyMap(),
            entities = emptyList()
        )
    }
    
    private fun calculateMemoryUsage(): Long {
        // 估算模型内存使用量
        var totalMemory = 0L
        
        uiDetectorInterpreter?.let { totalMemory += 50 * 1024 * 1024 } // 50MB
        textClassifierInterpreter?.let { totalMemory += 20 * 1024 * 1024 } // 20MB
        intentClassifierInterpreter?.let { totalMemory += 10 * 1024 * 1024 } // 10MB
        
        return totalMemory
    }
}