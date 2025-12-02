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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val pullToRefreshState = rememberPullToRefreshState()
    val onRefresh: () -> Unit = { scope.launch { viewModel.fetchKPMList() } }
    val scaleFraction = {
        if (viewModel.isRefreshing) 1f
        else 1f // keep simple; could be animated like Module
    }

    Scaffold(
        modifier = Modifier.pullToRefresh(
            state = pullToRefreshState,
            isRefreshing = viewModel.isRefreshing,
            onRefresh = onRefresh
        ),
        topBar = {
            SearchAppBar(
                title = { Text("KPM") },
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                onClearClick = { viewModel.search = androidx.compose.ui.text.input.TextFieldValue("") },
                actionsContent = {}, // no extra actions for now
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (fullFeatured) {
                ExtendedFloatingActionButton(
                    onClick = { installKPMLauncher.launch("*/*") },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Install KPM") },
                    text = { Text("Install") }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackBarHost) }
    ) { innerPadding ->

        if (!fullFeatured) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp)
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
        }

        KPMList(
            navigator = navigator,
            viewModel = viewModel,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            boxModifier = Modifier.padding(innerPadding),
            pullToRefreshState = pullToRefreshState,
            isRefreshing = viewModel.isRefreshing,
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
            enabled = fullFeatured,
            scaleFraction = scaleFraction(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KPMList(
    navigator: DestinationsNavigator,
    viewModel: KPMViewModel,
    modifier: Modifier = Modifier,
    boxModifier: Modifier = Modifier,
    pullToRefreshState: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    isRefreshing: Boolean,
    onInstallKPM: () -> Unit,
    onUnloadKPM: (KPMViewModel.KPMInfo) -> Unit,
    context: Context,
    snackBarHost: SnackbarHostState,
    enabled: Boolean = true,
    scaleFraction: Float = 1f,
) {
    val listState = rememberLazyListState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }

    val installLabel = stringResource(R.string.kpm_load)

    ExtendedFloatingActionButton(
        onClick = onInstallKPM,
        icon = { Icon(Icons.Default.Add, contentDescription = installLabel) },
        text = { Text(installLabel) }
    )
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

    Box(modifier = modifier
        .fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = remember {
                PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + 56.dp + 16.dp + 48.dp + 6.dp // match Module.kt spacing for FAB + snackbar
                )
            },
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
                        // keep only small FAB in header if needed, otherwise rely on Scaffold FAB
                        IconButton(onClick = onInstallKPM) {
                            Icon(Icons.Default.Add, contentDescription = installLabel)
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TonalCard(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth(0.95f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
    Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .scale(scaleFraction)
        ) {
            PullToRefreshDefaults.LoadingIndicator(state = pullToRefreshState, isRefreshing = isRefreshing)
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

    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = kpm.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = "v${kpm.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = { /* show details */ }) { Icon(Icons.Outlined.Info, contentDescription = null) }
                    IconButton(onClick = { if (enabled) onUnload() }) { Icon(Icons.Outlined.Delete, contentDescription = null) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(text = kpm.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
                state = R.string.kpm_state_loaded,
                size = 1024 * 1024,
                refCount = 2,
                flags = listOf("LIVE", "UNLOAD_OK")
            ),
            onUnload = {},
            enabled = true
        )
    }
}