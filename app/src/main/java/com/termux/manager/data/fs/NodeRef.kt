package com.termux.manager.data.fs

import android.net.Uri

/** 文件系统节点引用。当前仅 SAF(document uri);M4 接入 SFTP 后端时再扩展。 */
data class NodeRef(val uri: Uri)
