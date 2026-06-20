package com.termux.manager.data.fs

/**
 * 统一文件后端抽象。SAF(公共存储)与未来的 SFTP(Termux Home)都实现此接口,
 * 使浏览与操作在上层保持一致。M1 仅需列举;mkdir/rename/move/copy/delete 在后续里程碑加入。
 */
interface FileBackend {
    suspend fun list(dir: NodeRef): List<FileEntry>
}
