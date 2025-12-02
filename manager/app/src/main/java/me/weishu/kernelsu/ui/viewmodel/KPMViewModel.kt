package me.weishu.kernelsu.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import java.io.File

class KPMViewModel : ViewModel() {

    companion object {
        private const val TAG = "KPMViewModel"
        private var _modules by mutableStateOf<List<KPMInfo>>(emptyList())
    }

    @Immutable
    data class KPMInfo(
        val id: Int,
        val name: String,
        val version: String,
        val description: String,
        val state: Int,
        val size: Long,
        val refCount: Int,
        val flags: List<String>
    )

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isNeedRefresh by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var search by mutableStateOf(TextFieldValue("")) // compatible con ModuleViewModel

    val kpmList by derivedStateOf {
        val text = search.text
        if (text.isBlank()) {
            _modules
        } else {
            _modules.filter {
                it.name.contains(text, true) || it.description.contains(text, true)
            }
        }
    }

    init {
        fetchKPMList()
    }

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun fetchKPMList() {
        viewModelScope.launch {
            isLoading = true
            isRefreshing = true
            try {
                val modules = withContext(Dispatchers.IO) {
                    val out = mutableListOf<KPMInfo>()

                    kotlin.runCatching {
                        val count = try {
                            Natives.getKpmModuleCount()
                        } catch (e: Throwable) {
                            Log.w(TAG, "getKpmModuleCount failed", e)
                            -1
                        }

                        if (count > 0) {
                            val names = try {
                                Natives.getKpmModuleList()
                            } catch (e: Throwable) {
                                Log.w(TAG, "getKpmModuleList failed", e)
                                null
                            }

                            names?.forEachIndexed { idx, name ->
                                try {
                                    val info = Natives.getKpmModuleInfo(name)
                                    if (info != null) {
                                        out += KPMInfo(
                                            id = idx + 1,
                                            name = info.name,
                                            version = info.version,
                                            description = info.description,
                                            state = info.state,
                                            size = info.size,
                                            refCount = info.refCount,
                                            flags = emptyList()
                                        )
                                    }
                                } catch (e: Throwable) {
                                    Log.w(TAG, "getKpmModuleInfo failed for $name", e)
                                }
                            }
                        }

                        out
                    }.getOrElse {
                        Log.e(TAG, "fetchKPMList: parsing native result failed", it)
                        emptyList()
                    }
                }

                _modules = modules.sortedBy { it.name }
                isNeedRefresh = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch KPM list", e)
                errorMessage = "Failed to fetch KPM modules: ${e.message}"
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun installKPM(uri: Uri, context: Context) {
        viewModelScope.launch {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tmp = File(context.cacheDir, "kpm_${System.currentTimeMillis()}.ko")
                    inputStream?.use { input ->
                        tmp.outputStream().use { out -> input.copyTo(out) }
                    }

                    val res = try {
                        Natives.loadKpmModule(tmp.absolutePath)
                    } catch (e: Throwable) {
                        Log.e(TAG, "loadKpmModule failed", e)
                        false
                    }
                    tmp.delete()
                    if (!res) throw Exception("Failed to load KPM")
                }
                isNeedRefresh = true
                fetchKPMList()
            } catch (e: Exception) {
                Log.e(TAG, "installKPM failed", e)
                errorMessage = "Install failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun unloadKPM(moduleId: Int) {
        viewModelScope.launch {
            isLoading = true
            try {
                val success = withContext(Dispatchers.IO) {
                    val module = _modules.find { it.id == moduleId }
                    if (module != null) {
                        try {
                            Natives.unloadKpmModule(module.name)
                        } catch (e: Throwable) {
                            Log.e(TAG, "unloadKpmModule threw", e)
                            false
                        }
                    } else false
                }
                if (!success) throw Exception("Failed to unload KPM")
                isNeedRefresh = true
                fetchKPMList()
            } catch (e: Exception) {
                Log.e(TAG, "unloadKPM failed", e)
                errorMessage = "Unload failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun controlKPM(moduleId: Int, operation: Int, arg: Long = 0L) {
        viewModelScope.launch {
            isLoading = true
            try {
                val res = withContext(Dispatchers.IO) {
                    val module = _modules.find { it.id == moduleId }
                    if (module != null) {
                        try {
                            Natives.controlKpmModule(module.name, operation, arg)
                        } catch (e: Throwable) {
                            Log.e(TAG, "controlKpmModule threw", e)
                            -1L
                        }
                    } else -1L
                }
                if (res < 0) throw Exception("control operation failed")
                isNeedRefresh = true
                fetchKPMList()
            } catch (e: Exception) {
                Log.e(TAG, "controlKPM failed", e)
                errorMessage = "Control failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshKPMList() {
        isNeedRefresh = true
        fetchKPMList()
    }

    fun clearError() {
        errorMessage = null
    }
}