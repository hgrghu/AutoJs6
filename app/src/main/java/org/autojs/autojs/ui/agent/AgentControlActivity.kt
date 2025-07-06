package org.autojs.autojs.ui.agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.autojs.autojs.core.agent.AutoJs6Agent
import org.autojs.autojs.core.agent.AgentExecutionOptions
import org.autojs.autojs.core.agent.models.AgentResult
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityAgentControlBinding

/**
 * AI Agent控制界面
 * 集成到AutoJs6的主界面中，提供自然语言脚本生成和执行功能
 */
class AgentControlActivity : BaseActivity() {

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, AgentControlActivity::class.java))
        }
    }

    private lateinit var binding: ActivityAgentControlBinding
    private lateinit var agent: AutoJs6Agent

    // 语音识别结果处理
    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            
            if (!spokenText.isNullOrBlank()) {
                binding.taskInput.setText(spokenText)
                executeTask(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityAgentControlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        agent = AutoJs6Agent.getInstance(this)
        
        setupToolbar()
        setupUI()
        initializeAgent()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.text_ai_agent)
        }
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupUI() {
        // 执行任务按钮
        binding.executeButton.setOnClickListener {
            val description = binding.taskInput.text.toString().trim()
            if (description.isNotEmpty()) {
                executeTask(description)
            } else {
                binding.taskInput.error = getString(R.string.text_describe_task)
            }
        }

        // 语音输入按钮
        binding.voiceInputButton.setOnClickListener {
            startVoiceRecognition()
        }

        // UI分析按钮
        binding.analyzeUIButton.setOnClickListener {
            UIAnalyzerActivity.launch(this)
        }

        // 执行历史按钮
        binding.historyButton.setOnClickListener {
            showExecutionHistory()
        }

        // 学习模式开关
        binding.learningModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startLearningMode()
            } else {
                stopLearningMode()
            }
        }
    }

    private fun initializeAgent() {
        binding.progressBar.isVisible = true
        binding.statusText.text = getString(R.string.text_ai_models_loading)
        
        lifecycleScope.launch {
            try {
                val success = agent.initialize()
                if (success) {
                    binding.statusText.text = "✅ Agent初始化成功"
                    updateAgentStatus()
                } else {
                    binding.statusText.text = "❌ Agent初始化失败"
                }
            } catch (e: Exception) {
                binding.statusText.text = "❌ Agent初始化出错: ${e.message}"
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun executeTask(description: String) {
        binding.progressBar.isVisible = true
        binding.executeButton.isEnabled = false
        binding.resultText.text = "🤖 正在处理任务..."

        lifecycleScope.launch {
            try {
                val result = agent.executeNaturalLanguageTask(
                    description = description,
                    options = AgentExecutionOptions(
                        autoExecute = binding.autoExecuteSwitch.isChecked,
                        requireUIAnalysis = true
                    )
                )

                handleExecutionResult(result)

            } catch (e: Exception) {
                binding.resultText.text = "❌ 执行失败: ${e.message}"
                binding.scriptPreview.text = ""
            } finally {
                binding.progressBar.isVisible = false
                binding.executeButton.isEnabled = true
            }
        }
    }

    private fun handleExecutionResult(result: AgentResult) {
        when (result) {
            is AgentResult.Success -> {
                binding.resultText.text = "✅ ${getString(R.string.text_task_executed_successfully)}"
                binding.scriptPreview.text = result.script.content
                
                // 显示UI分析结果（如果有）
                result.uiAnalysis?.let { analysis ->
                    val elementsInfo = "${getString(R.string.text_ui_elements_found)}: ${analysis.elements.size}"
                    val confidenceInfo = "${getString(R.string.text_confidence)}: ${(analysis.confidence * 100).toInt()}%"
                    binding.uiAnalysisInfo.text = "$elementsInfo\n$confidenceInfo"
                    binding.uiAnalysisInfo.isVisible = true
                }
            }
            
            is AgentResult.Error -> {
                binding.resultText.text = "❌ ${getString(R.string.text_task_execution_failed)}"
                binding.scriptPreview.text = "错误信息: ${result.error.message}\n\n建议:\n${result.suggestions.joinToString("\n• ", "• ")}"
                binding.uiAnalysisInfo.isVisible = false
            }
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.text_describe_task))
        }
        
        try {
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            binding.resultText.text = "语音识别不可用: ${e.message}"
        }
    }

    private fun updateAgentStatus() {
        lifecycleScope.launch {
            try {
                val status = agent.getAgentStatus()
                binding.statusText.text = buildString {
                    appendLine("Agent状态: ${if (status.isInitialized) "已初始化" else "未初始化"}")
                    appendLine("${getString(R.string.text_local_models)}: ${if (status.localModelsStatus.isLoaded) "已加载" else "未加载"}")
                    appendLine("${getString(R.string.text_cloud_services)}: ${if (status.cloudServiceStatus.isAvailable) "可用" else "不可用"}")
                    appendLine("${getString(R.string.text_memory_usage)}: ${formatMemoryUsage(status.memoryUsage.usedMemory, status.memoryUsage.totalMemory)}")
                    appendLine("${getString(R.string.text_active_sessions)}: ${status.activeSessionsCount}")
                }
            } catch (e: Exception) {
                binding.statusText.text = "无法获取Agent状态: ${e.message}"
            }
        }
    }

    private fun formatMemoryUsage(used: Long, total: Long): String {
        val usedMB = used / (1024 * 1024)
        val totalMB = total / (1024 * 1024)
        val percentage = if (total > 0) (used * 100 / total) else 0
        return "${usedMB}MB/${totalMB}MB (${percentage}%)"
    }

    private fun showExecutionHistory() {
        lifecycleScope.launch {
            try {
                val history = agent.getExecutionHistory(20)
                // 这里可以启动一个新的Activity来显示历史记录
                // 或者显示一个对话框
                val historyText = history.joinToString("\n\n") { record ->
                    "${record.task.description}\n生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(record.timestamp))}"
                }
                
                androidx.appcompat.app.AlertDialog.Builder(this@AgentControlActivity)
                    .setTitle(getString(R.string.text_execution_history))
                    .setMessage(if (historyText.isNotEmpty()) historyText else "暂无执行历史")
                    .setPositiveButton("确定", null)
                    .show()
                    
            } catch (e: Exception) {
                binding.resultText.text = "获取历史记录失败: ${e.message}"
            }
        }
    }

    private fun startLearningMode() {
        lifecycleScope.launch {
            try {
                val session = agent.startLearningMode()
                binding.resultText.text = "🎓 学习模式已启动，会话ID: ${session.id}"
            } catch (e: Exception) {
                binding.resultText.text = "启动学习模式失败: ${e.message}"
                binding.learningModeSwitch.isChecked = false
            }
        }
    }

    private fun stopLearningMode() {
        binding.resultText.text = "学习模式已停止"
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理Agent资源在Application层面处理，这里不需要
    }
}