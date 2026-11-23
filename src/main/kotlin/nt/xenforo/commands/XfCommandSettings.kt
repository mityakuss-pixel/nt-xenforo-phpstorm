package nt.xenforo.commands

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
@State(name = "XenForoCommands", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class XfCommandSettings : SimplePersistentStateComponent<XfCommandSettings.Options>(Options()) {
    class Options : BaseState() {
        var interpreter by string()
        var root by string()
        var followAddOnPhpLevel by property(false)
    }

    var followAddOnPhpLevel: Boolean
        get() = state.followAddOnPhpLevel
        set(value) {
            state.followAddOnPhpLevel = value
        }

    var interpreter: String?
        get() = state.interpreter
        set(value) {
            state.interpreter = value?.takeIf { it.isNotBlank() }
        }

    var root: String?
        get() = state.root
        set(value) {
            state.root = value?.trim()?.takeIf { it.isNotEmpty() }
        }

    fun rootDirectory(): VirtualFile? = root?.let { LocalFileSystem.getInstance().findFileByPath(it) }?.takeIf { it.isDirectory }

    companion object {
        fun getInstance(project: Project): XfCommandSettings = project.service()
    }
}
