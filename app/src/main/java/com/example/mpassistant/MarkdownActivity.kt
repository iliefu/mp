package com.example.mpassistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
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
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")

        // file:// 页面里用 fetch() 读取本地资源经常被同源策略拦截(不同机型/系统版本表现不一致)。
        // 用 WebViewAssetLoader 把 assets 目录伪装成一个虚拟的 https:// 域名提供给 WebView,
        // fetch('templates/xxx.json') 这类相对路径请求就会被下面的 shouldInterceptRequest 拦截并
        // 直接从本地 assets 里读取返回,行为和真正的网络请求一致,不再受 file 协议限制。
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/markdown_preview.html")

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
