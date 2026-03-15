package com.shspanel.app.ui.preview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shspanel.app.R
import com.shspanel.app.databinding.ActivityPreviewBinding
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private var httpServer: LocalHttpServer? = null
    private var serverPort = 8080
    private var rootDir: File? = null

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        val targetFile = if (filePath.isNotEmpty()) File(filePath) else null

        setupToolbar(targetFile)
        setupWebView()
        loadPreview(targetFile)
    }

    private fun setupToolbar(file: File?) {
        binding.tvTitle.text = file?.name ?: "Preview"

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }

        binding.btnOpenBrowser.setOnClickListener {
            val url = binding.webView.url ?: return@setOnClickListener
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowUniversalAccessFromFileURLs = true
                allowFileAccessFromFileURLs = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    binding.progressBar.progress = newProgress
                    binding.progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    binding.tvCurrentUrl.text = url
                }
                override fun onPageFinished(view: WebView, url: String) {
                    binding.tvCurrentUrl.text = url
                }
            }
        }
    }

    private fun loadPreview(file: File?) {
        if (file == null) {
            binding.webView.loadData("<h1>No file selected</h1>", "text/html", "UTF-8")
            return
        }

        val targetDir = if (file.isDirectory) file else file.parentFile ?: file
        val targetHtml = if (file.isFile && file.extension.lowercase() in listOf("html", "htm")) {
            file
        } else {
            findIndexHtml(targetDir)
        }

        rootDir = targetDir

        try {
            httpServer = LocalHttpServer(targetDir, serverPort)
            httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

            val relativePath = if (targetHtml != null) {
                targetHtml.absolutePath.removePrefix(targetDir.absolutePath).trimStart('/')
            } else ""

            val url = "http://localhost:$serverPort/$relativePath"
            binding.webView.loadUrl(url)
            binding.tvCurrentUrl.text = url
        } catch (e: Exception) {
            // Fallback to file:// if local server fails
            val url = if (targetHtml != null) {
                "file://${targetHtml.absolutePath}"
            } else {
                "file://${file.absolutePath}"
            }
            binding.webView.loadUrl(url)
            binding.tvCurrentUrl.text = url
        }
    }

    private fun findIndexHtml(dir: File): File? {
        return dir.listFiles()?.firstOrNull {
            it.name.lowercase() in listOf("index.html", "index.htm")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        binding.webView.destroy()
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    inner class LocalHttpServer(private val rootDir: File, port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri.trimStart('/')
            val file = if (uri.isEmpty()) {
                findIndexHtml(rootDir) ?: rootDir
            } else {
                File(rootDir, uri)
            }

            return if (file.exists() && file.isFile) {
                val mimeType = getMimeType(file.extension)
                newChunkedResponse(Response.Status.OK, mimeType, FileInputStream(file))
            } else if (file.isDirectory) {
                val index = findIndexHtml(file)
                if (index != null) {
                    newChunkedResponse(Response.Status.OK, "text/html", FileInputStream(index))
                } else {
                    generateDirListing(file, uri)
                }
            } else {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found")
            }
        }

        private fun getMimeType(ext: String): String = when (ext.lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }

        private fun generateDirListing(dir: File, uri: String): Response {
            val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
            val html = buildString {
                append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>")
                append(dir.name)
                append("</title><style>body{font-family:monospace;background:#0D1B2A;color:#c8d3e0;padding:20px}")
                append("a{color:#00E5FF;text-decoration:none;display:block;padding:8px 0}")
                append("a:hover{color:#fff}h1{color:#00E5FF;margin-bottom:20px}</style></head><body>")
                append("<h1>📁 ${dir.name}</h1>")
                if (uri.isNotEmpty()) append("<a href='../'>⬆ Parent Directory</a>")
                files.forEach { f ->
                    val icon = if (f.isDirectory) "📁" else "📄"
                    val link = if (uri.isEmpty()) f.name else "$uri/${f.name}"
                    append("<a href='/$link'>$icon ${f.name}</a>")
                }
                append("</body></html>")
            }
            return newFixedLengthResponse(Response.Status.OK, "text/html", html)
        }
    }
}
