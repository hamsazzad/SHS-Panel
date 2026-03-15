package com.shspanel.app.utils

import java.io.File
import java.text.DecimalFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    fun getFileIcon(extension: String): Int {
        return when (extension.lowercase()) {
            "html", "htm" -> com.shspanel.app.R.drawable.ic_file_html
            "css" -> com.shspanel.app.R.drawable.ic_file_css
            "js" -> com.shspanel.app.R.drawable.ic_file_js
            "php" -> com.shspanel.app.R.drawable.ic_file_php
            "json" -> com.shspanel.app.R.drawable.ic_file_json
            "xml" -> com.shspanel.app.R.drawable.ic_file_xml
            "txt", "md" -> com.shspanel.app.R.drawable.ic_file_text
            "zip", "rar", "tar", "gz" -> com.shspanel.app.R.drawable.ic_file_zip
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> com.shspanel.app.R.drawable.ic_file_image
            "mp4", "mkv", "avi", "mov" -> com.shspanel.app.R.drawable.ic_file_video
            "mp3", "wav", "ogg", "flac" -> com.shspanel.app.R.drawable.ic_file_audio
            "apk" -> com.shspanel.app.R.drawable.ic_file_apk
            "py" -> com.shspanel.app.R.drawable.ic_file_code
            "kt", "java" -> com.shspanel.app.R.drawable.ic_file_code
            else -> com.shspanel.app.R.drawable.ic_file_generic
        }
    }

    fun isCodeFile(extension: String): Boolean {
        return extension.lowercase() in setOf(
            "html", "htm", "css", "js", "ts", "php", "json", "xml",
            "txt", "md", "py", "kt", "java", "c", "cpp", "h",
            "sh", "bash", "sql", "yaml", "yml", "toml", "ini",
            "conf", "config", "env", "gitignore", "htaccess",
            "jsx", "tsx", "vue", "svelte", "rb", "go", "rs",
            "dart", "swift", "gradle", "properties"
        )
    }

    fun isZipFile(extension: String): Boolean {
        return extension.lowercase() in setOf("zip", "jar", "apk", "aar")
    }

    fun extractZip(zipFile: File, targetDir: File): Boolean {
        return try {
            targetDir.mkdirs()
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().buffered().use { out ->
                            zis.copyTo(out)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createZip(sourceFiles: List<File>, outputZip: File, baseDir: File? = null): Boolean {
        return try {
            outputZip.parentFile?.mkdirs()
            ZipOutputStream(outputZip.outputStream().buffered()).use { zos ->
                sourceFiles.forEach { file ->
                    addToZip(zos, file, baseDir?.absolutePath ?: file.parent ?: "")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun addToZip(zos: ZipOutputStream, file: File, basePath: String) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                addToZip(zos, child, basePath)
            }
        } else {
            val entryName = file.absolutePath.removePrefix(basePath).trimStart('/', '\\')
            zos.putNextEntry(ZipEntry(entryName))
            file.inputStream().buffered().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }

    fun copyFile(src: File, dst: File): Boolean {
        return try {
            dst.parentFile?.mkdirs()
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
