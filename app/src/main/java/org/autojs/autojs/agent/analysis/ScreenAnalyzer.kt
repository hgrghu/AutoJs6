package org.autojs.autojs.agent.analysis

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.agent.model.*
import org.autojs.autojs.runtime.accessibility.AccessibilityBridge
import com.stardust.autojs.core.accessibility.UiObject
import com.stardust.autojs.core.accessibility.UiSelector
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 屏幕分析器
 * 负责实时获取和分析屏幕内容，提取UI元素信息
 */
class ScreenAnalyzer {
    
    private val accessibilityBridge: AccessibilityBridge? = null
    private val elementCache = ConcurrentHashMap<String, UIElement>()
    private var lastScreenContext: ScreenContext? = null
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 捕获当前屏幕截图
     */
    fun captureScreen(): Bitmap? {
        return try {
            // 调用AutoJs现有的截图功能
            accessibilityBridge?.screenshot()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 分析当前屏幕布局
     */
    suspend fun analyzeLayout(): LayoutInfo? = withContext(Dispatchers.Default) {
        try {
            val rootNode = accessibilityBridge?.rootInActiveWindow
            if (rootNode == null) {
                return@withContext null
            }
            
            val allElements = mutableListOf<UIElement>()
            val clickableElements = mutableListOf<UIElement>()
            val textElements = mutableListOf<UIElement>()
            val inputElements = mutableListOf<UIElement>()
            
            // 递归分析节点树
            analyzeNodeRecursively(rootNode, allElements, clickableElements, textElements, inputElements)
            
            val rootElement = convertNodeToUIElement(rootNode)
            val hierarchy = generateHierarchy(rootNode)
            
            LayoutInfo(
                rootElement = rootElement,
                allElements = allElements,
                clickableElements = clickableElements,
                textElements = textElements,
                inputElements = inputElements,
                hierarchy = hierarchy
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 检测屏幕上的UI元素
     */
    suspend fun detectElements(): List<UIElement> = withContext(Dispatchers.Default) {
        try {
            val layoutInfo = analyzeLayout()
            layoutInfo?.allElements ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 提取屏幕上的文本元素
     */
    suspend fun extractText(): List<TextElement> = withContext(Dispatchers.Default) {
        try {
            val layoutInfo = analyzeLayout()
            layoutInfo?.textElements?.mapNotNull { element ->
                element.text?.let { text ->
                    TextElement(
                        text = text,
                        bounds = element.bounds,
                        element = element
                    )
                }
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 查找可点击区域
     */
    suspend fun findClickableAreas(): List<ClickableElement> = withContext(Dispatchers.Default) {
        try {
            val layoutInfo = analyzeLayout()
            layoutInfo?.clickableElements?.map { element ->
                ClickableElement(
                    bounds = element.bounds,
                    element = element,
                    actionType = determineActionType(element)
                )
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 获取完整的屏幕上下文
     */
    suspend fun getScreenContext(): ScreenContext? = withContext(Dispatchers.Default) {
        try {
            val screenshot = captureScreen()
            val layoutInfo = analyzeLayout()
            val elements = layoutInfo?.allElements ?: emptyList()
            val appPackage = getCurrentAppPackage()
            val activity = getCurrentActivity()
            
            ScreenContext(
                screenShot = screenshot,
                layoutInfo = layoutInfo,
                elements = elements,
                appPackage = appPackage,
                activity = activity
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 比较两个屏幕上下文，检测变化
     */
    fun detectScreenChanges(current: ScreenContext, previous: ScreenContext?): List<ScreenChange> {
        if (previous == null) return emptyList()
        
        val changes = mutableListOf<ScreenChange>()
        
        // 检测元素变化
        val currentElements = current.elements.associateBy { it.id ?: "${it.bounds}_${it.text}" }
        val previousElements = previous.elements.associateBy { it.id ?: "${it.bounds}_${it.text}" }
        
        // 新增的元素
        currentElements.keys.minus(previousElements.keys).forEach { key ->
            currentElements[key]?.let { element ->
                changes.add(ScreenChange(ScreenChange.ChangeType.ELEMENT_ADDED, element))
            }
        }
        
        // 删除的元素
        previousElements.keys.minus(currentElements.keys).forEach { key ->
            previousElements[key]?.let { element ->
                changes.add(ScreenChange(ScreenChange.ChangeType.ELEMENT_REMOVED, element))
            }
        }
        
        // 修改的元素
        currentElements.keys.intersect(previousElements.keys).forEach { key ->
            val currentElement = currentElements[key]
            val previousElement = previousElements[key]
            
            if (currentElement != previousElement) {
                // 检测具体变化类型
                when {
                    currentElement?.text != previousElement?.text -> {
                        changes.add(ScreenChange(
                            ScreenChange.ChangeType.TEXT_CHANGED,
                            currentElement,
                            previousElement?.text,
                            currentElement?.text
                        ))
                    }
                    currentElement?.bounds != previousElement?.bounds -> {
                        changes.add(ScreenChange(
                            ScreenChange.ChangeType.POSITION_CHANGED,
                            currentElement,
                            previousElement?.bounds,
                            currentElement?.bounds
                        ))
                    }
                    currentElement?.isVisible != previousElement?.isVisible -> {
                        changes.add(ScreenChange(
                            ScreenChange.ChangeType.VISIBILITY_CHANGED,
                            currentElement,
                            previousElement?.isVisible,
                            currentElement?.isVisible
                        ))
                    }
                    else -> {
                        changes.add(ScreenChange(
                            ScreenChange.ChangeType.ELEMENT_MODIFIED,
                            currentElement
                        ))
                    }
                }
            }
        }
        
        return changes
    }
    
    /**
     * 启动实时屏幕监控
     */
    fun startRealtimeMonitoring(callback: (ScreenData) -> Unit): Job {
        return analysisScope.launch {
            while (isActive) {
                try {
                    val currentContext = getScreenContext()
                    if (currentContext != null) {
                        val changes = detectScreenChanges(currentContext, lastScreenContext)
                        val screenData = ScreenData(
                            context = currentContext,
                            previousContext = lastScreenContext,
                            changes = changes
                        )
                        callback(screenData)
                        lastScreenContext = currentContext
                    }
                    delay(1000) // 每秒检查一次
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(5000) // 出错时等待更长时间
                }
            }
        }
    }
    
    /**
     * 递归分析节点
     */
    private fun analyzeNodeRecursively(
        node: AccessibilityNodeInfo,
        allElements: MutableList<UIElement>,
        clickableElements: MutableList<UIElement>,
        textElements: MutableList<UIElement>,
        inputElements: MutableList<UIElement>
    ) {
        val element = convertNodeToUIElement(node)
        allElements.add(element)
        
        // 分类元素
        if (element.isClickable) {
            clickableElements.add(element)
        }
        
        if (!element.text.isNullOrBlank()) {
            textElements.add(element)
        }
        
        if (isInputElement(element)) {
            inputElements.add(element)
        }
        
        // 递归处理子节点
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { childNode ->
                analyzeNodeRecursively(childNode, allElements, clickableElements, textElements, inputElements)
            }
        }
    }
    
    /**
     * 将AccessibilityNodeInfo转换为UIElement
     */
    private fun convertNodeToUIElement(node: AccessibilityNodeInfo): UIElement {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        return UIElement(
            id = node.viewIdResourceName,
            text = node.text?.toString(),
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            bounds = bounds,
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isCheckable = node.isCheckable,
            isChecked = node.isChecked,
            isEnabled = node.isEnabled,
            isVisible = node.isVisibleToUser,
            description = node.contentDescription?.toString(),
            attributes = extractAttributes(node)
        )
    }
    
    /**
     * 提取节点属性
     */
    private fun extractAttributes(node: AccessibilityNodeInfo): Map<String, Any> {
        val attributes = mutableMapOf<String, Any>()
        
        // 添加各种属性
        attributes["focusable"] = node.isFocusable
        attributes["focused"] = node.isFocused
        attributes["selected"] = node.isSelected
        attributes["longClickable"] = node.isLongClickable
        attributes["password"] = node.isPassword
        attributes["editable"] = node.isEditable
        
        return attributes
    }
    
    /**
     * 判断是否为输入元素
     */
    private fun isInputElement(element: UIElement): Boolean {
        return element.className?.contains("EditText") == true ||
                element.attributes["editable"] == true ||
                element.isCheckable
    }
    
    /**
     * 确定动作类型
     */
    private fun determineActionType(element: UIElement): Action.ActionType {
        return when {
            element.isScrollable -> Action.ActionType.SCROLL
            element.attributes["longClickable"] == true -> Action.ActionType.LONG_CLICK
            element.isClickable -> Action.ActionType.CLICK
            else -> Action.ActionType.CLICK
        }
    }
    
    /**
     * 生成层次结构字符串
     */
    private fun generateHierarchy(node: AccessibilityNodeInfo): String {
        return generateHierarchyRecursive(node, 0)
    }
    
    private fun generateHierarchyRecursive(node: AccessibilityNodeInfo, depth: Int): String {
        val indent = "  ".repeat(depth)
        val className = node.className?.toString() ?: "Unknown"
        val text = node.text?.toString()?.let { " \"$it\"" } ?: ""
        val id = node.viewIdResourceName?.let { " @$it" } ?: ""
        
        val result = StringBuilder()
        result.append("$indent$className$id$text\n")
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                result.append(generateHierarchyRecursive(child, depth + 1))
            }
        }
        
        return result.toString()
    }
    
    /**
     * 获取当前应用包名
     */
    private fun getCurrentAppPackage(): String? {
        return try {
            accessibilityBridge?.rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取当前Activity
     */
    private fun getCurrentActivity(): String? {
        return try {
            // 这里需要通过其他方式获取，可能需要使用shell命令或其他API
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        analysisScope.cancel()
        elementCache.clear()
    }
}

/**
 * 文本元素
 */
data class TextElement(
    val text: String,
    val bounds: Rect,
    val element: UIElement
)

/**
 * 可点击元素
 */
data class ClickableElement(
    val bounds: Rect,
    val element: UIElement,
    val actionType: Action.ActionType
)