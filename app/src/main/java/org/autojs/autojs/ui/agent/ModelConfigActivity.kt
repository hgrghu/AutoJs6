package org.autojs.autojs.ui.agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.autojs.autojs.core.agent.CloudAIService
import org.autojs.autojs.core.agent.models.AIModelConfig
import org.autojs.autojs.core.agent.models.ModelProvider
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityModelConfigBinding
import org.autojs.autojs6.databinding.ItemModelConfigBinding
import org.autojs.autojs6.databinding.DialogAddModelBinding

/**
 * AI模型配置管理Activity
 */
class ModelConfigActivity : BaseActivity() {

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, ModelConfigActivity::class.java))
        }
    }

    private lateinit var binding: ActivityModelConfigBinding
    private lateinit var adapter: ModelConfigAdapter
    private lateinit var cloudAIService: CloudAIService
    private val modelConfigs = mutableListOf<AIModelConfig>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityModelConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cloudAIService = CloudAIService(this)
        
        setupToolbar()
        setupRecyclerView()
        setupUI()
        loadModelConfigs()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.text_model_management)
        }
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = ModelConfigAdapter(modelConfigs) { config, action ->
            when (action) {
                ModelAction.EDIT -> editModel(config)
                ModelAction.DELETE -> deleteModel(config)
                ModelAction.TEST -> testModel(config)
                ModelAction.TOGGLE -> toggleModel(config)
                ModelAction.SET_DEFAULT -> setDefaultModel(config)
            }
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupUI() {
        binding.fabAddModel.setOnClickListener {
            showAddModelDialog()
        }
        
        binding.addDefaultModelsButton.setOnClickListener {
            addDefaultModels()
        }
    }

    private fun loadModelConfigs() {
        // 从SharedPreferences或数据库加载保存的模型配置
        val sharedPref = getSharedPreferences("ai_models", Context.MODE_PRIVATE)
        val savedConfigs = sharedPref.getStringSet("configs", emptySet()) ?: emptySet()
        
        modelConfigs.clear()
        
        if (savedConfigs.isEmpty()) {
            // 如果没有保存的配置，显示添加默认模型的提示
            binding.emptyView.isVisible = true
            binding.recyclerView.isVisible = false
        } else {
            // TODO: 实现从JSON反序列化模型配置
            // 这里暂时使用示例数据
            modelConfigs.addAll(getExampleConfigs())
            binding.emptyView.isVisible = false
            binding.recyclerView.isVisible = true
        }
        
        adapter.notifyDataSetChanged()
    }

    private fun getExampleConfigs(): List<AIModelConfig> {
        return listOf(
            AIModelConfig.createDefaultOpenAI().copy(apiKey = "sk-..."),
            AIModelConfig.createDefaultClaude().copy(apiKey = "sk-ant-..."),
            AIModelConfig.createDefaultGemini().copy(apiKey = "AIza...")
        )
    }

    private fun saveModelConfigs() {
        // TODO: 实现将模型配置序列化保存到SharedPreferences或数据库
        val sharedPref = getSharedPreferences("ai_models", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            // 这里应该将modelConfigs序列化为JSON
            apply()
        }
    }

    private fun showAddModelDialog() {
        val dialogBinding = DialogAddModelBinding.inflate(layoutInflater)
        
        // 设置提供商选择
        val providers = ModelProvider.values()
        val providerNames = providers.map { it.displayName }.toTypedArray()
        
        dialogBinding.providerSpinner.setAdapter(
            android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, providerNames)
        )
        
        // 设置提供商选择监听器
        dialogBinding.providerSpinner.setOnItemClickListener { _, _, position, _ ->
            val selectedProvider = providers[position]
            dialogBinding.baseUrlInput.setText(selectedProvider.defaultBaseUrl)
            
            // 设置默认模型名称
            when (selectedProvider) {
                ModelProvider.OPENAI -> dialogBinding.modelNameInput.setText("gpt-4")
                ModelProvider.ANTHROPIC -> dialogBinding.modelNameInput.setText("claude-3-5-sonnet-20241022")
                ModelProvider.GOOGLE -> dialogBinding.modelNameInput.setText("gemini-pro")
                ModelProvider.CUSTOM -> dialogBinding.modelNameInput.setText("")
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.text_add_model))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.text_save_config)) { _, _ ->
                val name = dialogBinding.nameInput.text.toString().trim()
                val providerIndex = getProviderIndex(dialogBinding.providerSpinner.text.toString())
                val modelName = dialogBinding.modelNameInput.text.toString().trim()
                val baseUrl = dialogBinding.baseUrlInput.text.toString().trim()
                val apiKey = dialogBinding.apiKeyInput.text.toString().trim()
                
                if (validateInput(name, modelName, baseUrl, apiKey)) {
                    val config = AIModelConfig(
                        name = name,
                        provider = providers[providerIndex],
                        modelName = modelName,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        maxTokens = dialogBinding.maxTokensInput.text.toString().toIntOrNull() ?: 1000,
                        temperature = dialogBinding.temperatureInput.text.toString().toFloatOrNull() ?: 0.7f
                    )
                    
                    addModel(config)
                } else {
                    Toast.makeText(this, "请填写所有必填字段", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.show()
    }

    private fun getProviderIndex(providerName: String): Int {
        return ModelProvider.values().indexOfFirst { it.displayName == providerName }
    }

    private fun validateInput(name: String, modelName: String, baseUrl: String, apiKey: String): Boolean {
        return name.isNotBlank() && modelName.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()
    }

    private fun addModel(config: AIModelConfig) {
        modelConfigs.add(config)
        adapter.notifyItemInserted(modelConfigs.size - 1)
        
        binding.emptyView.isVisible = false
        binding.recyclerView.isVisible = true
        
        saveModelConfigs()
        
        Toast.makeText(this, "模型配置已添加", Toast.LENGTH_SHORT).show()
    }

    private fun addDefaultModels() {
        val defaultConfigs = listOf(
            AIModelConfig.createDefaultOpenAI(),
            AIModelConfig.createDefaultClaude(),
            AIModelConfig.createDefaultGemini()
        )
        
        modelConfigs.addAll(defaultConfigs)
        adapter.notifyDataSetChanged()
        
        binding.emptyView.isVisible = false
        binding.recyclerView.isVisible = true
        
        saveModelConfigs()
        
        Toast.makeText(this, "已添加默认模型配置，请设置API密钥", Toast.LENGTH_LONG).show()
    }

    private fun editModel(config: AIModelConfig) {
        // TODO: 实现编辑模型配置
        Toast.makeText(this, "编辑功能待实现", Toast.LENGTH_SHORT).show()
    }

    private fun deleteModel(config: AIModelConfig) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除模型")
            .setMessage("确定要删除模型 ${config.name} 吗？")
            .setPositiveButton(getString(R.string.text_delete_model)) { _, _ ->
                val position = modelConfigs.indexOf(config)
                modelConfigs.remove(config)
                adapter.notifyItemRemoved(position)
                
                if (modelConfigs.isEmpty()) {
                    binding.emptyView.isVisible = true
                    binding.recyclerView.isVisible = false
                }
                
                saveModelConfigs()
                Toast.makeText(this, "模型已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun testModel(config: AIModelConfig) {
        if (!config.isValid()) {
            Toast.makeText(this, "模型配置不完整，无法测试", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true
                val success = cloudAIService.testConnection(config)
                
                if (success) {
                    Toast.makeText(this@ModelConfigActivity, getString(R.string.text_connection_successful), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ModelConfigActivity, getString(R.string.text_connection_failed), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ModelConfigActivity, "测试失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun toggleModel(config: AIModelConfig) {
        val index = modelConfigs.indexOf(config)
        if (index != -1) {
            modelConfigs[index] = config.copy(isEnabled = !config.isEnabled)
            adapter.notifyItemChanged(index)
            saveModelConfigs()
            
            val status = if (config.isEnabled) "已禁用" else "已启用"
            Toast.makeText(this, "模型 ${config.name} $status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setDefaultModel(config: AIModelConfig) {
        // 先将所有模型设为非默认
        modelConfigs.forEachIndexed { index, model ->
            if (model.isDefault) {
                modelConfigs[index] = model.copy(isDefault = false)
            }
        }
        
        // 设置选中的模型为默认
        val index = modelConfigs.indexOf(config)
        if (index != -1) {
            modelConfigs[index] = config.copy(isDefault = true)
        }
        
        adapter.notifyDataSetChanged()
        saveModelConfigs()
        
        Toast.makeText(this, "已设置 ${config.name} 为默认模型", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 模型配置适配器
 */
class ModelConfigAdapter(
    private val configs: List<AIModelConfig>,
    private val onAction: (AIModelConfig, ModelAction) -> Unit
) : RecyclerView.Adapter<ModelConfigAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemModelConfigBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemModelConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = configs[position]
        
        with(holder.binding) {
            modelName.text = config.name
            modelProvider.text = config.provider.displayName
            modelUrl.text = config.baseUrl
            
            // 显示状态
            statusIcon.setImageResource(
                if (config.isEnabled) R.drawable.ic_check_circle_24 else R.drawable.ic_cancel_24
            )
            
            // 显示默认标识
            defaultBadge.isVisible = config.isDefault
            
            // 按钮事件
            editButton.setOnClickListener { onAction(config, ModelAction.EDIT) }
            deleteButton.setOnClickListener { onAction(config, ModelAction.DELETE) }
            testButton.setOnClickListener { onAction(config, ModelAction.TEST) }
            toggleButton.setOnClickListener { onAction(config, ModelAction.TOGGLE) }
            
            root.setOnLongClickListener {
                onAction(config, ModelAction.SET_DEFAULT)
                true
            }
        }
    }

    override fun getItemCount() = configs.size
}

enum class ModelAction {
    EDIT, DELETE, TEST, TOGGLE, SET_DEFAULT
}