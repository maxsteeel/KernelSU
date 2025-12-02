package me.weishu.kernelsu.ui.screen

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.component.ConfirmResult
import me.weishu.kernelsu.ui.component.SearchAppBar
import me.weishu.kernelsu.ui.component.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.rememberLoadingDialog
import me.weishu.kernelsu.ui.util.*
import me.weishu.kernelsu.ui.viewmodel.KPMViewModel
import androidx.compose.material3.ElevatedCard

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun KPMScreen(navigator: DestinationsNavigator) {
    val viewModel: KPMViewModel = viewModel()
    val context = LocalContext.current
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (viewModel.kpmList.isEmpty() || viewModel.isNeedRefresh) {
            viewModel.fetchKPMList()
        }
    }

    val isSafeMode = Natives.isSafeMode
    val isManager = Natives.becomeManager(context.packageName)
    val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()

    val installKPMLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.installKPM(uri, context)
            }
        }
    }

    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()

    LaunchedEffect(viewModel.isLoading) {
        if (viewModel.isLoading) {
            loadingDialog.show()
        } else {
            loadingDialog.hide()
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let { message ->
            snackBarHost.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!fullFeatured) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = if (isSafeMode) {
                        "KPM functionality is disabled in safe mode"
                    } else {
                        "KPM requires root access and proper kernel support"
                    },
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        KPMList(
            navigator = navigator,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
            onInstallKPM = { installKPMLauncher.launch("*/*") },
            onUnloadKPM = { kpm ->
                scope.launch {
                    val result = confirmDialog.awaitConfirm(
                        title = "Unload KPM Module",
                        content = "Are you sure you want to unload ${kpm.name}?",
                        confirm = "Unload",
                        dismiss = "Cancel"
                    )
                    if (result == ConfirmResult.Confirmed) {
                        viewModel.unloadKPM(kpm.id)
                    }
                }
            },
            context = context,
            snackBarHost = snackBarHost,
            enabled = fullFeatured
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KPMList(
    navigator: DestinationsNavigator,
    viewModel: KPMViewModel,
    modifier: Modifier = Modifier,
    onInstallKPM: () -> Unit,
    onUnloadKPM: (KPMViewModel.KPMInfo) -> Unit,
    context: Context,
    snackBarHost: SnackbarHostState,
    enabled: Boolean = true
) {
    val listState = rememberLazyListState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }

    val filteredKPMList = remember(viewModel.kpmList, searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.kpmList
        } else {
            viewModel.kpmList.filter { kpm ->
                kpm.name.contains(searchQuery, ignoreCase = true) ||
                kpm.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = { viewModel.fetchKPMList() },
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with install button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "KPM Modules (${viewModel.kpmList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (enabled) {
                        FloatingActionButton(
                            onClick = onInstallKPM,
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Install KPM")
                        }
                    }
                }
            }

            // Search bar
            if (viewModel.kpmList.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search KPM modules") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // KPM modules list
            if (filteredKPMList.isEmpty() && !viewModel.isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "No KPM modules installed"
                                } else {
                                    "No modules match your search"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "Install KPM modules to extend kernel functionality"
                                } else {
                                    "Try a different search term"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredKPMList) { kpm ->
                    KPMItem(
                        kpm = kpm,
                        onUnload = { onUnloadKPM(kpm) },
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
fun KPMItem(
    kpm: KPMViewModel.KPMInfo,
    onUnload: () -> Unit,
    enabled: Boolean = true
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatSize(size: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var sizeFloat = size.toFloat()
    var unitIndex = 0
    
    while (sizeFloat >= 1024 && unitIndex < units.size - 1) {
        sizeFloat /= 1024
        unitIndex++
    }
    
    return "%.1f %s".format(sizeFloat, units[unitIndex])
}

@Preview
@Composable
fun KPMItemPreview() {
    MaterialTheme {
        KPMItem(
            kpm = KPMViewModel.KPMInfo(
                id = 1,
                name = "Sample KPM Module",
                version = "1.0.0",
                description = "This is a sample KPM module for demonstration purposes",
                state = "loaded",
                size = 1024 * 1024,
                refCount = 2,
                flags = listOf("LIVE", "UNLOAD_OK")
            ),
            onUnload = {},
            enabled = true
        )
    }
}