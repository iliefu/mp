package com.example.mpassistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MarkdownActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                loadMarkdownFromUri(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_markdown)

        webView = findViewById(R.id.markdownWebView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        // 渲染页面需要用 fetch() 读取 assets/templates/ 下的模板JSON文件,
        // 这几个开关只影响这一个WebView(它只加载打包进APP的本地资源,不加载任何远程页面),
        // 所以放开本地文件互访是安全的。
        webView.settings.allowFileAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowUniversalAccessFromFileURLs = true
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/markdown_preview.html")

        findViewById<Button>(R.id.btnOpenFile).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/markdown", "text/plain", "text/x-markdown"))
            }
            openFileLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnBackToWechat).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    // 支持从文件管理器"用其他应用打开"/系统分享 传入 .md 文件
    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) {
            loadMarkdownFromUri(uri)
        }
    }

    private fun loadMarkdownFromUri(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val escaped = JSONObject.quote(text)
            webView.post {
                webView.evaluateJavascript("setMarkdownAndRender($escaped);", null)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onCopyDone() {
            runOnUiThread {
                Toast.makeText(this@MarkdownActivity, "已复制,可以切到公众号编辑器长按粘贴了", Toast.LENGTH_LONG).show()
            }
        }

        @JavascriptInterface
        fun onCopyFail(msg: String) {
            runOnUiThread {
                Toast.makeText(this@MarkdownActivity, "复制失败: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
