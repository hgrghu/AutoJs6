package org.autojs.autojs.agent.github

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * GitHub集成管理器
 * 支持推送优化的脚本和配置到GitHub仓库
 */
class GitHubManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: GitHubManager? = null
        private const val PREF_NAME = "github_manager"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_DEFAULT_REPO = "default_repo"
        private const val KEY_DEFAULT_BRANCH = "default_branch"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_SYNC_MODELS = "sync_models"
        private const val KEY_SYNC_SCRIPTS = "sync_scripts"
        
        fun getInstance(context: Context): GitHubManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GitHubManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    // GitHub API配置
    private val githubApiBase = "https://api.github.com"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * 设置GitHub访问令牌
     */
    fun setAccessToken(token: String) {
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .apply()
    }
    
    /**
     * 获取GitHub访问令牌
     */
    fun getAccessToken(): String? {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }
    
    /**
     * 设置默认仓库
     */
    fun setDefaultRepo(owner: String, repo: String) {
        sharedPreferences.edit()
            .putString(KEY_DEFAULT_REPO, "$owner/$repo")
            .apply()
    }
    
    /**
     * 获取默认仓库
     */
    fun getDefaultRepo(): Pair<String, String>? {
        val repo = sharedPreferences.getString(KEY_DEFAULT_REPO, null)
        return repo?.split("/")?.let { parts ->
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
    }
    
    /**
     * 设置默认分支
     */
    fun setDefaultBranch(branch: String) {
        sharedPreferences.edit()
            .putString(KEY_DEFAULT_BRANCH, branch)
            .apply()
    }
    
    /**
     * 获取默认分支
     */
    fun getDefaultBranch(): String {
        return sharedPreferences.getString(KEY_DEFAULT_BRANCH, "main") ?: "main"
    }
    
    /**
     * 设置自动同步
     */
    fun setAutoSync(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_AUTO_SYNC, enabled)
            .apply()
    }
    
    /**
     * 是否启用自动同步
     */
    fun isAutoSyncEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_SYNC, false)
    }
    
    /**
     * 测试GitHub连接
     */
    suspend fun testConnection(): GitHubTestResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext GitHubTestResult(false, "未设置访问令牌")
            }
            
            val userInfo = getUserInfo(token)
            GitHubTestResult(
                success = true,
                message = "连接成功",
                userInfo = userInfo
            )
        } catch (e: Exception) {
            GitHubTestResult(false, "连接失败: ${e.message}")
        }
    }
    
    /**
     * 获取用户信息
     */
    private suspend fun getUserInfo(token: String): GitHubUser = withContext(Dispatchers.IO) {
        val url = URL("$githubApiBase/user")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "token $token")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { 
                    it.readText() 
                }
                val json = JSONObject(response)
                GitHubUser(
                    login = json.getString("login"),
                    name = json.optString("name"),
                    avatarUrl = json.optString("avatar_url"),
                    publicRepos = json.optInt("public_repos", 0),
                    privateRepos = json.optInt("total_private_repos", 0)
                )
            } else {
                throw Exception("获取用户信息失败: HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 获取仓库列表
     */
    suspend fun getRepositories(): List<GitHubRepo> = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: throw Exception("未设置访问令牌")
        
        val url = URL("$githubApiBase/user/repos?type=all&sort=updated&per_page=100")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "token $token")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { 
                    it.readText() 
                }
                val jsonArray = JSONArray(response)
                (0 until jsonArray.length()).map { i ->
                    val repo = jsonArray.getJSONObject(i)
                    GitHubRepo(
                        name = repo.getString("name"),
                        fullName = repo.getString("full_name"),
                        description = repo.optString("description"),
                        isPrivate = repo.getBoolean("private"),
                        defaultBranch = repo.getString("default_branch"),
                        updatedAt = repo.getString("updated_at")
                    )
                }
            } else {
                throw Exception("获取仓库列表失败: HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 创建新仓库
     */
    suspend fun createRepository(
        name: String, 
        description: String = "",
        isPrivate: Boolean = false,
        autoInit: Boolean = true
    ): GitHubRepo = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: throw Exception("未设置访问令牌")
        
        val url = URL("$githubApiBase/user/repos")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "token $token")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("private", isPrivate)
                put("auto_init", autoInit)
            }
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { 
                    it.readText() 
                }
                val json = JSONObject(response)
                GitHubRepo(
                    name = json.getString("name"),
                    fullName = json.getString("full_name"),
                    description = json.optString("description"),
                    isPrivate = json.getBoolean("private"),
                    defaultBranch = json.getString("default_branch"),
                    updatedAt = json.getString("updated_at")
                )
            } else {
                val errorResponse = connection.errorStream?.let { 
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                }
                throw Exception("创建仓库失败: HTTP $responseCode - $errorResponse")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 推送脚本文件到GitHub
     */
    suspend fun pushScript(
        scriptName: String,
        scriptContent: String,
        commitMessage: String = "Update script via AutoJs6 Agent",
        owner: String? = null,
        repo: String? = null,
        branch: String? = null
    ): PushResult = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: throw Exception("未设置访问令牌")
        val (repoOwner, repoName) = getRepoInfo(owner, repo)
        val targetBranch = branch ?: getDefaultBranch()
        
        try {
            val filePath = "scripts/$scriptName"
            val encodedContent = android.util.Base64.encodeToString(
                scriptContent.toByteArray(), 
                android.util.Base64.NO_WRAP
            )
            
            // 检查文件是否存在并获取SHA
            val existingFile = getFileInfo(token, repoOwner, repoName, filePath, targetBranch)
            
            val success = createOrUpdateFile(
                token = token,
                owner = repoOwner,
                repo = repoName,
                path = filePath,
                content = encodedContent,
                message = commitMessage,
                branch = targetBranch,
                sha = existingFile?.sha
            )
            
            PushResult(
                success = success,
                message = if (success) "脚本推送成功" else "脚本推送失败",
                commitUrl = if (success) "https://github.com/$repoOwner/$repoName/blob/$targetBranch/$filePath" else null
            )
        } catch (e: Exception) {
            PushResult(false, "推送失败: ${e.message}")
        }
    }
    
    /**
     * 推送模型配置到GitHub
     */
    suspend fun pushModelConfig(
        configContent: String,
        commitMessage: String = "Update model config via AutoJs6 Agent",
        owner: String? = null,
        repo: String? = null,
        branch: String? = null
    ): PushResult = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: throw Exception("未设置访问令牌")
        val (repoOwner, repoName) = getRepoInfo(owner, repo)
        val targetBranch = branch ?: getDefaultBranch()
        
        try {
            val filePath = "config/models.json"
            val encodedContent = android.util.Base64.encodeToString(
                configContent.toByteArray(), 
                android.util.Base64.NO_WRAP
            )
            
            val existingFile = getFileInfo(token, repoOwner, repoName, filePath, targetBranch)
            
            val success = createOrUpdateFile(
                token = token,
                owner = repoOwner,
                repo = repoName,
                path = filePath,
                content = encodedContent,
                message = commitMessage,
                branch = targetBranch,
                sha = existingFile?.sha
            )
            
            PushResult(
                success = success,
                message = if (success) "模型配置推送成功" else "模型配置推送失败",
                commitUrl = if (success) "https://github.com/$repoOwner/$repoName/blob/$targetBranch/$filePath" else null
            )
        } catch (e: Exception) {
            PushResult(false, "推送失败: ${e.message}")
        }
    }
    
    /**
     * 批量推送优化结果
     */
    suspend fun pushOptimizationResults(
        results: List<OptimizationPushData>,
        owner: String? = null,
        repo: String? = null,
        branch: String? = null
    ): BatchPushResult = withContext(Dispatchers.IO) {
        val successfulPushes = mutableListOf<String>()
        val failedPushes = mutableListOf<String>()
        
        results.forEach { data ->
            try {
                val result = pushScript(
                    scriptName = data.fileName,
                    scriptContent = data.content,
                    commitMessage = data.commitMessage,
                    owner = owner,
                    repo = repo,
                    branch = branch
                )
                
                if (result.success) {
                    successfulPushes.add(data.fileName)
                } else {
                    failedPushes.add(data.fileName)
                }
            } catch (e: Exception) {
                failedPushes.add(data.fileName)
            }
        }
        
        BatchPushResult(
            totalCount = results.size,
            successCount = successfulPushes.size,
            failedCount = failedPushes.size,
            successfulFiles = successfulPushes,
            failedFiles = failedPushes
        )
    }
    
    /**
     * 从GitHub拉取脚本
     */
    suspend fun pullScript(
        filePath: String,
        owner: String? = null,
        repo: String? = null,
        branch: String? = null
    ): PullResult = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: throw Exception("未设置访问令牌")
        val (repoOwner, repoName) = getRepoInfo(owner, repo)
        val targetBranch = branch ?: getDefaultBranch()
        
        try {
            val fileInfo = getFileInfo(token, repoOwner, repoName, filePath, targetBranch)
            if (fileInfo != null) {
                val content = String(android.util.Base64.decode(fileInfo.content, android.util.Base64.DEFAULT))
                PullResult(
                    success = true,
                    content = content,
                    fileName = fileInfo.name,
                    lastModified = fileInfo.lastModified
                )
            } else {
                PullResult(false, message = "文件不存在")
            }
        } catch (e: Exception) {
            PullResult(false, message = "拉取失败: ${e.message}")
        }
    }
    
    /**
     * 获取文件信息
     */
    private suspend fun getFileInfo(
        token: String,
        owner: String,
        repo: String,
        path: String,
        branch: String
    ): GitHubFile? = withContext(Dispatchers.IO) {
        val encodedPath = URLEncoder.encode(path, "UTF-8")
        val url = URL("$githubApiBase/repos/$owner/$repo/contents/$encodedPath?ref=$branch")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "token $token")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            val responseCode = connection.responseCode
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { 
                        it.readText() 
                    }
                    val json = JSONObject(response)
                    GitHubFile(
                        name = json.getString("name"),
                        path = json.getString("path"),
                        sha = json.getString("sha"),
                        content = json.getString("content"),
                        lastModified = json.optString("last_modified", "")
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw Exception("获取文件信息失败: HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 创建或更新文件
     */
    private suspend fun createOrUpdateFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String,
        sha: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val encodedPath = URLEncoder.encode(path, "UTF-8")
        val url = URL("$githubApiBase/repos/$owner/$repo/contents/$encodedPath")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "token $token")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("message", message)
                put("content", content)
                put("branch", branch)
                sha?.let { put("sha", it) }
            }
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 获取仓库信息
     */
    private fun getRepoInfo(owner: String?, repo: String?): Pair<String, String> {
        return if (owner != null && repo != null) {
            Pair(owner, repo)
        } else {
            getDefaultRepo() ?: throw Exception("未设置默认仓库且未提供仓库信息")
        }
    }
    
    /**
     * 自动同步优化结果
     */
    suspend fun autoSyncIfEnabled(
        scriptName: String,
        optimizedContent: String,
        originalScore: Int,
        newScore: Int
    ) {
        if (isAutoSyncEnabled()) {
            try {
                val commitMessage = "Auto-optimize script: $scriptName (${originalScore}→${newScore} points) [${dateFormat.format(Date())}]"
                pushScript(
                    scriptName = scriptName,
                    scriptContent = optimizedContent,
                    commitMessage = commitMessage
                )
            } catch (e: Exception) {
                // 静默失败，不影响主要功能
                e.printStackTrace()
            }
        }
    }
}

/**
 * GitHub测试结果
 */
data class GitHubTestResult(
    val success: Boolean,
    val message: String,
    val userInfo: GitHubUser? = null
)

/**
 * GitHub用户信息
 */
data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val publicRepos: Int,
    val privateRepos: Int
)

/**
 * GitHub仓库信息
 */
data class GitHubRepo(
    val name: String,
    val fullName: String,
    val description: String?,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val updatedAt: String
)

/**
 * GitHub文件信息
 */
data class GitHubFile(
    val name: String,
    val path: String,
    val sha: String,
    val content: String,
    val lastModified: String
)

/**
 * 推送结果
 */
data class PushResult(
    val success: Boolean,
    val message: String,
    val commitUrl: String? = null
)

/**
 * 拉取结果
 */
data class PullResult(
    val success: Boolean,
    val content: String? = null,
    val fileName: String? = null,
    val lastModified: String? = null,
    val message: String? = null
)

/**
 * 批量推送结果
 */
data class BatchPushResult(
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val successfulFiles: List<String>,
    val failedFiles: List<String>
)

/**
 * 优化推送数据
 */
data class OptimizationPushData(
    val fileName: String,
    val content: String,
    val commitMessage: String
)