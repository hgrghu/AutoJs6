package org.autojs.autojs.ui.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.autojs.autojs.R
import org.autojs.autojs.core.agent.*
import org.autojs.autojs.databinding.ActivityScriptEditorAgentBinding
import org.autojs.autojs.ui.BaseActivity
import java.io.IOException

/**
 * 脚本编辑器 - 集成Agent监控功能
 * 
 * 功能：
 * 1. 代码编辑和高亮
 * 2. Agent智能监控和自动修复
 * 3. 图片/文本参考输入
 * 4. 实时执行状态监控
 * 5. 修改历史查看
 */
class ScriptEditorAgentActivity : BaseActivity() {

    private lateinit var binding: ActivityScriptEditorAgentBinding
    private lateinit var scriptMonitorAgent: ScriptMonitorAgent
    
    // Agent相关状态
    private var isAgentEnabled = false
    private var currentReference: UserReference? = null
    private var currentSessionId: String? = null
    private var referenceImage: Bitmap? = null
    
    // 适配器
    private lateinit var modificationsAdapter: ScriptModificationsAdapter
    
    // Activity结果启动器
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScriptEditorAgentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupAgent()
        setupActivityLaunchers()
        observeAgentStatus()
    }

    private fun setupUI() {
        // 设置工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "智能脚本编辑器"
        }
        
        // 初始化修改历史RecyclerView
        modificationsAdapter = ScriptModificationsAdapter { modification ->
            showModificationDetails(modification)
        }
        
        binding.recyclerModifications.apply {
            layoutManager = LinearLayoutManager(this@ScriptEditorAgentActivity)
            adapter = modificationsAdapter
        }
        
        // 设置默认代码模板
        binding.editScript.setText(getDefaultScriptTemplate())
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Agent开关
        binding.switchAgent.setOnCheckedChangeListener { _, isChecked ->
            isAgentEnabled = isChecked
            binding.layoutAgentOptions.visibility = if (isChecked) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
        
        // 添加文本参考
        binding.btnAddTextReference.setOnClickListener {
            showTextReferenceDialog()
        }
        
        // 添加图片参考
        binding.btnAddImageReference.setOnClickListener {
            checkPermissionAndPickImage()
        }
        
        // 清除参考
        binding.btnClearReference.setOnClickListener {
            clearReference()
        }
        
        // 执行脚本
        binding.btnExecuteScript.setOnClickListener {
            executeScriptWithAgent()
        }
        
        // 停止监控
        binding.btnStopMonitoring.setOnClickListener {
            stopCurrentMonitoring()
        }
        
        // 查看当前屏幕分析
        binding.btnAnalyzeScreen.setOnClickListener {
            analyzeCurrentScreen()
        }
        
        // 切换修改历史面板
        binding.btnToggleHistory.setOnClickListener {
            toggleModificationHistory()
        }
    }

    private fun setupAgent() {
        scriptMonitorAgent = ScriptMonitorAgent.getInstance(this)
    }

    private fun setupActivityLaunchers() {
        // 图片选择器
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    handleImageSelected(uri)
                }
            }
        }
        
        // 权限请求
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                pickImage()
            } else {
                Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeAgentStatus() {
        lifecycleScope.launch {
            scriptMonitorAgent.monitoringStatus.observe(this@ScriptEditorAgentActivity) { status ->
                updateMonitoringStatus(status)
            }
        }
    }

    // =============== 脚本执行相关 ===============

    private fun executeScriptWithAgent() {
        val scriptContent = binding.editScript.text.toString().trim()
        
        if (scriptContent.isEmpty()) {
            Toast.makeText(this, "请输入脚本内容", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 停止之前的监控
        currentSessionId?.let { scriptMonitorAgent.stopScriptMonitoring(it) }
        
        if (isAgentEnabled) {
            // 使用Agent监控执行
            executeWithAgentMonitoring(scriptContent)
        } else {
            // 直接执行
            executeScriptDirectly(scriptContent)
        }
    }

    private fun executeWithAgentMonitoring(scriptContent: String) {
        val reference = currentReference
        if (reference == null) {
            Toast.makeText(this, "启用Agent时请先添加文本描述或参考图片", Toast.LENGTH_SHORT).show()
            return
        }
        
        val options = MonitoringOptions(
            enableAutoFix = true,
            maxRetryAttempts = 3,
            screenshotOnFailure = true,
            saveOptimizedScript = true
        )
        
        currentSessionId = scriptMonitorAgent.startScriptMonitoring(
            scriptContent = scriptContent,
            userReference = reference,
            options = options
        )
        
        updateUIForMonitoring(true)
        binding.textMonitoringStatus.text = "正在执行脚本并监控..."
    }

    private fun executeScriptDirectly(scriptContent: String) {
        lifecycleScope.launch {
            try {
                binding.textMonitoringStatus.text = "正在执行脚本..."
                updateUIForMonitoring(true)
                
                // TODO: 使用OptimizedScriptExecutor直接执行
                // val executor = OptimizedScriptExecutor()
                // val result = executor.executeScript(scriptContent)
                
                // 模拟执行
                kotlinx.coroutines.delay(2000)
                
                binding.textMonitoringStatus.text = "脚本执行完成"
                updateUIForMonitoring(false)
                
            } catch (e: Exception) {
                binding.textMonitoringStatus.text = "执行失败: ${e.message}"
                updateUIForMonitoring(false)
            }
        }
    }

    private fun stopCurrentMonitoring() {
        currentSessionId?.let { sessionId ->
            scriptMonitorAgent.stopScriptMonitoring(sessionId)
            updateUIForMonitoring(false)
            binding.textMonitoringStatus.text = "监控已停止"
        }
    }

    // =============== 参考信息管理 ===============

    private fun showTextReferenceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_text_reference, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.edit_reference_text)
        
        // 如果已有文本参考，显示当前内容
        if (currentReference is UserReference.TextDescription) {
            editText.setText((currentReference as UserReference.TextDescription).description)
        } else if (currentReference is UserReference.Mixed) {
            editText.setText((currentReference as UserReference.Mixed).description)
        }
        
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        
        dialogView.findViewById<android.widget.Button>(R.id.btn_confirm).setOnClickListener {
            val text = editText.text.toString().trim()
            if (text.isNotEmpty()) {
                updateTextReference(text)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "请输入参考描述", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialogView.findViewById<android.widget.Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun checkPermissionAndPickImage() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        val needsPermission = permissions.any { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needsPermission) {
            permissionLauncher.launch(permissions)
        } else {
            pickImage()
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun handleImageSelected(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            referenceImage = bitmap
            
            // 更新参考信息
            when (val current = currentReference) {
                is UserReference.TextDescription -> {
                    currentReference = UserReference.Mixed(current.description, bitmap)
                }
                is UserReference.Mixed -> {
                    currentReference = UserReference.Mixed(current.description, bitmap)
                }
                else -> {
                    currentReference = UserReference.ImageReference(bitmap)
                }
            }
            
            updateReferenceDisplay()
            Toast.makeText(this, "参考图片已添加", Toast.LENGTH_SHORT).show()
            
        } catch (e: IOException) {
            Toast.makeText(this, "图片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTextReference(text: String) {
        currentReference = when (val current = currentReference) {
            is UserReference.ImageReference -> {
                UserReference.Mixed(text, current.image)
            }
            is UserReference.Mixed -> {
                UserReference.Mixed(text, current.image)
            }
            else -> {
                UserReference.TextDescription(text)
            }
        }
        
        updateReferenceDisplay()
    }

    private fun clearReference() {
        currentReference = null
        referenceImage = null
        updateReferenceDisplay()
        Toast.makeText(this, "参考信息已清除", Toast.LENGTH_SHORT).show()
    }

    private fun updateReferenceDisplay() {
        when (val reference = currentReference) {
            is UserReference.TextDescription -> {
                binding.textReferenceInfo.text = "文本描述: ${reference.description}"
                binding.imageReference.visibility = android.view.View.GONE
            }
            is UserReference.ImageReference -> {
                binding.textReferenceInfo.text = "参考图片已添加"
                binding.imageReference.setImageBitmap(reference.image)
                binding.imageReference.visibility = android.view.View.VISIBLE
            }
            is UserReference.Mixed -> {
                binding.textReferenceInfo.text = "文本 + 图片: ${reference.description}"
                binding.imageReference.setImageBitmap(reference.image)
                binding.imageReference.visibility = android.view.View.VISIBLE
            }
            null -> {
                binding.textReferenceInfo.text = "未添加参考信息"
                binding.imageReference.visibility = android.view.View.GONE
            }
        }
    }

    // =============== 监控状态更新 ===============

    private fun updateMonitoringStatus(status: MonitoringStatus) {
        when (status) {
            is MonitoringStatus.Success -> {
                binding.textMonitoringStatus.text = status.message
                binding.progressMonitoring.visibility = android.view.View.GONE
                updateUIForMonitoring(false)
                
                // 更新修改历史
                currentSessionId?.let { sessionId ->
                    updateModificationHistory(sessionId)
                }
            }
            
            is MonitoringStatus.Analyzing -> {
                binding.textMonitoringStatus.text = status.message
                binding.progressMonitoring.visibility = android.view.View.VISIBLE
                
                // 更新修改历史
                currentSessionId?.let { sessionId ->
                    updateModificationHistory(sessionId)
                }
            }
            
            is MonitoringStatus.Error -> {
                binding.textMonitoringStatus.text = status.message
                binding.progressMonitoring.visibility = android.view.View.GONE
                updateUIForMonitoring(false)
                Toast.makeText(this, "执行出错: ${status.error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUIForMonitoring(isMonitoring: Boolean) {
        binding.btnExecuteScript.isEnabled = !isMonitoring
        binding.btnStopMonitoring.visibility = if (isMonitoring) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        binding.progressMonitoring.visibility = if (isMonitoring) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun updateModificationHistory(sessionId: String) {
        val session = scriptMonitorAgent.getMonitoringSession(sessionId)
        session?.let {
            modificationsAdapter.updateModifications(it.modifications)
            
            // 如果有修改，更新脚本内容为最新版本
            if (it.modifications.isNotEmpty()) {
                val latestScript = it.currentScript ?: it.originalScript
                if (latestScript != binding.editScript.text.toString()) {
                    binding.editScript.setText(latestScript)
                }
            }
        }
    }

    // =============== 其他功能 ===============

    private fun analyzeCurrentScreen() {
        val intent = Intent(this, UIAnalyzerActivity::class.java)
        startActivity(intent)
    }

    private fun toggleModificationHistory() {
        val isVisible = binding.layoutModificationHistory.visibility == android.view.View.VISIBLE
        binding.layoutModificationHistory.visibility = if (isVisible) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
        
        binding.btnToggleHistory.text = if (isVisible) "显示修改历史" else "隐藏修改历史"
    }

    private fun showModificationDetails(modification: ScriptModification) {
        val intent = Intent(this, ScriptDiffViewActivity::class.java).apply {
            putExtra("original_code", modification.originalCode)
            putExtra("modified_code", modification.modifiedCode)
            putExtra("reason", modification.reason)
            putExtra("attempt_number", modification.attemptNumber)
        }
        startActivity(intent)
    }

    private fun getDefaultScriptTemplate(): String = """
// AutoJs6 脚本模板
// 启用Agent后，模型会根据您的描述和参考图片自动分析和修复脚本

// 等待界面加载
sleep(2000);

// 示例：点击按钮
// text("确定").findOne().click();

// 示例：输入文本
// id("editText").findOne().setText("Hello World");

// 示例：滑动操作
// swipe(500, 1000, 500, 500, 1000);

// 提示：启用Agent后，如果脚本执行失败，AI会自动：
// 1. 截图分析当前界面
// 2. 检测可用的UI元素
// 3. 重新生成优化的代码
// 4. 修正坐标和选择器
""".trimIndent()

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止当前监控
        currentSessionId?.let { scriptMonitorAgent.stopScriptMonitoring(it) }
    }
}

/**
 * 脚本修改历史适配器
 */
class ScriptModificationsAdapter(
    private val onItemClick: (ScriptModification) -> Unit
) : RecyclerView.Adapter<ScriptModificationsAdapter.ViewHolder>() {

    private var modifications = listOf<ScriptModification>()

    fun updateModifications(newModifications: List<ScriptModification>) {
        modifications = newModifications
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_script_modification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(modifications[position])
    }

    override fun getItemCount() = modifications.size

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val textAttempt: android.widget.TextView = itemView.findViewById(R.id.text_attempt)
        private val textReason: android.widget.TextView = itemView.findViewById(R.id.text_reason)
        private val textTime: android.widget.TextView = itemView.findViewById(R.id.text_time)

        fun bind(modification: ScriptModification) {
            textAttempt.text = "第${modification.attemptNumber}次修改"
            textReason.text = modification.reason
            textTime.text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(modification.timestamp))
            
            itemView.setOnClickListener {
                onItemClick(modification)
            }
        }
    }
}