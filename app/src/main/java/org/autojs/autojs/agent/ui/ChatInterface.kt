package org.autojs.autojs.agent.ui

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import org.autojs.autojs.agent.core.AgentService
import org.autojs.autojs.agent.model.ChatMessage

/**
 * AI聊天界面
 * 提供与AI助手的对话功能
 */
class ChatInterface(private val context: Context) {
    
    private var dialog: MaterialDialog? = null
    private var messageAdapter: ChatMessageAdapter? = null
    private var agentService: AgentService? = null
    private val chatMessages = mutableListOf<ChatMessage>()
    private val chatScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * 显示聊天界面
     */
    fun show() {
        if (dialog?.isShowing == true) {
            return
        }
        
        initializeAgentService()
        createChatDialog()
    }
    
    /**
     * 初始化Agent服务
     */
    private fun initializeAgentService() {
        agentService = AgentService.getInstance(context)
        
        chatScope.launch {
            try {
                agentService?.initialize()
                addSystemMessage("AI助手已就绪，您可以开始对话")
            } catch (e: Exception) {
                addSystemMessage("AI助手初始化失败: ${e.message}")
            }
        }
    }
    
    /**
     * 创建聊天对话框
     */
    private fun createChatDialog() {
        val dialogView = createChatView()
        
        dialog = MaterialDialog.Builder(context)
            .title("AI 助手")
            .customView(dialogView, false)
            .positiveText("关闭")
            .onPositive { dialog, _ -> 
                cleanup()
                dialog.dismiss()
            }
            .cancelable(true)
            .dismissListener { 
                cleanup()
            }
            .build()
            
        dialog?.show()
    }
    
    /**
     * 创建聊天视图
     */
    private fun createChatView(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        
        // 消息列表
        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutManager = LinearLayoutManager(context)
        }
        
        messageAdapter = ChatMessageAdapter(chatMessages)
        recyclerView.adapter = messageAdapter
        
        layout.addView(recyclerView)
        
        // 输入区域
        val inputContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val inputText = TextInputEditText(context).apply {
            hint = "输入您的问题..."
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 0, 8, 0)
            }
            minLines = 1
            maxLines = 3
        }
        
        val sendButton = Button(context).apply {
            text = "发送"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            setOnClickListener {
                val message = inputText.text?.toString()?.trim()
                if (!message.isNullOrBlank()) {
                    sendMessage(message)
                    inputText.text?.clear()
                }
            }
        }
        
        inputContainer.addView(inputText)
        inputContainer.addView(sendButton)
        
        layout.addView(inputContainer)
        
        // 添加欢迎消息
        addSystemMessage("您好！我是AutoJs6的AI助手，可以帮助您编写和优化脚本。")
        
        return layout
    }
    
    /**
     * 发送消息
     */
    private fun sendMessage(message: String) {
        // 添加用户消息
        addUserMessage(message)
        
        // 发送给AI并获取回复
        chatScope.launch {
            try {
                val response = agentService?.chatWithAgent(message) 
                    ?: throw Exception("AI服务不可用")
                
                addAssistantMessage(response.message.content)
                
                // 如果有建议的脚本，显示相关信息
                response.generatedScript?.let { script ->
                    addSystemMessage("我为您生成了一个脚本片段，您可以在编辑器中查看。")
                }
                
            } catch (e: Exception) {
                addSystemMessage("抱歉，发生了错误: ${e.message}")
            }
        }
    }
    
    /**
     * 添加用户消息
     */
    private fun addUserMessage(content: String) {
        val message = ChatMessage(
            content = content,
            role = ChatMessage.MessageRole.USER
        )
        chatMessages.add(message)
        messageAdapter?.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    /**
     * 添加AI助手消息
     */
    private fun addAssistantMessage(content: String) {
        val message = ChatMessage(
            content = content,
            role = ChatMessage.MessageRole.ASSISTANT
        )
        chatMessages.add(message)
        messageAdapter?.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    /**
     * 添加系统消息
     */
    private fun addSystemMessage(content: String) {
        val message = ChatMessage(
            content = content,
            role = ChatMessage.MessageRole.SYSTEM
        )
        chatMessages.add(message)
        messageAdapter?.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    /**
     * 滚动到底部
     */
    private fun scrollToBottom() {
        val recyclerView = dialog?.customView?.findViewById<RecyclerView>(0)
        recyclerView?.scrollToPosition(chatMessages.size - 1)
    }
    
    /**
     * 清理资源
     */
    private fun cleanup() {
        chatScope.cancel()
        agentService?.cleanup()
    }
}

/**
 * 聊天消息适配器
 */
private class ChatMessageAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MessageViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 4, 8, 4)
            }
            setPadding(12, 8, 12, 8)
            textSize = 14f
        }
        return MessageViewHolder(textView)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val textView = holder.itemView as TextView
        
        textView.text = message.content
        
        when (message.role) {
            ChatMessage.MessageRole.USER -> {
                textView.setBackgroundColor(0xFF2196F3.toInt())
                textView.setTextColor(0xFFFFFFFF.toInt())
                textView.gravity = android.view.Gravity.END
            }
            ChatMessage.MessageRole.ASSISTANT -> {
                textView.setBackgroundColor(0xFFE0E0E0.toInt())
                textView.setTextColor(0xFF000000.toInt())
                textView.gravity = android.view.Gravity.START
            }
            ChatMessage.MessageRole.SYSTEM -> {
                textView.setBackgroundColor(0xFFFFF3E0.toInt())
                textView.setTextColor(0xFF757575.toInt())
                textView.gravity = android.view.Gravity.CENTER
                textView.textSize = 12f
            }
        }
    }
    
    override fun getItemCount(): Int = messages.size
    
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}