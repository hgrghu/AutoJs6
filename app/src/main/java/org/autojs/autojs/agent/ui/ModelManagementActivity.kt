package org.autojs.autojs.agent.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.autojs.autojs.R
import org.autojs.autojs.agent.api.*
import org.autojs.autojs.agent.github.GitHubManager
import java.util.*

/**
 * 模型管理Activity
 * 提供完整的AI模型管理功能
 */
class ModelManagementActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var modelAdapter: ModelAdapter
    private lateinit var modelManager: ModelManager
    private lateinit var githubManager: GitHubManager
    
    private val models = mutableListOf<AIModel>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_management)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI模型管理"
        
        initViews()
        initManagers()
        loadModels()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        fab = findViewById(R.id.fab)
        
        modelAdapter = ModelAdapter(models) { model, action ->
            when (action) {
                ModelAction.EDIT -> editModel(model)
                ModelAction.DELETE -> deleteModel(model)
                ModelAction.TEST -> testModel(model)
                ModelAction.DUPLICATE -> duplicateModel(model)
            }
        }
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ModelManagementActivity)
            adapter = modelAdapter
            addItemDecoration(DividerItemDecoration(this@ModelManagementActivity, DividerItemDecoration.VERTICAL))
        }
        
        fab.setOnClickListener {
            showAddModelDialog()
        }
    }
    
    private fun initManagers() {
        modelManager = ModelManager.getInstance(this)
        githubManager = GitHubManager.getInstance(this)
    }
    
    private fun loadModels() {
        models.clear()
        models.addAll(modelManager.getAllModels())
        modelAdapter.notifyDataSetChanged()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_model_management, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_import -> {
                showImportDialog()
                true
            }
            R.id.action_export -> {
                exportModels()
                true
            }
            R.id.action_github_config -> {
                showGitHubConfigDialog()
                true
            }
            R.id.action_sync_to_github -> {
                syncModelsToGitHub()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showAddModelDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_model, null)
        setupModelDialog(dialogView, null) { model ->
            lifecycleScope.launch {
                val success = modelManager.addCustomModel(model)
                if (success) {
                    loadModels()
                    showSnackbar("模型添加成功")
                } else {
                    showSnackbar("模型添加失败")
                }
            }
        }
    }
    
    private fun editModel(model: AIModel) {
        if (!model.isCustom) {
            showSnackbar("预定义模型无法编辑")
            return
        }
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_model, null)
        setupModelDialog(dialogView, model) { updatedModel ->
            lifecycleScope.launch {
                val success = modelManager.updateCustomModel(updatedModel)
                if (success) {
                    loadModels()
                    showSnackbar("模型更新成功")
                } else {
                    showSnackbar("模型更新失败")
                }
            }
        }
    }
    
    private fun setupModelDialog(
        dialogView: View, 
        existingModel: AIModel?, 
        onSave: (AIModel) -> Unit
    ) {
        val etId = dialogView.findViewById<TextInputEditText>(R.id.etId)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etProvider = dialogView.findViewById<TextInputEditText>(R.id.etProvider)
        val etBaseUrl = dialogView.findViewById<TextInputEditText>(R.id.etBaseUrl)
        val etModelName = dialogView.findViewById<TextInputEditText>(R.id.etModelName)
        val etMaxTokens = dialogView.findViewById<TextInputEditText>(R.id.etMaxTokens)
        val etTemperature = dialogView.findViewById<TextInputEditText>(R.id.etTemperature)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val chipGroupApiType = dialogView.findViewById<ChipGroup>(R.id.chipGroupApiType)
        val switchVision = dialogView.findViewById<Switch>(R.id.switchVision)
        
        // 设置API类型选择器
        APIType.values().forEach { apiType ->
            val chip = Chip(this).apply {
                text = apiType.name
                isCheckable = true
                tag = apiType
            }
            chipGroupApiType.addView(chip)
        }
        
        // 如果是编辑模式，填充现有数据
        existingModel?.let { model ->
            etId.setText(model.id)
            etName.setText(model.name)
            etProvider.setText(model.provider)
            etBaseUrl.setText(model.baseUrl)
            etModelName.setText(model.modelName)
            etMaxTokens.setText(model.maxTokens.toString())
            etTemperature.setText(model.temperature.toString())
            etDescription.setText(model.description)
            switchVision.isChecked = model.supportVision
            
            // 选中对应的API类型
            for (i in 0 until chipGroupApiType.childCount) {
                val chip = chipGroupApiType.getChildAt(i) as Chip
                if (chip.tag == model.apiType) {
                    chip.isChecked = true
                    break
                }
            }
            
            etId.isEnabled = false // 编辑时不允许修改ID
        } else {
            // 新增模式，设置默认值
            etId.setText("custom-${UUID.randomUUID().toString().substring(0, 8)}")
            etMaxTokens.setText("2048")
            etTemperature.setText("0.3")
        }
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existingModel != null) "编辑模型" else "添加模型")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                try {
                    val selectedApiType = chipGroupApiType.checkedChipIds.firstOrNull()?.let { chipId ->
                        chipGroupApiType.findViewById<Chip>(chipId).tag as APIType
                    } ?: APIType.OPENAI_COMPATIBLE
                    
                    val model = AIModel(
                        id = etId.text.toString().trim(),
                        name = etName.text.toString().trim(),
                        provider = etProvider.text.toString().trim(),
                        baseUrl = etBaseUrl.text.toString().trim(),
                        apiType = selectedApiType,
                        modelName = etModelName.text.toString().trim(),
                        maxTokens = etMaxTokens.text.toString().toIntOrNull() ?: 2048,
                        temperature = etTemperature.text.toString().toFloatOrNull() ?: 0.3f,
                        supportVision = switchVision.isChecked,
                        description = etDescription.text.toString().trim(),
                        isCustom = true
                    )
                    
                    onSave(model)
                } catch (e: Exception) {
                    showSnackbar("保存失败: ${e.message}")
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        dialog.show()
    }
    
    private fun duplicateModel(model: AIModel) {
        val newModel = model.copy(
            id = "copy-${model.id}-${UUID.randomUUID().toString().substring(0, 8)}",
            name = "${model.name} (副本)",
            isCustom = true
        )
        
        lifecycleScope.launch {
            val success = modelManager.addCustomModel(newModel)
            if (success) {
                loadModels()
                showSnackbar("模型复制成功")
            } else {
                showSnackbar("模型复制失败")
            }
        }
    }
    
    private fun deleteModel(model: AIModel) {
        if (!model.isCustom) {
            showSnackbar("预定义模型无法删除")
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("删除模型")
            .setMessage("确定要删除模型 '${model.name}' 吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val success = modelManager.deleteCustomModel(model.id)
                    if (success) {
                        loadModels()
                        showSnackbar("模型删除成功")
                    } else {
                        showSnackbar("模型删除失败")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun testModel(model: AIModel) {
        val apiKeyDialog = AlertDialog.Builder(this)
            .setTitle("测试模型连接")
            .setMessage("请输入API密钥以测试模型连接")
        
        val apiKeyInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "API Key"
        }
        
        apiKeyDialog.setView(apiKeyInput)
        apiKeyDialog.setPositiveButton("测试") { _, _ ->
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                testModelConnection(model, apiKey)
            } else {
                showSnackbar("请输入有效的API密钥")
            }
        }
        apiKeyDialog.setNegativeButton("取消", null)
        apiKeyDialog.show()
    }
    
    private fun testModelConnection(model: AIModel, apiKey: String) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("测试连接")
            .setMessage("正在测试模型连接...")
            .setCancelable(false)
            .create()
        
        progressDialog.show()
        
        lifecycleScope.launch {
            try {
                val result = modelManager.testModelConnection(model, apiKey)
                progressDialog.dismiss()
                
                val resultDialog = MaterialAlertDialogBuilder(this@ModelManagementActivity)
                    .setTitle("测试结果")
                    .setMessage(
                        if (result.success) {
                            "连接成功！\n响应时间: ${result.responseTime}ms"
                        } else {
                            "连接失败: ${result.message}"
                        }
                    )
                    .setPositiveButton("确定", null)
                
                if (result.success && result.modelResponse != null) {
                    resultDialog.setNeutralButton("查看响应") { _, _ ->
                        showModelResponse(result.modelResponse)
                    }
                }
                
                resultDialog.show()
                
            } catch (e: Exception) {
                progressDialog.dismiss()
                showSnackbar("测试失败: ${e.message}")
            }
        }
    }
    
    private fun showModelResponse(response: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("模型响应")
            .setMessage(response)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showImportDialog() {
        val input = EditText(this).apply {
            hint = "粘贴模型配置JSON"
            minLines = 5
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("导入模型配置")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val jsonData = input.text.toString().trim()
                if (jsonData.isNotEmpty()) {
                    importModels(jsonData)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun importModels(jsonData: String) {
        lifecycleScope.launch {
            try {
                val result = modelManager.importModels(jsonData)
                if (result.success) {
                    loadModels()
                    showSnackbar("导入成功: ${result.importedCount}个模型")
                } else {
                    showSnackbar("导入失败: ${result.error}")
                }
            } catch (e: Exception) {
                showSnackbar("导入失败: ${e.message}")
            }
        }
    }
    
    private fun exportModels() {
        try {
            val exportData = modelManager.exportModels()
            
            // 创建分享Intent
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, exportData)
                type = "text/plain"
            }
            
            startActivity(Intent.createChooser(shareIntent, "导出模型配置"))
            
        } catch (e: Exception) {
            showSnackbar("导出失败: ${e.message}")
        }
    }
    
    private fun showGitHubConfigDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_github_config, null)
        
        val etToken = dialogView.findViewById<TextInputEditText>(R.id.etToken)
        val etOwner = dialogView.findViewById<TextInputEditText>(R.id.etOwner)
        val etRepo = dialogView.findViewById<TextInputEditText>(R.id.etRepo)
        val etBranch = dialogView.findViewById<TextInputEditText>(R.id.etBranch)
        val switchAutoSync = dialogView.findViewById<Switch>(R.id.switchAutoSync)
        
        // 填充现有配置
        etToken.setText(githubManager.getAccessToken())
        githubManager.getDefaultRepo()?.let { (owner, repo) ->
            etOwner.setText(owner)
            etRepo.setText(repo)
        }
        etBranch.setText(githubManager.getDefaultBranch())
        switchAutoSync.isChecked = githubManager.isAutoSyncEnabled()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("GitHub配置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                // 保存配置
                githubManager.setAccessToken(etToken.text.toString().trim())
                
                val owner = etOwner.text.toString().trim()
                val repo = etRepo.text.toString().trim()
                if (owner.isNotEmpty() && repo.isNotEmpty()) {
                    githubManager.setDefaultRepo(owner, repo)
                }
                
                val branch = etBranch.text.toString().trim()
                if (branch.isNotEmpty()) {
                    githubManager.setDefaultBranch(branch)
                }
                
                githubManager.setAutoSync(switchAutoSync.isChecked)
                
                showSnackbar("GitHub配置已保存")
            }
            .setNeutralButton("测试连接") { _, _ ->
                testGitHubConnection()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun testGitHubConnection() {
        lifecycleScope.launch {
            try {
                val result = githubManager.testConnection()
                val message = if (result.success) {
                    val userInfo = result.userInfo
                    "连接成功！\n用户: ${userInfo?.login}\n公开仓库: ${userInfo?.publicRepos}"
                } else {
                    "连接失败: ${result.message}"
                }
                
                MaterialAlertDialogBuilder(this@ModelManagementActivity)
                    .setTitle("GitHub连接测试")
                    .setMessage(message)
                    .setPositiveButton("确定", null)
                    .show()
                    
            } catch (e: Exception) {
                showSnackbar("测试失败: ${e.message}")
            }
        }
    }
    
    private fun syncModelsToGitHub() {
        lifecycleScope.launch {
            try {
                val configData = modelManager.exportModels()
                val result = githubManager.pushModelConfig(
                    configContent = configData,
                    commitMessage = "Sync model config from AutoJs6 Agent"
                )
                
                if (result.success) {
                    showSnackbar("模型配置同步成功")
                } else {
                    showSnackbar("同步失败: ${result.message}")
                }
            } catch (e: Exception) {
                showSnackbar("同步失败: ${e.message}")
            }
        }
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(recyclerView, message, Snackbar.LENGTH_LONG).show()
    }
}

/**
 * 模型列表适配器
 */
class ModelAdapter(
    private val models: List<AIModel>,
    private val onAction: (AIModel, ModelAction) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvProvider: TextView = view.findViewById(R.id.tvProvider)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val chipApiType: Chip = view.findViewById(R.id.chipApiType)
        val chipCustom: Chip = view.findViewById(R.id.chipCustom)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val btnTest: ImageButton = view.findViewById(R.id.btnTest)
        val btnMore: ImageButton = view.findViewById(R.id.btnMore)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        
        holder.tvName.text = model.name
        holder.tvProvider.text = model.provider
        holder.tvDescription.text = model.description.ifEmpty { "无描述" }
        holder.chipApiType.text = model.apiType.name
        holder.chipCustom.visibility = if (model.isCustom) View.VISIBLE else View.GONE
        
        // 设置按钮点击事件
        holder.btnEdit.setOnClickListener { onAction(model, ModelAction.EDIT) }
        holder.btnDelete.setOnClickListener { onAction(model, ModelAction.DELETE) }
        holder.btnTest.setOnClickListener { onAction(model, ModelAction.TEST) }
        
        holder.btnMore.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.popup_model_actions, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_duplicate -> {
                        onAction(model, ModelAction.DUPLICATE)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
        
        // 设置按钮可见性
        if (!model.isCustom) {
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        }
    }
    
    override fun getItemCount() = models.size
}

/**
 * 模型操作枚举
 */
enum class ModelAction {
    EDIT, DELETE, TEST, DUPLICATE
}