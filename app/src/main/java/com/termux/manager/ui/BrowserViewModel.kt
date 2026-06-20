package com.termux.manager.ui

import android.app.Application
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.termux.manager.data.fs.FileEntry
import com.termux.manager.data.fs.NodeRef
import com.termux.manager.data.fs.SafBackend
import kotlinx.coroutines.launch

class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val backend = SafBackend(app)
    private val stack = ArrayDeque<NodeRef>()

    private val _entries = MutableLiveData<List<FileEntry>>(emptyList())
    val entries: LiveData<List<FileEntry>> = _entries

    private val _path = MutableLiveData("")
    val path: LiveData<String> = _path

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** 打开(必要时创建)工作区,作为浏览起点。 */
    fun openWorkspace() {
        viewModelScope.launch {
            try {
                val ws = backend.ensureWorkspace()
                stack.clear()
                stack.addLast(ws)
                refresh()
            } catch (e: Exception) {
                _error.value = e.message ?: "打开工作区失败"
            }
        }
    }

    fun enter(dir: NodeRef) {
        stack.addLast(dir)
        refresh()
    }

    /** 返回上一级;已在栈底则返回 false。 */
    fun goUp(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        refresh()
        return true
    }

    private fun refresh() {
        val cur = stack.lastOrNull() ?: return
        viewModelScope.launch {
            try {
                _entries.value = backend.list(cur)
                _path.value = displayPath(cur)
            } catch (e: Exception) {
                _error.value = e.message ?: "读取目录失败"
            }
        }
    }

    private fun displayPath(ref: NodeRef): String {
        val docId = runCatching { DocumentsContract.getDocumentId(ref.uri) }.getOrNull() ?: return "/"
        return "/" + docId.substringAfter(':', "")
    }
}
