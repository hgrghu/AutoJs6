# AutoJs6 自定义模型管理系统完整实现方案

## 概述

本系统为AutoJs6添加了完整的自定义AI模型管理功能，支持用户自由添加、修改、删除各种AI模型，通过URL、API、模型名称调用市面上所有的模型，并集成GitHub推送功能，实现优化更新的自动同步。

## 核心功能

### 1. 自定义模型管理

#### 支持的模型类型
- **OpenAI官方API**: GPT-3.5、GPT-4等
- **OpenAI兼容API**: Ollama、vLLM、LM Studio等本地服务
- **Anthropic Claude**: Claude-3 Opus、Sonnet、Haiku
- **Google Gemini**: Gemini Pro、Gemini Pro Vision
- **自定义API**: 支持任意格式的API接口

#### 模型管理功能
- ✅ **添加模型**: 自定义模型名称、提供商、API URL、模型参数
- ✅ **编辑模型**: 修改现有自定义模型的所有配置
- ✅ **删除模型**: 删除不需要的自定义模型
- ✅ **复制模型**: 基于现有模型快速创建副本
- ✅ **测试连接**: 验证模型配置是否正确
- ✅ **导入/导出**: JSON格式的配置导入导出

### 2. GitHub集成功能

#### 自动同步
- ✅ **自动推送**: 脚本优化后自动推送到GitHub仓库
- ✅ **配置同步**: 模型配置自动同步到GitHub
- ✅ **版本控制**: 支持commit消息自定义和分支管理

#### 手动操作
- ✅ **手动推送**: 支持手动推送脚本和配置
- ✅ **拉取脚本**: 从GitHub拉取现有脚本
- ✅ **批量操作**: 支持批量推送多个优化结果

## 技术架构

### 核心组件

#### 1. ModelManager (模型管理器)
```kotlin
// 位置: app/src/main/java/org/autojs/autojs/agent/api/ModelManager.kt
- 单例模式管理所有AI模型
- 支持预定义模型和自定义模型
- 提供模型CRUD操作
- 支持模型连接测试
- 配置导入导出功能
```

#### 2. UniversalAPIClient (通用API客户端)
```kotlin
// 位置: app/src/main/java/org/autojs/autojs/agent/api/UniversalAPIClient.kt
- OpenAICompatibleClient: 支持OpenAI及兼容API
- AnthropicClient: Anthropic Claude API客户端
- GoogleGeminiClient: Google Gemini API客户端
- CustomAPIClient: 自定义API格式支持
```

#### 3. GitHubManager (GitHub集成管理器)
```kotlin
// 位置: app/src/main/java/org/autojs/autojs/agent/github/GitHubManager.kt
- GitHub API集成
- 自动同步功能
- 仓库管理
- 文件推送和拉取
```

#### 4. ModelManagementActivity (模型管理界面)
```kotlin
// 位置: app/src/main/java/org/autojs/autojs/agent/ui/ModelManagementActivity.kt
- 完整的模型管理界面
- Material Design设计
- GitHub配置界面
- 模型测试功能
```

### 数据模型

#### AIModel (AI模型数据类)
```kotlin
data class AIModel(
    val id: String,                    // 模型唯一标识
    val name: String,                  // 显示名称
    val provider: String,              // 提供商
    val baseUrl: String,               // API基础URL
    val apiType: APIType,              // API类型
    val modelName: String,             // 模型名称
    val maxTokens: Int = 2048,         // 最大令牌数
    val temperature: Float = 0.3f,     // 温度参数
    val supportVision: Boolean = false, // 是否支持视觉
    val description: String = "",       // 描述
    val customConfig: Map<String, Any>? = null, // 自定义配置
    val isCustom: Boolean = true,      // 是否为自定义模型
    val createdTime: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0
)
```

#### APIType (API类型枚举)
```kotlin
enum class APIType {
    OPENAI,              // OpenAI 官方API
    OPENAI_COMPATIBLE,   // OpenAI 兼容API
    ANTHROPIC,           // Anthropic Claude
    GOOGLE,              // Google Gemini
    CUSTOM               // 自定义API格式
}
```

## 使用指南

### 1. 添加自定义模型

#### 步骤
1. 打开AutoJs6编辑器
2. 点击菜单 → AI Agent → 模型管理
3. 点击右下角"+"按钮
4. 填写模型配置：
   - **模型ID**: 唯一标识符（自动生成）
   - **模型名称**: 显示名称
   - **提供商**: 如OpenAI、Anthropic等
   - **API URL**: 接口地址
   - **模型名称**: API中的模型名
   - **最大令牌数**: 通常2048-8192
   - **温度**: 0.1-1.0，控制创造性
   - **API类型**: 选择对应的API格式
   - **支持视觉**: 是否支持图像分析
   - **描述**: 模型说明

#### 示例配置

**本地Ollama模型**:
```json
{
  "name": "Llama2 Local",
  "provider": "Ollama",
  "baseUrl": "http://localhost:11434/v1",
  "apiType": "OPENAI_COMPATIBLE",
  "modelName": "llama2:latest",
  "maxTokens": 4096,
  "temperature": 0.3
}
```

**Claude API**:
```json
{
  "name": "Claude 3 Sonnet",
  "provider": "Anthropic",
  "baseUrl": "https://api.anthropic.com/v1",
  "apiType": "ANTHROPIC",
  "modelName": "claude-3-sonnet-20240229",
  "maxTokens": 4096,
  "supportVision": true
}
```

### 2. GitHub集成配置

