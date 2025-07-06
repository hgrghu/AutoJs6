package org.autojs.autojs.ui.agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.autojs.autojs.core.agent.AutoJs6Agent
import org.autojs.autojs.core.agent.AgentException
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityUiAnalyzerBinding

/**
 * UI分析器界面
 * 分析当前界面的UI元素并生成操作代码
 */
class UIAnalyzerActivity : BaseActivity() {

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, UIAnalyzerActivity::class.java))
        }
    }

    private lateinit var binding: ActivityUiAnalyzerBinding
    private lateinit var agent: AutoJs6Agent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityUiAnalyzerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        agent = AutoJs6Agent.getInstance(this)
        
        setupToolbar()
        setupUI()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.text_ui_analyzer)
        }
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupUI() {
        binding.analyzeButton.setOnClickListener {
            analyzeCurrentUI()
        }
        
        binding.refreshButton.setOnClickListener {
            analyzeCurrentUI()
        }
    }

    private fun analyzeCurrentUI() {
        binding.progressBar.isVisible = true
        binding.analyzeButton.isEnabled = false
        binding.refreshButton.isEnabled = false
        binding.statusText.text = "正在分析当前界面..."

        lifecycleScope.launch {
            try {
                val analysis = agent.analyzeCurrentUI()
                
                // 显示分析结果
                binding.statusText.text = "✅ UI分析完成"
                binding.elementsCountText.text = "${getString(R.string.text_ui_elements_found)}: ${analysis.elements.size}"
                binding.confidenceText.text = "${getString(R.string.text_confidence)}: ${(analysis.confidence * 100).toInt()}%"
                
                // 显示建议操作
                val suggestionsText = if (analysis.suggestedActions.isNotEmpty()) {
                    analysis.suggestedActions.joinToString("\n") { "• ${it.description}" }
                } else {
                    "暂无操作建议"
                }
                binding.suggestionsText.text = suggestionsText
                
                // 显示生成的代码
                binding.codePreview.text = analysis.generatedCode
                
                // 显示结果容器
                binding.resultContainer.isVisible = true
                
            } catch (e: AgentException) {
                binding.statusText.text = "❌ UI分析失败: ${e.message}"
                binding.resultContainer.isVisible = false
            } catch (e: Exception) {
                binding.statusText.text = "❌ 发生错误: ${e.message}"
                binding.resultContainer.isVisible = false
            } finally {
                binding.progressBar.isVisible = false
                binding.analyzeButton.isEnabled = true
                binding.refreshButton.isEnabled = true
            }
        }
    }
}