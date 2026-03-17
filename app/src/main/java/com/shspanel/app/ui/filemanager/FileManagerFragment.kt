package com.shspanel.app.ui.filemanager

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shspanel.app.R
import com.shspanel.app.databinding.FragmentFileManagerBinding
import com.shspanel.app.model.FileItem
import com.shspanel.app.ui.editor.CodeEditorActivity
import com.shspanel.app.ui.preview.PreviewActivity
import com.shspanel.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerFragment : Fragment() {

    private var _binding: FragmentFileManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FileAdapter
    private var currentDir: File = Environment.getExternalStorageDirectory()
    private val pathStack = ArrayDeque<File>()

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) importFiles(uris)
    }

    companion object {
        fun newInstance() = FileManagerFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        setupToolbar()
        loadDirectory(currentDir)
    }

    fun reload() {
        if (_binding != null) loadDirectory(currentDir)
    }

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            context = requireContext(),
            onItemClick = ::onFileClick,
            onItemLongClick = ::showContextMenu
        )
        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FileManagerFragment.adapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun setupFab() {
        binding.fabMain.setOnClickListener {
            showCreateDialog()
        }
        binding.fabImport.setOnClickListener {
            try {
                importLauncher.launch("*/*")
            } catch (e: Exception) {
                Toast.makeText(context, "File picker not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { navigateUp() }
        binding.btnSelectAll.setOnClickListener {
            if (adapter.multiSelectMode) adapter.selectAll()
        }
        binding.btnDeleteSelected.setOnClickListener { deleteSelectedFiles() }
        binding.btnZipSelected.setOnClickListener { zipSelectedFiles() }
    }

    private fun loadDirectory(dir: File) {
        currentDir = dir
        updateBreadcrumb()
        val b = _binding ?: return
        b.tvCurrentPath.text = dir.absolutePath

        lifecycleScope.launch {
            val b2 = _binding ?: return@launch
            b2.progressBar.visibility = View.VISIBLE
            b2.rvFiles.visibility = View.GONE
            b2.tvEmptyState.visibility = View.GONE

            val files = withContext(Dispatchers.IO) {
                try {
                    dir.listFiles()
                        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        ?.map { f ->
                            val childCount = if (f.isDirectory) {
                                try { f.listFiles()?.size ?: 0 } catch (_: Exception) { 0 }
                            } else -1
                            FileItem(file = f, childCount = childCount)
                        }
                        ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val b3 = _binding ?: return@launch
            b3.progressBar.visibility = View.GONE

            if (files.isEmpty()) {
                b3.tvEmptyState.visibility = View.VISIBLE
                b3.rvFiles.visibility = View.GONE
            } else {
                b3.tvEmptyState.visibility = View.GONE
                b3.rvFiles.visibility = View.VISIBLE
                adapter.updateItems(files.toMutableList())
            }
        }
    }

    private fun updateBreadcrumb() {
        val b = _binding ?: return
        b.breadcrumbContainer.removeAllViews()

        val sdCard = Environment.getExternalStorageDirectory()
        val parts = currentDir.absolutePath
            .removePrefix(sdCard.absolutePath)
            .split("/")
            .filter { it.isNotEmpty() }

        val rootView = makeBreadcrumbItem("Storage")
        rootView.setOnClickListener { navigateTo(sdCard) }
        b.breadcrumbContainer.addView(rootView)

        var buildPath = sdCard
        for (part in parts) {
            buildPath = File(buildPath, part)
            val sep = makeBreadcrumbSep()
            b.breadcrumbContainer.addView(sep)
            val item = makeBreadcrumbItem(part)
            val targetPath = buildPath
            item.setOnClickListener { navigateTo(targetPath) }
            b.breadcrumbContainer.addView(item)
        }
    }

    private fun makeBreadcrumbItem(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
            setPadding(8, 4, 8, 4)
        }
    }

    private fun makeBreadcrumbSep(): TextView {
        return TextView(requireContext()).apply {
            text = "›"
            setTextColor(Color.parseColor("#4a6080"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(2, 4, 2, 4)
        }
    }

    private fun onFileClick(item: FileItem) {
        when {
            item.isDirectory -> {
                pathStack.addLast(currentDir)
                loadDirectory(item.file)
            }
            FileUtils.isCodeFile(item.extension) -> openCodeEditor(item.file)
            FileUtils.isZipFile(item.extension) -> showZipOptions(item.file)
            else -> openWithDefault(item.file)
        }
    }

    private fun showContextMenu(item: FileItem, anchor: View) {
        try {
            val popup = PopupMenu(requireContext(), anchor)
            popup.menuInflater.inflate(R.menu.context_menu_file, popup.menu)

            if (!FileUtils.isZipFile(item.extension) && !item.isDirectory) {
                popup.menu.findItem(R.id.action_extract)?.isVisible = false
            }
            if (item.isDirectory || FileUtils.isZipFile(item.extension)) {
                popup.menu.findItem(R.id.action_open_editor)?.isVisible = false
                popup.menu.findItem(R.id.action_preview)?.isVisible = false
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_rename -> showRenameDialog(item)
                    R.id.action_delete -> confirmDelete(item)
                    R.id.action_compress -> compressItem(item)
                    R.id.action_extract -> showExtractOptions(item.file)
                    R.id.action_open_editor -> openCodeEditor(item.file)
                    R.id.action_preview -> openPreview(item.file)
                    R.id.action_multi_select -> {
                        adapter.enterMultiSelectMode()
                        _binding?.multiSelectToolbar?.visibility = View.VISIBLE
                    }
                }
                true
            }
            popup.show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCreateDialog() {
        val options = arrayOf("New Folder", "New File")
        AlertDialog.Builder(requireContext())
            .setTitle("Create New")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showInputDialog("New Folder", "Folder name") { name -> createFolder(name) }
                    1 -> showInputDialog("New File", "File name (e.g. index.html)") { name -> createFile(name) }
                }
            }
            .show()
    }

    private fun showInputDialog(title: String, hint: String, onConfirm: (String) -> Unit) {
        val editText = EditText(requireContext()).apply {
            this.hint = hint
            setPadding(50, 30, 50, 30)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) onConfirm(name)
                else Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createFolder(name: String) {
        try {
            val newDir = File(currentDir, name)
            if (newDir.exists()) {
                Toast.makeText(context, "Already exists", Toast.LENGTH_SHORT).show(); return
            }
            if (newDir.mkdirs()) {
                loadDirectory(currentDir)
                Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed — check permissions", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFile(name: String) {
        try {
            val newFile = File(currentDir, name)
            if (newFile.exists()) {
                Toast.makeText(context, "File already exists", Toast.LENGTH_SHORT).show(); return
            }
            newFile.createNewFile()
            loadDirectory(currentDir)
            Toast.makeText(context, "File created", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog(item: FileItem) {
        val editText = EditText(requireContext()).apply {
            setText(item.name)
            setPadding(50, 30, 50, 30)
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    val dest = File(currentDir, newName)
                    if (item.file.renameTo(dest)) {
                        loadDirectory(currentDir)
                        Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(item: FileItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete")
            .setMessage("Delete \"${item.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                if (FileUtils.deleteRecursive(item.file)) {
                    loadDirectory(currentDir)
                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedFiles() {
        val selected = adapter.selectedItems.toList()
        if (selected.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selected.size} items")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        selected.forEach { path ->
                            try { FileUtils.deleteRecursive(File(path)) } catch (_: Exception) {}
                        }
                    }
                    val b = _binding ?: return@launch
                    adapter.exitMultiSelectMode()
                    b.multiSelectToolbar.visibility = View.GONE
                    loadDirectory(currentDir)
                    Toast.makeText(context, "Deleted ${selected.size} items", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun zipSelectedFiles() {
        val selected = adapter.selectedItems.map { File(it) }
        if (selected.isEmpty()) return
        showInputDialog("Create ZIP", "Archive name (no .zip)") { name ->
            lifecycleScope.launch {
                val outputZip = File(currentDir, "$name.zip")
                val success = withContext(Dispatchers.IO) {
                    FileUtils.createZip(selected, outputZip, currentDir)
                }
                val b = _binding ?: return@launch
                if (success) {
                    adapter.exitMultiSelectMode()
                    b.multiSelectToolbar.visibility = View.GONE
                    loadDirectory(currentDir)
                    Toast.makeText(context, "Created: $name.zip", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun compressItem(item: FileItem) {
        showInputDialog("Compress", "Archive name (no .zip)") { name ->
            lifecycleScope.launch {
                val outputZip = File(currentDir, "$name.zip")
                val success = withContext(Dispatchers.IO) {
                    FileUtils.createZip(listOf(item.file), outputZip, currentDir)
                }
                val b = _binding ?: return@launch
                if (success) {
                    loadDirectory(currentDir)
                    Toast.makeText(context, "Compressed to $name.zip", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Compression failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showZipOptions(zipFile: File) {
        val options = arrayOf("Extract Here", "Extract to Folder", "Open in Editor")
        AlertDialog.Builder(requireContext())
            .setTitle(zipFile.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> extractZipHere(zipFile)
                    1 -> showExtractToDialog(zipFile)
                    2 -> openCodeEditor(zipFile)
                }
            }.show()
    }

    private fun showExtractOptions(zipFile: File) = showZipOptions(zipFile)

    private fun extractZipHere(zipFile: File) {
        lifecycleScope.launch {
            val b = _binding ?: return@launch
            b.progressBar.visibility = View.VISIBLE
            val success = withContext(Dispatchers.IO) { FileUtils.extractZip(zipFile, currentDir) }
            val b2 = _binding ?: return@launch
            b2.progressBar.visibility = View.GONE
            if (success) {
                loadDirectory(currentDir)
                Toast.makeText(context, "Extracted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Extraction failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExtractToDialog(zipFile: File) {
        showInputDialog("Extract To", "Folder name") { name ->
            lifecycleScope.launch {
                val targetDir = File(currentDir, name)
                val b = _binding ?: return@launch
                b.progressBar.visibility = View.VISIBLE
                val success = withContext(Dispatchers.IO) { FileUtils.extractZip(zipFile, targetDir) }
                val b2 = _binding ?: return@launch
                b2.progressBar.visibility = View.GONE
                if (success) {
                    loadDirectory(currentDir)
                    Toast.makeText(context, "Extracted to $name/", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Extraction failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importFiles(uris: List<Uri>) {
        lifecycleScope.launch {
            val b = _binding ?: return@launch
            b.progressBar.visibility = View.VISIBLE
            var success = 0
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    try {
                        val fileName = getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
                        val destFile = File(currentDir, fileName)
                        requireContext().contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        success++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            val b2 = _binding ?: return@launch
            b2.progressBar.visibility = View.GONE
            loadDirectory(currentDir)
            Toast.makeText(context, "Imported $success/${uris.size} files", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            var name: String? = null
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
            name ?: uri.lastPathSegment
        } catch (_: Exception) { null }
    }

    private fun openCodeEditor(file: File) {
        try {
            startActivity(Intent(requireContext(), CodeEditorActivity::class.java).apply {
                putExtra(CodeEditorActivity.EXTRA_FILE_PATH, file.absolutePath)
            })
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open editor", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPreview(file: File) {
        try {
            startActivity(Intent(requireContext(), PreviewActivity::class.java).apply {
                putExtra(PreviewActivity.EXTRA_FILE_PATH, file.absolutePath)
            })
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open preview", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWithDefault(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app to open this file type", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateTo(dir: File) {
        pathStack.clear()
        loadDirectory(dir)
    }

    private fun navigateUp() {
        if (pathStack.isNotEmpty()) {
            loadDirectory(pathStack.removeLast())
        } else {
            val parent = currentDir.parentFile
            if (parent != null && parent.absolutePath != currentDir.absolutePath) {
                loadDirectory(parent)
            }
        }
    }

    fun onBackPressed(): Boolean {
        val storage = Environment.getExternalStorageDirectory()
        if (currentDir.absolutePath == storage.absolutePath) return false
        navigateUp()
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
