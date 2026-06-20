package com.termux.manager.data.fs

/** 列表项:一个文件或目录的展示信息。 */
data class FileEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val ref: NodeRef,
)
