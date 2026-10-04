package com.jarves.mh.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarves.mh.model.Project
import com.jarves.mh.ui.theme.PocketGreen
import com.jarves.mh.ui.theme.PocketOrange
import java.io.File

@Composable
fun WorkspacePreviewTab(
    activeProject: Project?,
    previewReady: Boolean,
    serverUrl: String?,
    isAndroidProject: Boolean,
    androidBuildRunning: Boolean,
    androidBuildMessage: String?,
    onBuildAndRunAndroid: () -> Unit,
) {
    val context = LocalContext.current
    val workspaceRoot = remember(activeProject) {
        if (activeProject != null) {
            File(context.filesDir, "workspaces/${activeProject.id}").canonicalFile
        } else null
    }

    // Check for static index.html in project workspace
    val localIndexFile = remember(workspaceRoot, activeProject) {
        if (workspaceRoot != null && workspaceRoot.isDirectory) {
            listOf("", "public", "dist", "build", "src")
                .map { File(workspaceRoot, if (it.isEmpty()) "index.html" else "$it/index.html") }
                .firstOrNull { it.isFile }
        } else null
    }

    var selectedPreviewMode by rememberSaveable(isAndroidProject, localIndexFile?.path) {
        mutableIntStateOf(if (isAndroidProject) 1 else 0)
    }

    val unknownAppsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
                onBuildAndRunAndroid()
            } else {
                Toast.makeText(context, "Allow app installs to run Android projects", Toast.LENGTH_LONG).show()
            }
        },
    )

    Column(Modifier.fillMaxSize()) {
        if (isAndroidProject) {
            TabRow(
                selectedTabIndex = selectedPreviewMode,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab(
                    selected = selectedPreviewMode == 0,
                    onClick = { selectedPreviewMode = 0 },
                    text = { Text("Web Preview") },
                    icon = { Icon(Icons.Default.Language, null, Modifier.size(16.dp)) },
                )
                Tab(
                    selected = selectedPreviewMode == 1,
                    onClick = { selectedPreviewMode = 1 },
                    text = { Text("Android App") },
                    icon = { Icon(Icons.Default.Android, null, Modifier.size(16.dp)) },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            if (selectedPreviewMode == 1 && isAndroidProject) {
                AndroidAppPreviewView(
                    project = activeProject,
                    androidBuildRunning = androidBuildRunning,
                    androidBuildMessage = androidBuildMessage,
                    onBuildAndRun = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !context.packageManager.canRequestPackageInstalls()
                        ) {
                            unknownAppsLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        } else {
                            onBuildAndRunAndroid()
                        }
                    },
                )
            } else {
                WebInstantPreviewView(
                    localIndexFile = localIndexFile,
                    serverUrl = serverUrl,
                    ready = previewReady,
                )
            }
        }
    }
}

@Composable
private fun WebInstantPreviewView(
    localIndexFile: File?,
    serverUrl: String?,
    ready: Boolean,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val initialUrl = remember(localIndexFile, serverUrl) {
        when {
            !serverUrl.isNullOrBlank() -> serverUrl
            localIndexFile != null -> "file://${localIndexFile.absolutePath}"
            else -> ""
        }
    }

    var address by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var activeUrl by rememberSaveable(initialUrl) { mutableStateOf(initialUrl.takeIf { it.isNotBlank() }) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(localIndexFile, serverUrl) {
        if (!serverUrl.isNullOrBlank()) {
            address = serverUrl
            activeUrl = serverUrl
        } else if (localIndexFile != null && (activeUrl.isNullOrBlank() || activeUrl?.startsWith("file://") == true)) {
            val fileUrl = "file://${localIndexFile.absolutePath}"
            address = fileUrl
            activeUrl = fileUrl
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isLocalFile = activeUrl?.startsWith("file://") == true
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLocalFile) PocketGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            text = if (isLocalFile) "Static Direct Preview" else "Dev Server",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLocalFile) PocketGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }

                    OutlinedTextField(
                        value = if (isLocalFile) "index.html (no Node.js server required)" else address,
                        onValueChange = {
                            address = it
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = isLocalFile,
                        placeholder = { Text("localhost:3000") },
                        trailingIcon = {
                            if (!isLocalFile) {
                                IconButton(onClick = { activeUrl = address }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go")
                                }
                            }
                        },
                    )

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            webView?.reload()
                        },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload preview")
                    }
                }

                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }

        val targetUrl = activeUrl
        if (targetUrl.isNullOrBlank()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Language, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("No Web Preview Available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Create an index.html file in your project or start a dev server in Terminal to preview here instantly.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loading = newProgress < 100
                            }
                        }
                        loadUrl(targetUrl)
                    }
                },
                update = { view ->
                    if (view.url != targetUrl) {
                        view.loadUrl(targetUrl)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AndroidAppPreviewView(
    project: Project?,
    androidBuildRunning: Boolean,
    androidBuildMessage: String?,
    onBuildAndRun: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = PocketGreen.copy(alpha = 0.18f),
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Android, null, tint = PocketGreen, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            project?.name ?: "Android App",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Secure Native Linux Build Environment (PRoot · ARM64)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    InfoPill(label = "Platform", value = "Android 15 / 16")
                    InfoPill(label = "Toolchain", value = "Gradle 8.14")
                    InfoPill(label = "SDK", value = "API 36")
                }

                if (androidBuildRunning) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        androidBuildMessage ?: "Building debug APK…",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        onClick = onBuildAndRun,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Build & Install Native App on Phone")
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Secure Isolated Environment", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    "Gradle compilation and execution occur safely inside your device's isolated Ubuntu PRoot environment without requiring cloud build servers or external dependencies.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
