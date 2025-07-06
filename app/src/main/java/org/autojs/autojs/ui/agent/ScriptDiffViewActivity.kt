package org.autojs.autojs.ui.agent

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.ContextCompat
import org.autojs.autojs.R
import org.autojs.autojs.databinding.ActivityScriptDiffViewBinding
import org.autojs.autojs.ui.BaseActivity

/**
 * 脚本代码对比查看Activity
 * 
 * 功能：
 * 1. 显示修改前后的代码对比
 * 2. 高亮显示差异部分
 * 3. 显示修改原因和建议
 * 4. 支持代码复制
 */
class ScriptDiffViewActivity : BaseActivity() {

    private lateinit var binding: ActivityScriptDiffViewBinding
    
    private var originalCode: String = ""
    private var modifiedCode: String = ""
    private var reason: String = ""
    private var attemptNumber: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScriptDiffViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        loadIntentData()
        displayCodeDiff()
    }

    private fun setupUI() {
        // 设置工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "代码修改详情"
        }
        
        // 设置代码字体
        binding.textOriginalCode.typeface = android.graphics.Typeface.MONOSPACE
        binding.textModifiedCode.typeface = android.graphics.Typeface.MONOSPACE
    }

    private fun loadIntentData() {
        originalCode = intent.getStringExtra("original_code") ?: ""
        modifiedCode = intent.getStringExtra("modified_code") ?: ""
        reason = intent.getStringExtra("reason") ?: ""
        attemptNumber = intent.getIntExtra("attempt_number", 1)
        
        // 更新标题
        supportActionBar?.title = "第${attemptNumber}次修改详情"
    }

    private fun displayCodeDiff() {
        // 显示修改原因
        binding.textModificationReason.text = reason
        
        // 计算并显示代码差异
        val diffResult = calculateDiff(originalCode, modifiedCode)
        
        // 显示原始代码（带高亮）
        binding.textOriginalCode.text = highlightRemovedLines(originalCode, diffResult.removedLines)
        
        // 显示修改后代码（带高亮）
        binding.textModifiedCode.text = highlightAddedLines(modifiedCode, diffResult.addedLines)
        
        // 显示统计信息
        displayStatistics(diffResult)
    }

    /**
     * 计算代码差异
     */
    private fun calculateDiff(original: String, modified: String): DiffResult {
        val originalLines = original.lines()
        val modifiedLines = modified.lines()
        
        val removedLines = mutableSetOf<Int>()
        val addedLines = mutableSetOf<Int>()
        
        // 简单的差异算法（实际应用中可以使用更复杂的算法）
        var originalIndex = 0
        var modifiedIndex = 0
        
        while (originalIndex < originalLines.size || modifiedIndex < modifiedLines.size) {
            if (originalIndex >= originalLines.size) {
                // 原始代码已结束，剩余的都是新增行
                addedLines.add(modifiedIndex)
                modifiedIndex++
            } else if (modifiedIndex >= modifiedLines.size) {
                // 修改后代码已结束，剩余的都是删除行
                removedLines.add(originalIndex)
                originalIndex++
            } else {
                val originalLine = originalLines[originalIndex].trim()
                val modifiedLine = modifiedLines[modifiedIndex].trim()
                
                if (originalLine == modifiedLine) {
                    // 行相同，继续
                    originalIndex++
                    modifiedIndex++
                } else {
                    // 行不同，标记为删除和添加
                    removedLines.add(originalIndex)
                    addedLines.add(modifiedIndex)
                    originalIndex++
                    modifiedIndex++
                }
            }
        }
        
        return DiffResult(removedLines, addedLines)
    }

    /**
     * 高亮删除的行
     */
    private fun highlightRemovedLines(code: String, removedLines: Set<Int>): SpannableStringBuilder {
        val lines = code.lines()
        val spannableBuilder = SpannableStringBuilder()
        
        lines.forEachIndexed { index, line ->
            val start = spannableBuilder.length
            spannableBuilder.append(line)
            
            if (removedLines.contains(index)) {
                val colorRemoved = ContextCompat.getColor(this, R.color.code_removed)
                spannableBuilder.setSpan(
                    BackgroundColorSpan(colorRemoved),
                    start,
                    spannableBuilder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            if (index < lines.size - 1) {
                spannableBuilder.append("\n")
            }
        }
        
        return spannableBuilder
    }

    /**
     * 高亮添加的行
     */
    private fun highlightAddedLines(code: String, addedLines: Set<Int>): SpannableStringBuilder {
        val lines = code.lines()
        val spannableBuilder = SpannableStringBuilder()
        
        lines.forEachIndexed { index, line ->
            val start = spannableBuilder.length
            spannableBuilder.append(line)
            
            if (addedLines.contains(index)) {
                val colorAdded = ContextCompat.getColor(this, R.color.code_added)
                spannableBuilder.setSpan(
                    BackgroundColorSpan(colorAdded),
                    start,
                    spannableBuilder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            if (index < lines.size - 1) {
                spannableBuilder.append("\n")
            }
        }
        
        return spannableBuilder
    }

    /**
     * 显示统计信息
     */
    private fun displayStatistics(diffResult: DiffResult) {
        val removedCount = diffResult.removedLines.size
        val addedCount = diffResult.addedLines.size
        
        binding.textDiffStats.text = buildString {
            append("修改统计: ")
            if (removedCount > 0) {
                append("-$removedCount 行")
            }
            if (addedCount > 0) {
                if (removedCount > 0) append(", ")
                append("+$addedCount 行")
            }
            if (removedCount == 0 && addedCount == 0) {
                append("无变化")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_script_diff, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy_original -> {
                copyToClipboard(originalCode, "原始代码已复制")
                true
            }
            R.id.action_copy_modified -> {
                copyToClipboard(modifiedCode, "修改后代码已复制")
                true
            }
            R.id.action_share -> {
                shareCodeDiff()
                true
            }
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 复制代码到剪贴板
     */
    private fun copyToClipboard(code: String, message: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Script Code", code)
        clipboard.setPrimaryClip(clip)
        
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 分享代码差异
     */
    private fun shareCodeDiff() {
        val shareText = buildString {
            appendLine("AutoJs6 脚本AI修改记录")
            appendLine()
            appendLine("修改原因: $reason")
            appendLine("修改次数: 第${attemptNumber}次")
            appendLine()
            appendLine("原始代码:")
            appendLine("```javascript")
            appendLine(originalCode)
            appendLine("```")
            appendLine()
            appendLine("修改后代码:")
            appendLine("```javascript")
            appendLine(modifiedCode)
            appendLine("```")
        }
        
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "AutoJs6 脚本修改记录")
        }
        
        startActivity(android.content.Intent.createChooser(shareIntent, "分享代码修改记录"))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /**
     * 差异计算结果
     */
    private data class DiffResult(
        val removedLines: Set<Int>,
        val addedLines: Set<Int>
    )
}