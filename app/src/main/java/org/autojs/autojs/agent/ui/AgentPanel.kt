package org.autojs.autojs.agent.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.autojs.autojs.agent.core.AgentService
import org.autojs.autojs.agent.model.*

/**
 * Agent面板UI组件
 * 提供脚本优化、生成和AI聊天功能的用户界面
 */
class AgentPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    // UI组件
    private lateinit var agentCheckBox: MaterialCheckBox
    private lateinit var chatButton: MaterialButton
    private lateinit var optimizeButton: MaterialButton
    private lateinit var generateButton: MaterialButton
    private lateinit var templatesButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var requestInput: TextInputEditText
    private lateinit var requestLayout: TextInputLayout
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    
    // 服务和状态
    private var agentService: AgentService? = null
    private var isAgentEnabled = false
    private var currentScript = ""
    
    // 回调接口
    interface AgentPanelListener {
        fun onScriptOptimized(optimizedScript: String)
        fun onScriptGenerated(script: String, explanation: String)
        fun onShowChatInterface()
        fun onShowTemplateLibrary()
        fun onShowSettings()
    }
    
    private var listener: AgentPanelListener? = null
    
    init {
        orientation = VERTICAL
        initializeViews()
        setupListeners()
        initializeAgent()
    }
    
    /**
     * 初始化视图
     */
    private fun initializeViews() {
        // 创建主容器
        val mainCard = MaterialCardView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(16, 8, 16, 8)
            }
            cardElevation = 4f
            radius = 12f
        }
        
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        // Agent启用开关
        agentCheckBox = MaterialCheckBox(context).apply {
            text = "启用AI Agent"
            textSize = 16f
            isChecked = false
        }
        container.addView(agentCheckBox)
        
        // 分隔线
        val divider1 = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(0, 12, 0, 12)
            }
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
        container.addView(divider1)
        
        // 请求输入区域
        requestLayout = TextInputLayout(context).apply {
            hint = "描述您想要实现的功能..."
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8, 0, 8)
            }
        }
        
        requestInput = TextInputEditText(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            minLines = 2
            maxLines = 4
        }
        requestLayout.addView(requestInput)
        container.addView(requestLayout)
        
        // 按钮区域
        val buttonContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 12, 0, 0)
            }
        }
        
        // 生成脚本按钮
        generateButton = MaterialButton(context).apply {
            text = "生成脚本"
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 4, 0)
            }
            isEnabled = false
        }
        buttonContainer.addView(generateButton)
        
        // 优化脚本按钮
        optimizeButton = MaterialButton(context).apply {
            text = "优化当前脚本"
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 0, 0, 0)
            }
            isEnabled = false
        }
        buttonContainer.addView(optimizeButton)
        
        container.addView(buttonContainer)
        
        // 第二行按钮
        val buttonContainer2 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8, 0, 0)
            }
        }
        
        // 聊天按钮
        chatButton = MaterialButton(context).apply {
            text = "AI聊天"
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 4, 0)
            }
            isEnabled = false
        }
        buttonContainer2.addView(chatButton)
        
        // 模板库按钮
        templatesButton = MaterialButton(context).apply {
            text = "脚本模板"
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 0, 4, 0)
            }
            isEnabled = false
        }
        buttonContainer2.addView(templatesButton)
        
        // 设置按钮
        settingsButton = MaterialButton(context).apply {
            text = "设置"
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 0, 0, 0)
            }
        }
        buttonContainer2.addView(settingsButton)
        
        container.addView(buttonContainer2)
        
        // 分隔线
        val divider2 = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(0, 12, 0, 12)
            }
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
        container.addView(divider2)
        
        // 状态区域
        val statusContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        
        progressBar = ProgressBar(context).apply {
            layoutParams = LayoutParams(24, 24).apply {
                setMargins(0, 0, 8, 0)
            }
            visibility = GONE
        }
        statusContainer.addView(progressBar)
        
        statusText = TextView(context).apply {
            text = "请先启用AI Agent"
            textSize = 12f
            setTextColor(0xFF666666.toInt())
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        statusContainer.addView(statusText)
        
        container.addView(statusContainer)
        
        mainCard.addView(container)
        addView(mainCard)
    }
    
    /**
     * 设置监听器
     */
    private fun setupListeners() {
        agentCheckBox.setOnCheckedChangeListener { _, isChecked ->
            isAgentEnabled = isChecked
            updateUIState()
            if (isChecked) {
                initializeAgent()
            }
        }
        
        generateButton.setOnClickListener {
            generateScript()
        }
        
        optimizeButton.setOnClickListener {
            optimizeScript()
        }
        
        chatButton.setOnClickListener {
            listener?.onShowChatInterface()
        }
        
        templatesButton.setOnClickListener {
            listener?.onShowTemplateLibrary()
        }
        
        settingsButton.setOnClickListener {
            listener?.onShowSettings()
        }
    }
    
    /**
     * 初始化Agent服务
     */
    private fun initializeAgent() {
        if (!isAgentEnabled) return
        
        try {
            agentService = AgentService.getInstance(context)
            // 在协程中初始化
            (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
                try {
                    agentService?.initialize()
                    updateStatus("AI Agent已就绪")
                } catch (e: Exception) {
                    updateStatus("AI Agent初始化失败: ${e.message}")
                    isAgentEnabled = false
                    agentCheckBox.isChecked = false
                    updateUIState()
                }
            }
        } catch (e: Exception) {
            updateStatus("无法启动AI Agent: ${e.message}")
            isAgentEnabled = false
            agentCheckBox.isChecked = false
            updateUIState()
        }
    }
    
    /**
     * 生成脚本
     */
    private fun generateScript() {
        val request = requestInput.text?.toString()?.trim()
        if (request.isNullOrBlank()) {
            updateStatus("请输入功能描述")
            return
        }
        
        showProgress(true)
        updateStatus("正在生成脚本...")
        
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            try {
                val result = agentService?.generateScript(request)
                if (result != null && result.isExecutable) {
                    updateStatus("脚本生成成功")
                    listener?.onScriptGenerated(result.script, result.explanation)
                } else {
                    updateStatus("脚本生成失败: ${result?.explanation ?: "未知错误"}")
                }
            } catch (e: Exception) {
                updateStatus("生成脚本时出错: ${e.message}")
            } finally {
                showProgress(false)
            }
        }
    }
    
    /**
     * 优化脚本
     */
    private fun optimizeScript() {
        if (currentScript.isBlank()) {
            updateStatus("没有可优化的脚本")
            return
        }
        
        showProgress(true)
        updateStatus("正在优化脚本...")
        
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            try {
                val result = agentService?.analyzeAndOptimizeScript(currentScript)
                if (result != null && result.isSuccessful) {
                    updateStatus("脚本优化完成，评分: ${result.score.toInt()}")
                    listener?.onScriptOptimized(result.optimizedScript)
                } else {
                    updateStatus("脚本优化失败: ${result?.warnings?.firstOrNull() ?: "未知错误"}")
                }
            } catch (e: Exception) {
                updateStatus("优化脚本时出错: ${e.message}")
            } finally {
                showProgress(false)
            }
        }
    }
    
    /**
     * 更新UI状态
     */
    private fun updateUIState() {
        val enabled = isAgentEnabled && agentService != null
        
        generateButton.isEnabled = enabled
        optimizeButton.isEnabled = enabled && currentScript.isNotBlank()
        chatButton.isEnabled = enabled
        templatesButton.isEnabled = enabled
        
        requestInput.isEnabled = enabled
        
        if (enabled) {
            updateStatus("AI Agent已就绪")
        } else {
            updateStatus("请先启用AI Agent")
        }
    }
    
    /**
     * 更新状态文本
     */
    private fun updateStatus(message: String) {
        post {
            statusText.text = message
        }
    }
    
    /**
     * 显示/隐藏进度条
     */
    private fun showProgress(show: Boolean) {
        post {
            progressBar.visibility = if (show) VISIBLE else GONE
        }
    }
    
    /**
     * 设置当前脚本
     */
    fun setCurrentScript(script: String) {
        currentScript = script
        updateUIState()
    }
    
    /**
     * 设置监听器
     */
    fun setAgentPanelListener(listener: AgentPanelListener) {
        this.listener = listener
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        agentService?.cleanup()
    }
}