#### 设置步骤
1. 在模型管理界面点击菜单 → GitHub配置
2. 填写配置信息：
   - **GitHub Token**: 个人访问令牌
   - **仓库所有者**: GitHub用户名
   - **仓库名称**: 目标仓库名
   - **分支**: 默认分支（通常为main）
   - **自动同步**: 是否启用自动推送

#### GitHub Token创建
1. 访问 GitHub Settings → Developer settings → Personal access tokens
2. 点击 "Generate new token (classic)"
3. 选择权限: `repo`, `workflow`
4. 复制生成的token

### 3. 模型切换和使用

#### 在编辑器中使用
1. 打开模型管理界面
2. 选择要使用的模型
3. 点击"测试"按钮验证连接
4. 在AI Agent菜单中使用各项功能

#### 支持的AI功能
- **脚本优化**: AI分析并改进脚本质量
- **脚本生成**: 根据自然语言描述生成脚本
- **屏幕分析**: 分析屏幕截图识别UI元素
- **坐标优化**: 替换硬编码坐标为稳定选择器
- **AI聊天**: 与AI助手实时对话

## 配置文件

### 模型配置存储
```json
// SharedPreferences中存储的模型配置格式
{
  "version": "1.0",
  "exportTime": 1703123456789,
  "customModels": [
    {
      "id": "custom-model-1",
      "name": "My Custom Model",
      "provider": "Local",
      "baseUrl": "http://localhost:8080/v1",
      "apiType": "OPENAI_COMPATIBLE",
      "modelName": "custom-model",
      "maxTokens": 2048,
      "temperature": 0.3,
      "supportVision": false,
      "description": "本地部署的自定义模型",
      "isCustom": true,
      "createdTime": 1703123456789
    }
  ]
}
```

### GitHub配置
```json
// GitHub相关配置
{
  "access_token": "ghp_xxxxxxxxxxxxxxxx",
  "default_repo": "username/autojs-scripts",
  "default_branch": "main",
  "auto_sync": true
}
```

## 扩展开发

### 添加新的API类型

#### 1. 扩展APIType枚举
```kotlin
enum class APIType {
    OPENAI,
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GOOGLE,
    HUGGINGFACE,    // 新增
    CUSTOM
}
```

#### 2. 实现对应的客户端
```kotlin
class HuggingFaceClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val modelName: String
) : AgentAPI {
    // 实现所有AgentAPI接口方法
}
```

#### 3. 在ModelManager中添加支持
```kotlin
fun createClientForModel(model: AIModel, apiKey: String): AgentAPI {
    return when (model.apiType) {
        // ... 现有类型
        APIType.HUGGINGFACE -> HuggingFaceClient(
            apiKey = apiKey,
            baseUrl = model.baseUrl,
            modelName = model.modelName
        )
        // ...
    }
}
```

### 添加新的Git服务支持

仿照GitHubManager的模式，可以添加对GitLab、Gitee等服务的支持：

```kotlin
class GitLabManager private constructor(private val context: Context) {
    // 实现GitLab API集成
}
```

## 部署说明

### 1. 权限配置
确保在AndroidManifest.xml中添加必要权限：
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 2. 活动注册
```xml
<activity
    android:name="org.autojs.autojs.agent.ui.ModelManagementActivity"
    android:theme="@style/AppTheme"
    android:label="AI模型管理" />
```

### 3. 依赖项
确保项目中包含必要的依赖：
```gradle
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4'
implementation 'com.google.android.material:material:1.9.0'
```

## 安全考虑

### 1. API密钥安全
- API密钥使用加密存储
- 不在日志中输出敏感信息
- 支持密钥轮换

### 2. 网络安全
- 强制HTTPS连接
- 证书验证
- 请求超时设置

### 3. 权限控制
- 最小权限原则
- 用户确认敏感操作
- 安全的错误处理

## 性能优化

### 1. 缓存策略
- API响应缓存
- 模型配置缓存
- 智能缓存失效

### 2. 异步处理
- 所有网络请求异步执行
- 协程池管理
- 请求队列优化

### 3. 内存管理
- 及时释放资源
- 弱引用避免内存泄漏
- 大对象复用

## 故障排除

### 常见问题

#### 1. 模型连接失败
- 检查网络连接
- 验证API密钥
- 确认URL格式正确

#### 2. GitHub推送失败
- 验证GitHub Token权限
- 检查仓库访问权限
- 确认分支存在

#### 3. 脚本优化失败
- 检查脚本语法
- 验证AI服务状态
- 查看错误日志

### 调试方法
1. 启用详细日志
2. 使用网络抓包工具
3. 检查SharedPreferences配置
4. 验证JSON格式

## 版本更新记录

### v1.0.0 (2024-01)
- ✅ 完整的模型管理系统
- ✅ GitHub集成功能
- ✅ 多种API类型支持
- ✅ 用户界面完善

### 未来规划
- 🔄 模型性能监控
- 🔄 API使用统计
- 🔄 模型推荐系统
- 🔄 更多Git服务支持
- 🔄 模型市场功能

## 总结

该自定义模型管理系统为AutoJs6提供了强大的AI集成能力，用户可以：

1. **自由选择模型**: 不局限于特定厂商，支持所有主流AI服务
2. **灵活配置**: 支持详细的模型参数配置和自定义选项
3. **便捷管理**: 直观的界面和完整的CRUD功能
4. **自动同步**: GitHub集成实现无缝的版本控制
5. **扩展性强**: 模块化架构便于添加新功能

通过这套系统，AutoJs6真正实现了"人人都能用AI写脚本"的愿景，降低了自动化脚本开发的门槛，提高了开发效率。