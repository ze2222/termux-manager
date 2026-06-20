package com.termux.manager.data.fs

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 SAF(Storage Access Framework)的文件后端。
 * 列举使用 ContentResolver 直接查询 children,避免 DocumentFile.listFiles() 的逐项开销。
 */
class SafBackend(private val context: Context) : FileBackend {

    private val resolver get() = context.contentResolver

    override suspend fun list(dir: NodeRef): List<FileEntry> = withContext(Dispatchers.IO) {
        val parentDocId = DocumentsContract.getDocumentId(dir.uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dir.uri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val out = ArrayList<FileEntry>()
        resolver.query(childrenUri, projection, null, null, null)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val lmIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (c.moveToNext()) {
                val docId = c.getString(idIdx)
                val name = c.getString(nameIdx) ?: continue
                val isDir = c.getString(mimeIdx) == DocumentsContract.Document.MIME_TYPE_DIR
                val childUri = DocumentsContract.buildDocumentUriUsingTree(dir.uri, docId)
                out.add(
                    FileEntry(
                        name = name,
                        isDir = isDir,
                        size = if (c.isNull(sizeIdx)) 0L else c.getLong(sizeIdx),
                        lastModified = if (c.isNull(lmIdx)) 0L else c.getLong(lmIdx),
                        ref = NodeRef(childUri),
                    )
                )
            }
        }
        out.sortWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
        out
    }

    /** 是否已持久化授权「内部存储根(primary)」。 */
    fun hasPrimaryRootAccess(): Boolean =
        resolver.persistedUriPermissions.any {
            it.isReadPermission && it.isWritePermission && it.uri.isPrimaryRoot()
        }

    /** 确保工作区 Documents/Termux-Manager 存在,返回其引用;不存在则创建。 */
    suspend fun ensureWorkspace(): NodeRef = withContext(Dispatchers.IO) {
        val tree = PRIMARY_ROOT_TREE_URI
        val workspaceUri = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:$WORKSPACE_REL")
        if (exists(workspaceUri)) return@withContext NodeRef(workspaceUri)
        val documentsUri = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Documents")
        val created = DocumentsContract.createDocument(
            resolver, documentsUri, DocumentsContract.Document.MIME_TYPE_DIR, WORKSPACE_NAME
        ) ?: error("无法创建工作区目录 $WORKSPACE_REL")
        NodeRef(created)
    }

    private fun exists(uri: Uri): Boolean = runCatching {
        resolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
        )?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    companion object {
        const val WORKSPACE_NAME = "Termux-Manager"
        const val WORKSPACE_REL = "Documents/Termux-Manager"
        private const val EXTERNAL_AUTHORITY = "com.android.externalstorage.documents"

        /** 内部存储根的 tree uri:content://com.android.externalstorage.documents/tree/primary%3A */
        val PRIMARY_ROOT_TREE_URI: Uri =
            DocumentsContract.buildTreeDocumentUri(EXTERNAL_AUTHORITY, "primary:")

        private fun Uri.isPrimaryRoot(): Boolean =
            runCatching { DocumentsContract.getTreeDocumentId(this) == "primary:" }.getOrDefault(false)
    }
}
