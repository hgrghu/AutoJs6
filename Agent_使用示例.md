# AutoJs6 Agent 使用示例

## 🚀 快速开始

### 1. 初始化Agent系统

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var agent: AutoJs6Agent
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 获取Agent实例
        agent = AutoJs6Agent.getInstance(this)
        
        // 异步初始化
        lifecycleScope.launch {
            val success = agent.initialize()
            if (success) {
                showToast("Agent初始化成功")
                enableAgentFeatures()
            } else {
                showToast("Agent初始化失败")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        agent.cleanup()
    }
}
```

### 2. 自然语言执行任务

```kotlin
// 基础示例：点击按钮
lifecycleScope.launch {
    val result = agent.executeNaturalLanguageTask(
        description = "点击登录按钮"
    )
    
    when (result) {
        is AgentResult.Success -> {
            println("任务执行成功")
            println("生成的脚本：\n${result.script.content}")
        }
        
        is AgentResult.Error -> {
            println("任务执行失败：${result.error.message}")
            println("建议：${result.suggestions}")
        }
    }
}

// 复杂示例：填写表单
lifecycleScope.launch {
    val result = agent.executeNaturalLanguageTask(
        description = "在用户名输入框输入'admin'，在密码框输入'123456'，然后点击登录",
        options = AgentExecutionOptions(
            autoExecute = true,  // 自动执行生成的脚本
            requireUIAnalysis = true  // 需要UI分析
        )
    )
    
    handleAgentResult(result)
}
```

### 3. UI界面分析

```kotlin
// 分析当前界面
fun analyzeCurrentScreen() {
    lifecycleScope.launch {
        try {
            val analysis = agent.analyzeCurrentUI()
            
            println("发现${analysis.elements.size}个UI元素")
            
            // 显示分析结果
            showUIAnalysisDialog(analysis)
            
            // 显示生成的操作代码
            showGeneratedCode(analysis.generatedCode)
            
        } catch (e: AgentException) {
            showError("UI分析失败: ${e.message}")
        }
    }
}

private fun showUIAnalysisDialog(analysis: UIAnalysisReport) {
    val dialog = AlertDialog.Builder(this)
        .setTitle("UI分析结果")
        .setMessage(buildString {
            appendLine("界面元素数量: ${analysis.elements.size}")
            appendLine("分析置信度: ${(analysis.confidence * 100).toInt()}%")
            appendLine("\n操作建议:")
            analysis.suggestedActions.forEach { suggestion ->
                appendLine("• ${suggestion.description}")
            }
        })
        .setPositiveButton("查看代码") { _, _ ->
            showCodeDialog(analysis.generatedCode)
        }
        .setNegativeButton("关闭", null)
        .create()
    
    dialog.show()
}
```

### 4. 学习模式

```kotlin
// 开始学习用户操作
fun startLearningMode() {
    lifecycleScope.launch {
        val session = agent.startLearningMode()
        
        showToast("学习模式已启动，会话ID: ${session.id}")
        
        // 在这里可以记录用户的操作
        // Agent会自动学习操作模式
    }
}

// 查看执行历史
fun showExecutionHistory() {
    lifecycleScope.launch {
        val history = agent.getExecutionHistory(limit = 20)
        
        val adapter = ExecutionHistoryAdapter(history)
        recyclerView.adapter = adapter
    }
}
```

### 5. 脚本优化

```kotlin
// 优化现有脚本
fun optimizeScript(originalScript: String) {
    lifecycleScope.launch {
        try {
            val optimizedScript = agent.optimizeScript(originalScript)
            
            showOptimizationResult(
                original = originalScript,
                optimized = optimizedScript.optimizedScript,
                improvements = optimizedScript.optimizations,
                estimatedImprovement = optimizedScript.estimatedImprovement
            )
            
        } catch (e: AgentException) {
            showError("脚本优化失败: ${e.message}")
        }
    }
}

