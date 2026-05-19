package com.ethan.tnd

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var serverProcess: Process? = null
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fully immersive: hide status + nav bars; swipe from edge to reveal transiently.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
        setContentView(webView)

        thread(name = "tnd-launcher", isDaemon = true) { startServerAndLoad() }
    }

    private fun startServerAndLoad() {
        try {
            val binary = File(applicationInfo.nativeLibraryDir, "libtnd.so")
            if (!binary.exists()) {
                Log.e(TAG, "binary missing: ${binary.absolutePath}")
                runOnUiThread { webView.loadData(errorPage("找不到 Rust 二进制 libtnd.so"), "text/html", "utf-8") }
                return
            }
            binary.setExecutable(true, false)

            val dataDir = File(filesDir, "tnd-data").apply { mkdirs() }

            val pb = ProcessBuilder(
                binary.absolutePath,
                "--server",
                "--data-dir", dataDir.absolutePath
            ).apply {
                redirectErrorStream(true)
                environment()["HOME"] = dataDir.absolutePath
                environment()["TMPDIR"] = cacheDir.absolutePath
            }
            serverProcess = pb.start()

            thread(name = "tnd-stdout", isDaemon = true) {
                serverProcess?.inputStream?.bufferedReader()?.useLines { lines ->
                    lines.forEach { Log.i(TAG_SRV, it) }
                }
            }

            val url = "http://127.0.0.1:18423"
            if (waitForServer(url, timeoutMs = 30_000)) {
                runOnUiThread { webView.loadUrl(url) }
            } else {
                runOnUiThread { webView.loadData(errorPage("服务器未在 30 秒内启动"), "text/html", "utf-8") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "launch failed", e)
            runOnUiThread { webView.loadData(errorPage("启动失败: ${e.message}"), "text/html", "utf-8") }
        }
    }

    private fun waitForServer(url: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                val c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 500
                    readTimeout = 500
                    requestMethod = "GET"
                }
                val code = c.responseCode
                c.disconnect()
                if (code in 200..499) return true
            } catch (_: Exception) {
            }
            Thread.sleep(300)
        }
        return false
    }

    private fun errorPage(msg: String) =
        "<html><body style='font-family:sans-serif;padding:24px;'><h2>启动失败</h2><pre>$msg</pre></body></html>"

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serverProcess?.destroy()
        serverProcess = null
    }

    companion object {
        private const val TAG = "TND"
        private const val TAG_SRV = "TND-srv"
    }
}
