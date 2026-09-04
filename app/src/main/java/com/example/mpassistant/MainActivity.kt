package com.example.mpassistant

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 起始地址:公众号后台首页
    private val START_URL = "https://mp.weixin.qq.com/"

    // 伪装成电脑Chrome的UA,让微信后台返回完整桌面版编辑器(而不是阉割过的移动版)
    private val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            var results: Array<Uri>? = null
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val dataString = data?.dataString
                if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                } else {
                    // 处理多选图片的情况
                    val clipData = data?.clipData
                    if (clipData != null) {
                        results = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    }
                }
            }
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        findViewById<android.widget.Button>(R.id.btnOpenMarkdown).setOnClickListener {
            startActivity(Intent(this, MarkdownActivity::class.java))
        }

        setupWebView()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(START_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = DESKTOP_UA
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // 登录状态(cookie)持久化,避免每次都要重新扫码登录
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                // 扫码登录等场景可能跳转到微信APP的scheme,交给系统处理
                if (url.startsWith("weixin://")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        // 未安装微信客户端,忽略
                    }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                swipeRefresh.isRefreshing = false
                cookieManager.flush()
                injectViewportFix(view)
            }
        }

        // 处理 <input type="file"> 上传图片/封面
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams.createIntent()
                intent.type = "image/*"
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

                return try {
                    fileChooserLauncher.launch(Intent.createChooser(intent, "选择图片"))
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }

        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
    }

    // 桌面版页面本身没有为触屏做适配,而且很多现代网页会自己拦截touch事件用于内部滚动,
    // 导致WebView原生的双指缩放/拖动手势被吞掉,滑不动也缩不了。
    // 这里在页面加载完之后动态插入一个viewport声明,把整页按屏幕宽度算出的比例缩小显示,
    // 同时强制声明 touch-action,明确告诉浏览器"缩放和滑动这两个手势必须放行",
    // 这样WebView原生的缩放/滚动手势才能正常生效。
    private fun injectViewportFix(view: WebView) {
        val js = """
            (function(){
                try {
                    var meta = document.querySelector('meta[name=viewport]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        document.head.appendChild(meta);
                    }
                    function fitScale(){
                        var w = Math.max(document.documentElement.scrollWidth, document.body ? document.body.scrollWidth : 0);
                        if (!w) return;
                        var scale = window.innerWidth / w;
                        if (scale > 1) scale = 1;
                        meta.setAttribute('content', 'width=' + w + ', initial-scale=' + scale + ', minimum-scale=0.1, maximum-scale=5, user-scalable=yes');
                    }
                    fitScale();
                    window.addEventListener('resize', fitScale);

                    var fitTimer = null;
                    function debouncedFit(){
                        if (fitTimer) clearTimeout(fitTimer);
                        fitTimer = setTimeout(fitScale, 300);
                    }
                    if (window.MutationObserver && document.body) {
                        var mo = new MutationObserver(debouncedFit);
                        mo.observe(document.body, { childList: true, subtree: true, attributes: true });
                    }
                    // SPA异步渲染内容,首次计算时页面可能还没撑开,过一会再校正一次
                    setTimeout(fitScale, 800);
                    setTimeout(fitScale, 2000);

                    var style = document.getElementById('__mpassistant_touch_fix__');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = '__mpassistant_touch_fix__';
                        document.head.appendChild(style);
                    }
                    style.innerHTML = 'html, body { touch-action: pan-x pan-y pinch-zoom !important; }';
                } catch (e) {
                    console.error('viewport fix failed', e);
                }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
