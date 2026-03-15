package com.shspanel.app.ui.filemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
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

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) importFiles(uris)
    }

    companion object {
        fun newInstance() = FileManagerFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            context = requireContext(),
            onItemClick = ::onFileClick,
            onItemLongClick = ::showContextMenu
        )
        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FileManagerFragment.adapter
        }
    }

    private fun setupFab() {
        binding.fabMain.setOnClickListener {
            showCreateDialog()
        }

        binding.fabImport.setOnClickListener {
            importLauncher.launch("*/*")
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            navigateUp()
        }

        binding.btnSelectAll.setOnClickListener {
            if (adapter.multiSelectMode) {
                adapter.selectAll()
            }
        }

        binding.btnDeleteSelected.setOnClickListener {
            deleteSelectedFiles()
        }

        binding.btnZipSelected.setOnClickListener {
            zipSelectedFiles()
        }
    }

    private fun loadDirectory(dir: File) {
        currentDir = dir
        updateBreadcrumb()
        binding.tvCurrentPath.text = dir.absolutePath

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val files = withContext(Dispatchers.IO) {
                dir.listFiles()
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?.map { FileItem(it) }
                    ?: emptyList()
            }
            binding.progressBar.visibility = View.GONE

            if (files.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.rvFiles.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.rvFiles.visibility = View.VISIBLE
                adapter.updateItems(files.toMutableList())
            }
        }
    }

    private fun updateBreadcrumb() {
        binding.breadcrumbContainer.removeAllViews()
        val parts = currentDir.absolutePath
            .removePrefix(Environment.getExternalStorageDirectory().absolutePath)
            .split("/")
            .filter { it.isNotEmpty() }

        val rootChip = createBreadcrumbChip("Storage")
        rootChip.setOnClickListener {
            navigateTo(Environment.getExternalStorageDirectory())
        }
        binding.breadcrumbContainer.addView(rootChip)

        var buildPath = Environment.getExternalStorageDirectory()
        parts.forEach { part ->
            buildPath = File(buildPath, part)
            val chip = createBreadcrumbChip(part)
            val targetPath = buildPath
            chip.setOnClickListener { navigateTo(targetPath) }
            binding.breadcrumbContainer.addView(chip)
        }
    }

    private fun createBreadcrumbChip(text: String): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isCheckable = false
            setChipBackgroundColorResource(R.color.chip_background)
            setTextColor(resources.getColor(R.color.accent_cyan, null))
        }
    }

    private fun onFileClick(item: FileItem) {
        if (item.isDirectory) {
            pathStack.addLast(currentDir)
            loadDirectory(item.file)
        } else if (FileUtils.isCodeFile(item.extension)) {
            openCodeEditor(item.file)
        } else if (FileUtils.isZipFile(item.extension)) {
            showZipOptions(item.file)
        } else {
            openWithDefault(item.file)
        }
    }

    private fun showContextMenu(item: FileItem, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.context_menu_file, popup.menu)

        if (!FileUtils.isZipFile(item.extension) && !item.isDirectory) {
            popup.menu.findItem(R.id.action_extract).isVisible = false
        }
        if (item.isDirectory || FileUtils.isZipFile(item.extension)) {
            popup.menu.findItem(R.id.action_open_editor).isVisible = false
            popup.menu.findItem(R.id.action_preview).isVisible = false
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
                    binding.multiSelectToolbar.visibility = View.VISIBLE
                }
            }
            true
        }
        popup.show()
    }

    private fun showCreateDialog() {
        val options = arrayOf("📁 New Folder", "📄 New File")
        AlertDialog.Builder(requireContext(), R.style.GlassDialog)
            .setTitle("Create New")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showInputDialog("New Folder", "Folder name") { name ->
                        createFolder(name)
                    }
                    1 -> showInputDialog("New File", "File name (e.g., index.html)") { name ->
                        createFile(name)
                    }
                }
            }
            .show()
    }

    private fun showInputDialog(title: String, hint: String, onConfirm: (String) -> Unit) {
        val editText = EditText(requireContext()).apply {
            this.hint = hint
            setPadding(50, 30, 50, 30)
        }
        AlertDialog.Builder(requireContext(), R.style.GlassDialog)
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
        val newDir = File(currentDir, name)
        if (newDir.exists()) {
            Toast.makeText(context, "Folder already exists", Toast.LENGTH_SHORT).show()
            return
        }
        if (newDir.mkdirs()) {
            loadDirectory(currentDir)
            Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFile(name: String) {
        val newFile = File(currentDir, name)
        if (newFile.exists()) {
            Toast.makeText(context, "File already exists", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            newFile.createNewFile()
            loadDirectory(currentDir)
            Toast.makeText(context, "File created", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog(item: FileItem) {
        val editText = EditText(requireContext()).apply {
            setText(item.name)
            setPadding(50, 30, 50, 30)
            selectAll()
        }
        AlertDialog.Builder(requireContext(), R.style.GlassDialog)
            .setTitle("Rename")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    val dest = File(currentDir, newName)
                    if (item.file.renameTo(dest)) {
                        loadDirectory(currentDir)
                        Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(item: FileItem) {
        AlertDialog.Builder(requireContext(), R.style.GlassDialog)
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
        AlertDialog.Builder(requireContext(), R.style.GlassDialog)
            .setTitle("Delete ${selected.size} items")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        selected.forEach { path -> FileUtils.deleteRecursive(File(path)) }
                    }
                    adapter.exitMultiSelectMode()
                    binding.multiSelectToolbar.visibility = View.GONE
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
        showInputDialog("Create ZIP", "Archive name (without .zip)") { name ->
            lifecycleScope.launch {
                val outputZip = File(currentDir, "$name.zip")
                val success = withContext(Dispatchers.IO) {
                    FileUtils.createZip(selected, outputZip, currentDir)
                }
                if (success) {
                    adapter.exitMultiSelectMode()
                    binding.multiSelectToolbar.visibility = View.GONE
                    loadDirectory(currentDir)
                    Toast.makeText(context, "ZIP created: $name.zip", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun compressItem(item: FileItem) {
        showInputDialog("Compress", "Archive name (without .zip)") { name ->
            lifecycleScope.launch {
                val outputZip = File(currentDir, "$name.zip")
                val success = withContext(Dispatchers.IO) {
                    FileUtils.createZip(listOf(item.file), outputZip, currentDir)
                }
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
        val options = arrayOf("📂 Extract Here", "📁 Extract To Folder...", "✏️ Open in Editor")
        AlertDialog.Builder(requireContext(), R.style.GlassDialog)
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
            binding.progressBar.visibility = View.VISIBLE
            val success = withContext(Dispatchers.IO) {
                FileUtils.extractZip(zipFile, currentDir)
            }
            binding.progressBar.visibility = View.GONE
            if (success) {
                loadDirectory(currentDir)
                Toast.makeText(context, "Extracted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Extraction failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExtractToDialog(zipFile: File) {
        showInputDialog("Extract To", "Folder name") { name ->
            lifecycleScope.launch {
                val targetDir = File(currentDir, name)
                binding.progressBar.visibility = View.VISIBLE
                val success = withContext(Dispatchers.IO) {
                    FileUtils.extractZip(zipFile, targetDir)
                }
                binding.progressBar.visibility = View.GONE
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
            binding.progressBar.visibility = View.VISIBLE
            var success = 0
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    try {
                        val fileName = getFileNameFromUri(uri) ?: "imported_file_${System.currentTimeMillis()}"
                        val destFile = File(currentDir, fileName)
                        requireContext().contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        success++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            binding.progressBar.visibility = View.GONE
            loadDirectory(currentDir)
            Toast.makeText(context, "Imported $success/${uris.size} files", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun openCodeEditor(file: File) {
        val intent = Intent(requireContext(), CodeEditorActivity::class.java).apply {
            putExtra(CodeEditorActivity.EXTRA_FILE_PATH, file.absolutePath)
        }
        startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun openPreview(file: File) {
        val intent = Intent(requireContext(), PreviewActivity::class.java).apply {
            putExtra(PreviewActivity.EXTRA_FILE_PATH, file.absolutePath)
        }
        startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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
            Toast.makeText(context, "No app to open this file", Toast.LENGTH_SHORT).show()
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
            if (parent != null && parent != currentDir) {
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
