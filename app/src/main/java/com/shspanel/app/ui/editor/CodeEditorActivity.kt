package com.shspanel.app.ui.editor

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shspanel.app.R
import com.shspanel.app.databinding.ActivityCodeEditorBinding
import com.shspanel.app.ui.preview.PreviewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

class CodeEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodeEditorBinding
    private var filePath: String = ""
    private var currentFile: File? = null
    private var isDirty = false

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        currentFile = if (filePath.isNotEmpty()) File(filePath) else null

        setupToolbar()
        setupWebView()
        loadFileInEditor()
    }

    private fun setupToolbar() {
        binding.tvFileName.text = currentFile?.name ?: "Untitled"

        binding.btnBack.setOnClickListener {
            if (isDirty) showUnsavedChangesDialog() else finish()
        }

        binding.btnSave.setOnClickListener { saveFile() }

        binding.btnPreview.setOnClickListener {
            val file = currentFile ?: return@setOnClickListener
            val previewTarget = if (file.extension.lowercase() in setOf("html", "htm")) {
                file
            } else {
                file.parentFile ?: file
            }
            startActivity(Intent(this, PreviewActivity::class.java).apply {
                putExtra(PreviewActivity.EXTRA_FILE_PATH, previewTarget.absolutePath)
            })
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.btnUndo.setOnClickListener {
            binding.webEditor.evaluateJavascript("editor.undo();", null)
        }

        binding.btnRedo.setOnClickListener {
            binding.webEditor.evaluateJavascript("editor.redo();", null)
        }

        binding.btnSearch.setOnClickListener {
            binding.webEditor.evaluateJavascript("editor.execCommand('find');", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webEditor.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                builtInZoomControls = false
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    loadFileContent()
                }
            }
            addJavascriptInterface(EditorBridge(), "Android")
        }
    }

    private fun loadFileInEditor() {
        val html = buildEditorHtml()
        binding.webEditor.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun loadFileContent() {
        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                currentFile?.readText() ?: ""
            }
            val escaped = content
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("\$", "\\\$")
            val ext = currentFile?.extension?.lowercase() ?: "txt"
            val mode = getAceMode(ext)
            binding.webEditor.evaluateJavascript("""
                editor.session.setMode('ace/mode/$mode');
                editor.setValue(`$escaped`, -1);
                editor.clearSelection();
                isDirty = false;
            """.trimIndent(), null)
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun saveFile() {
        binding.webEditor.evaluateJavascript("editor.getValue()") { value ->
            val content = value
                ?.removeSurrounding("\"")
                ?.replace("\\n", "\n")
                ?.replace("\\t", "\t")
                ?.replace("\\r", "\r")
                ?.replace("\\\"", "\"")
                ?.replace("\\'", "'")
                ?.replace("\\\\", "\\")
                ?: return@evaluateJavascript

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    currentFile?.writeText(content)
                }
                isDirty = false
                binding.webEditor.evaluateJavascript("isDirty = false; updateTitle();", null)
                Toast.makeText(this@CodeEditorActivity, "✅ Saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUnsavedChangesDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.GlassDialog)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Save before leaving?")
            .setPositiveButton("Save & Exit") { _, _ -> saveAndExit() }
            .setNegativeButton("Discard") { _, _ -> finish() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun saveAndExit() {
        binding.webEditor.evaluateJavascript("editor.getValue()") { value ->
            val content = value?.removeSurrounding("\"")
                ?.replace("\\n", "\n")?.replace("\\t", "\t")
                ?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: ""
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { currentFile?.writeText(content) }
                finish()
            }
        }
    }

    private fun getAceMode(ext: String): String = when (ext) {
        "html", "htm" -> "html"
        "css" -> "css"
        "js" -> "javascript"
        "ts" -> "typescript"
        "php" -> "php"
        "json" -> "json"
        "xml" -> "xml"
        "py" -> "python"
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "c", "cpp", "cc", "h", "hpp" -> "c_cpp"
        "sh", "bash" -> "sh"
        "sql" -> "sql"
        "yaml", "yml" -> "yaml"
        "md" -> "markdown"
        "rb" -> "ruby"
        "go" -> "golang"
        "rs" -> "rust"
        "dart" -> "dart"
        "swift" -> "swift"
        "vue" -> "html"
        "jsx", "tsx" -> "jsx"
        else -> "text"
    }

    private fun buildEditorHtml(): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body { width: 100%; height: 100%; background: #0D1B2A; overflow: hidden; }
  #editor {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    font-size: 14px;
    font-family: 'JetBrains Mono', 'Fira Code', 'Courier New', monospace;
  }
  .ace_editor { background: #0D1B2A !important; }
  .ace_gutter { background: #0a1520 !important; color: #4a6080 !important; border-right: 1px solid rgba(0,229,255,0.15) !important; }
  .ace_gutter-active-line { background: #1a2d40 !important; }
  .ace_active-line { background: #1a2d40 !important; }
  .ace_cursor { color: #00E5FF !important; border-left: 2px solid #00E5FF !important; }
  .ace_selection { background: rgba(0,229,255,0.2) !important; }
  .ace_scroller { background: #0D1B2A !important; }
  .ace_line { color: #c8d3e0 !important; }
  .ace_keyword { color: #00E5FF !important; }
  .ace_string { color: #98c379 !important; }
  .ace_comment { color: #5c6370 !important; font-style: italic; }
  .ace_numeric { color: #d19a66 !important; }
  .ace_identifier { color: #e06c75 !important; }
  .ace_tag { color: #e06c75 !important; }
  .ace_attribute { color: #d19a66 !important; }
  .ace_function { color: #61afef !important; }
  .ace_variable { color: #e5c07b !important; }
  .ace_scrollbar { width: 6px !important; }
  .ace_scrollbar-v .ace_scrollbar-inner { background: rgba(0,229,255,0.3) !important; border-radius: 3px; }
</style>
</head>
<body>
<div id="editor"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/ace/1.32.2/ace.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/ace/1.32.2/ext-language_tools.min.js"></script>
<script>
var editor = ace.edit("editor");
var isDirty = false;

editor.setOptions({
  theme: "ace/theme/monokai",
  enableBasicAutocompletion: true,
  enableSnippets: true,
  enableLiveAutocompletion: true,
  showPrintMargin: false,
  scrollPastEnd: 0.5,
  fontSize: "14px",
  tabSize: 2,
  useSoftTabs: true,
  wrap: false,
  showGutter: true,
  displayIndentGuides: true
});

editor.session.on('change', function() {
  if (!isDirty) {
    isDirty = true;
    updateTitle();
  }
});

function updateTitle() {
  Android.onDirtyChanged(isDirty);
}

editor.commands.addCommand({
  name: 'save',
  bindKey: {win: 'Ctrl-S', mac: 'Command-S'},
  exec: function(editor) { Android.onSave(); }
});
</script>
</body>
</html>
""".trimIndent()

    inner class EditorBridge {
        @JavascriptInterface
        fun onDirtyChanged(dirty: Boolean) {
            isDirty = dirty
            runOnUiThread {
                val indicator = if (dirty) " •" else ""
                binding.tvFileName.text = "${currentFile?.name ?: "Untitled"}$indicator"
            }
        }

        @JavascriptInterface
        fun onSave() {
            runOnUiThread { saveFile() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isDirty) {
                showUnsavedChangesDialog()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
