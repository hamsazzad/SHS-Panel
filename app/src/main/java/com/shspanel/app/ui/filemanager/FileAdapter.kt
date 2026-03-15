package com.shspanel.app.ui.filemanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shspanel.app.R
import com.shspanel.app.model.FileItem
import com.shspanel.app.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val context: Context,
    private var items: MutableList<FileItem> = mutableListOf(),
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem, View) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    var multiSelectMode = false
    val selectedItems = mutableSetOf<String>()

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivFileIcon)
        val name: TextView = view.findViewById(R.id.tvFileName)
        val info: TextView = view.findViewById(R.id.tvFileInfo)
        val checkbox: CheckBox = view.findViewById(R.id.cbSelect)
        val typeIndicator: View = view.findViewById(R.id.viewTypeIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = items[position]
        val animation = AnimationUtils.loadAnimation(context, R.anim.item_slide_in)
        holder.itemView.startAnimation(animation)

        holder.name.text = item.name

        if (item.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder)
            val count = item.file.listFiles()?.size ?: 0
            holder.info.text = "$count items"
            holder.typeIndicator.setBackgroundResource(R.drawable.indicator_folder)
        } else {
            holder.icon.setImageResource(FileUtils.getFileIcon(item.extension))
            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(item.lastModified))
            holder.info.text = "${FileUtils.formatFileSize(item.size)} • $dateStr"
            holder.typeIndicator.setBackgroundResource(R.drawable.indicator_file)
        }

        if (multiSelectMode) {
            holder.checkbox.visibility = View.VISIBLE
            holder.checkbox.isChecked = selectedItems.contains(item.path)
        } else {
            holder.checkbox.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (multiSelectMode) {
                toggleSelection(item, holder)
            } else {
                onItemClick(item)
            }
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(item, it)
            true
        }

        holder.checkbox.setOnClickListener {
            toggleSelection(item, holder)
        }
    }

    private fun toggleSelection(item: FileItem, holder: FileViewHolder) {
        if (selectedItems.contains(item.path)) {
            selectedItems.remove(item.path)
            holder.checkbox.isChecked = false
        } else {
            selectedItems.add(item.path)
            holder.checkbox.isChecked = true
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<FileItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun enterMultiSelectMode() {
        multiSelectMode = true
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun exitMultiSelectMode() {
        multiSelectMode = false
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun selectAll() {
        items.forEach { selectedItems.add(it.path) }
        notifyDataSetChanged()
    }
}
