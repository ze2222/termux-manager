package com.termux.manager.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.termux.manager.R
import com.termux.manager.data.fs.FileEntry
import com.termux.manager.databinding.ItemFileBinding

class FileAdapter(
    private val onClick: (FileEntry) -> Unit,
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private val items = mutableListOf<FileEntry>()

    fun submit(list: List<FileEntry>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].ref == list[n].ref
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == list[n]
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: FileEntry) {
            val ctx = b.root.context
            b.name.text = e.name
            b.icon.setImageResource(if (e.isDir) R.drawable.ic_folder else R.drawable.ic_file)
            b.info.text = if (e.isDir) {
                ctx.getString(R.string.folder_label)
            } else {
                val size = Formatter.formatShortFileSize(ctx, e.size)
                val time = DateUtils.getRelativeTimeSpanString(e.lastModified)
                "$size · $time"
            }
            b.root.setOnClickListener { onClick(e) }
        }
    }
}
