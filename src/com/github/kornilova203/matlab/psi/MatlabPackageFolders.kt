package com.github.kornilova203.matlab.psi

import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*

/**
 * Changes when package and class folders (+pkg, @Cls) may have changed: a folder created, deleted, moved or
 * renamed, or a file added to or taken out of one. Other file changes (editing, files created elsewhere, git
 * writing its objects) leave it alone, so what's computed from those folders isn't recomputed all the time.
 */
object MatlabPackageFolders : SimpleModificationTracker() {
    /** Whether [event] may change which package or class folders exist, or what's in them. */
    fun matters(event: VFileEvent): Boolean = when (event) {
        is VFileContentChangeEvent -> false
        is VFilePropertyChangeEvent ->
            event.propertyName == com.intellij.openapi.vfs.VirtualFile.PROP_NAME &&
                    (isFolder(event) || inPackage(event.path) || isMatlabName(event.oldValue) || isMatlabName(event.newValue))
        // Creating, deleting, moving or copying: any folder (it may hold packages), or files in package folders
        else -> isFolder(event) || inPackage(event.path) || (event is VFileMoveEvent && inPackage(event.oldPath))
    }

    private fun isFolder(event: VFileEvent): Boolean = when (event) {
        is VFileCreateEvent -> event.isDirectory
        else -> event.file?.isDirectory == true
    }

    private fun isMatlabName(name: Any?): Boolean = (name as? String)?.let { it.startsWith("+") || it.startsWith("@") } == true

    /** A path inside a +package or @class folder. */
    fun inPackage(path: String): Boolean = path.contains("/+") || path.contains("/@") || path.contains("\\+") || path.contains("\\@")
}

/** Counts the VFS events that matter to [MatlabPackageFolders]. */
class MatlabPackageFoldersListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.any(MatlabPackageFolders::matters)) MatlabPackageFolders.incModificationCount()
    }
}