private fun showOptimizationResult(
    original: String,
    optimized: String,
    improvements: List<OptimizationSuggestion>,
    estimatedImprovement: Float
) {
    val dialog = AlertDialog.Builder(this)
        .setTitle("脚本优化结果")
        .setMessage(buildString {
            appendLine("预估性能提升: ${estimatedImprovement.toInt()}%")
            appendLine("\n优化项目:")
            improvements.forEach { suggestion ->
                appendLine("• ${suggestion.description} (${suggestion.impact})")
            }
        })
        .setPositiveButton("查看优化后代码") { _, _ ->
            showCodeComparison(original, optimized)
        }
        .setNegativeButton("关闭", null)
        .create()
    
    dialog.show()
}
```

## 🎨 UI集成示例

### Agent控制面板Activity

```kotlin
class AgentControlActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAgentControlBinding
    private lateinit var agent: AutoJs6Agent
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentControlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        agent = AutoJs6Agent.getInstance(this)
        setupUI()
        checkAgentStatus()
    }
    
    private fun setupUI() {
        // 自然语言输入
        binding.executeButton.setOnClickListener {
            val description = binding.taskInput.text.toString()
            if (description.isNotBlank()) {
                executeTask(description)
            }
        }
        
        // 语音输入按钮
        binding.voiceInputButton.setOnClickListener {
            startVoiceRecognition()
        }
        
        // UI分析按钮
        binding.analyzeUIButton.setOnClickListener {
            analyzeCurrentScreen()
        }
        
        // 学习模式开关
        binding.learningModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startLearningMode()
            } else {
                stopLearningMode()
            }
        }
        
        // 历史记录按钮
        binding.historyButton.setOnClickListener {
            showExecutionHistory()
        }
    }
    
    private fun executeTask(description: String) {
        binding.progressBar.isVisible = true
        binding.executeButton.isEnabled = false
        
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
                showError("执行失败: ${e.message}")
            } finally {
                binding.progressBar.isVisible = false
                binding.executeButton.isEnabled = true
            }
        }
    }
    
    private fun handleExecutionResult(result: AgentResult) {
        when (result) {
            is AgentResult.Success -> {
                binding.resultText.text = "✅ 任务执行成功"
                binding.scriptPreview.text = result.script.content
                
                // 显示执行结果详情
                if (result.executionResult != null) {
                    showExecutionDetails(result.executionResult)
                }
            }
            
            is AgentResult.Error -> {
                binding.resultText.text = "❌ 任务执行失败"
                showErrorDialog(result.error.message ?: "未知错误", result.suggestions)
            }
        }
    }
    
    private fun checkAgentStatus() {
        lifecycleScope.launch {
            val status = agent.getAgentStatus()
            updateStatusDisplay(status)
        }
    }
    
    private fun updateStatusDisplay(status: AgentStatus) {
        binding.statusText.text = buildString {
            appendLine("Agent状态: ${if (status.isInitialized) "已初始化" else "未初始化"}")
            appendLine("本地模型: ${if (status.localModelsStatus.isLoaded) "已加载" else "未加载"}")
            appendLine("云端服务: ${if (status.cloudServiceStatus.isAvailable) "可用" else "不可用"}")
            appendLine("内存使用: ${status.memoryUsage.getAppMemoryUsagePercentage().toInt()}%")
            appendLine("活跃会话: ${status.activeSessionsCount}")
        }
    }
}
```

### 布局文件示例

```xml
<!-- activity_agent_control.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">
    
    <!-- Agent状态显示 -->
    <TextView
        android:id="@+id/statusText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/status_background"
        android:padding="12dp"
        android:textSize="12sp" />
    
    <!-- 任务输入区域 -->
    <com.google.android.material.textfield.TextInputLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp">
        
        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/taskInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="请描述您要执行的任务..."
            android:maxLines="3" />
            
    </com.google.android.material.textfield.TextInputLayout>
    
    <!-- 控制按钮 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:orientation="horizontal">
        
        <Button
            android:id="@+id/executeButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="执行任务" />
            
        <ImageButton
            android:id="@+id/voiceInputButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:src="@drawable/ic_mic" />
            
    </LinearLayout>
    
    <!-- 功能选项 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:orientation="horizontal">
        
        <Switch
            android:id="@+id/autoExecuteSwitch"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="自动执行" />
            
        <Switch
            android:id="@+id/learningModeSwitch"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:text="学习模式" />
            
    </LinearLayout>
    
    <!-- 功能按钮 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:orientation="horizontal">
        
        <Button
            android:id="@+id/analyzeUIButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="分析界面"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton" />
            
        <Button
            android:id="@+id/historyButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:text="执行历史"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton" />
            
    </LinearLayout>
    
    <!-- 进度条 -->
    <ProgressBar
        android:id="@+id/progressBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:visibility="gone"
        style="?android:attr/progressBarStyleHorizontal" />
    
    <!-- 结果显示 -->
    <TextView
        android:id="@+id/resultText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:padding="12dp"
        android:background="@drawable/result_background" />
    
    <!-- 脚本预览 -->
    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginTop="16dp">
        
        <TextView
            android:id="@+id/scriptPreview"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@drawable/code_background"
            android:fontFamily="monospace"
            android:padding="12dp"
            android:textSize="12sp" />
            
    </ScrollView>
    
</LinearLayout>
```

## 🔧 高级用法

### 自定义Agent行为

```kotlin
// 自定义脚本生成选项
val customOptions = ScriptGenerationOptions(
    addComments = true,
    addErrorHandling = true,
    optimizePerformance = true,
    includeWaitMechanisms = false  // 禁用自动等待
)

// 使用自定义选项执行任务
val result = agent.executeNaturalLanguageTask(
    description = "快速点击所有按钮",
    options = AgentExecutionOptions(
        scriptOptions = customOptions,
        timeout = 60000L  // 设置超时时间
    )
)
```

### 批量任务处理

```kotlin
// 批量执行多个任务
val tasks = listOf(
    "打开设置应用",
    "进入WiFi设置",
    "连接到指定网络"
)

tasks.forEach { task ->
    lifecycleScope.launch {
        val result = agent.executeNaturalLanguageTask(task)
        handleTaskResult(task, result)
    }
}
```

### 集成到现有workflow

```kotlin
// 与现有的AutoJs6脚本系统集成
fun integrateWithExistingScript(existingScript: String, userDescription: String) {
    lifecycleScope.launch {
        // 使用Agent分析用户需求
        val result = agent.executeNaturalLanguageTask(
            description = userDescription,
            options = AgentExecutionOptions(autoExecute = false)
        )
        
        if (result is AgentResult.Success) {
            // 将Agent生成的脚本与现有脚本合并
            val combinedScript = """
                $existingScript
                
                // Agent生成的补充功能
                ${result.script.content}
            """.trimIndent()
            
            // 执行合并后的脚本
            executeScript(combinedScript)
        }
    }
}
```

## 📋 最佳实践

1. **初始化检查**：始终检查Agent初始化状态
2. **错误处理**：妥善处理Agent执行异常
3. **资源管理**：在适当时机清理Agent资源
4. **用户反馈**：为长时间运行的任务提供进度反馈
5. **权限管理**：确保必要的权限已授予
6. **性能监控**：定期检查Agent状态和内存使用

这个Agent系统为AutoJs6提供了强大的AI能力，让自动化脚本的创建和执行变得更加智能和便捷！