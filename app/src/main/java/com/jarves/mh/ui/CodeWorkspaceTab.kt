package com.jarves.mh.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.model.ChangeItem
import com.jarves.mh.model.DiffLine
import com.jarves.mh.model.DiffLineType
import com.jarves.mh.network.GitHubRepository
import com.jarves.mh.model.WorkspaceEntry
import com.jarves.mh.ui.theme.PocketGreen
import com.jarves.mh.ui.theme.PocketOrange

enum class CodeSubTab {
    TREE,
    LIVE_EDITS,
    TERMINAL,
}

@Composable
fun CodeWorkspaceTab(
    files: List<WorkspaceEntry>,
    loading: Boolean,
    changes: List<ChangeItem>,
    suggestedProjectRoot: String?,
    onRefresh: () -> Unit,
    onOpenFile: (WorkspaceEntry) -> Unit,
    onUseSuggestedProjectRoot: () -> Unit,
    onExport: () -> Unit,
    onUndoChanges: () -> Unit,
    onKeepChanges: () -> Unit,
    onUndoFileChange: (String) -> Unit,
    onKeepFileChange: (String) -> Unit,
    onPushToGitHub: (repoName: String, isNewRepo: Boolean, isPrivate: Boolean, commitMessage: String) -> Unit,
    githubConnected: Boolean,
    githubRepositories: List<GitHubRepository>,
    terminalScreen: @Composable () -> Unit,
) {
    var selectedSubTab by rememberSaveable { mutableStateOf(CodeSubTab.TREE) }
    var showGitHubDialog by rememberSaveable { mutableStateOf(false) }

    if (showGitHubDialog) {
        GitHubPushDialog(
            githubConnected = githubConnected,
            repositories = githubRepositories,
            onDismiss = { showGitHubDialog = false },
            onPush = { repo, isNew, isPriv, msg ->
                showGitHubDialog = false
                onPushToGitHub(repo, isNew, isPriv, msg)
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Sub-navigation bar with pills
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CodeSubTabChip(
                        label = "Files Tree",
                        icon = Icons.Default.Folder,
                        selected = selectedSubTab == CodeSubTab.TREE,
                        onClick = { selectedSubTab = CodeSubTab.TREE },
                    )
                    CodeSubTabChip(
                        label = if (changes.isNotEmpty()) "Live Edits (${changes.size})" else "AI Edits",
                        icon = Icons.Default.Code,
                        selected = selectedSubTab == CodeSubTab.LIVE_EDITS,
                        badgeCount = changes.size,
                        onClick = { selectedSubTab = CodeSubTab.LIVE_EDITS },
                    )
                    CodeSubTabChip(
                        label = "Terminal",
                        icon = Icons.Default.Terminal,
                        selected = selectedSubTab == CodeSubTab.TERMINAL,
                        onClick = { selectedSubTab = CodeSubTab.TERMINAL },
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showGitHubDialog = true },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = "Push to GitHub",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !loading,
                        modifier = Modifier.size(34.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh files", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (selectedSubTab) {
                CodeSubTab.TREE -> FileTreeView(
                    files = files,
                    loading = loading,
                    changes = changes,
                    suggestedProjectRoot = suggestedProjectRoot,
                    onRefresh = onRefresh,
                    onOpenFile = onOpenFile,
                    onUseSuggestedProjectRoot = onUseSuggestedProjectRoot,
                    onExport = onExport,
                )
                CodeSubTab.LIVE_EDITS -> LiveAiEditsView(
                    changes = changes,
                    onUndoChanges = onUndoChanges,
                    onKeepChanges = onKeepChanges,
                    onUndoFileChange = onUndoFileChange,
                    onKeepFileChange = onKeepFileChange,
                )
                CodeSubTab.TERMINAL -> terminalScreen()
            }
        }
    }
}

@Composable
private fun CodeSubTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (badgeCount > 0 && !selected) {
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(PocketOrange, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun FileTreeView(
    files: List<WorkspaceEntry>,
    loading: Boolean,
    changes: List<ChangeItem>,
    suggestedProjectRoot: String?,
    onRefresh: () -> Unit,
    onOpenFile: (WorkspaceEntry) -> Unit,
    onUseSuggestedProjectRoot: () -> Unit,
    onExport: () -> Unit,
) {
    var expandedDirectories by rememberSaveable { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(files.map { it.path }) {
        val directories = files.asSequence().filter { it.isDirectory }.map { it.path }.toSet()
        expandedDirectories = expandedDirectories.filter { it in directories }
    }
    val expandedSet = expandedDirectories.toSet()
    val visibleFiles = files.filter { entry ->
        val segments = entry.path.split('/')
        segments.size == 1 || (1 until segments.size).all { depth ->
            segments.take(depth).joinToString("/") in expandedSet
        }
    }
    val directChildCounts = files.filter { candidate ->
        candidate.path.contains('/')
    }.groupingBy { candidate -> candidate.path.substringBeforeLast('/') }.eachCount()

    val changedPaths = remember(changes) { changes.map { it.path }.toSet() }

    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (suggestedProjectRoot != null) {
            item(key = "suggested-project-root") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Project folder detected", fontWeight = FontWeight.Bold)
                        Text(
                            "Use $suggestedProjectRoot as the project root so Chat, Terminal, and Preview run from the same folder.",
                            fontSize = 13.sp,
                        )
                        Button(onClick = onUseSuggestedProjectRoot, modifier = Modifier.fillMaxWidth()) {
                            Text("Use $suggestedProjectRoot as project root")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (files.isEmpty() && !loading) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Folder, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No files in workspace yet", fontWeight = FontWeight.Medium)
                        Text(
                            "Ask the assistant to generate code or create a file in Terminal.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(visibleFiles, key = { it.path }) { entry ->
            val isModifiedByAi = entry.path in changedPaths
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (entry.isDirectory) {
                            expandedDirectories = if (entry.path in expandedSet) {
                                expandedDirectories - entry.path
                            } else {
                                expandedDirectories + entry.path
                            }
                        } else {
                            onOpenFile(entry)
                        }
                    }
                    .padding(start = (entry.depth * 18).dp)
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (entry.isDirectory) {
                    Icon(
                        if (entry.path in expandedSet) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        if (entry.path in expandedSet) "Collapse folder" else "Expand folder",
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                } else {
                    Spacer(Modifier.width(20.dp))
                }

                val (icon, iconTint) = fileIconAndColor(entry)
                Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
                Spacer(Modifier.width(8.dp))

                Text(
                    text = if (entry.isDirectory) "${entry.name} (${directChildCounts[entry.path] ?: 0})" else entry.name,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isModifiedByAi) PocketOrange else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isModifiedByAi) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = PocketOrange.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Text(
                            "EDITED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PocketOrange,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }

                if (!entry.isDirectory) {
                    Text(
                        formatFileSize(entry.sizeBytes),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveAiEditsView(
    changes: List<ChangeItem>,
    onUndoChanges: () -> Unit,
    onKeepChanges: () -> Unit,
    onUndoFileChange: (String) -> Unit,
    onKeepFileChange: (String) -> Unit,
) {
    if (changes.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Check, contentDescription = null, Modifier.size(42.dp), tint = PocketGreen)
                Spacer(Modifier.height(12.dp))
                Text("No pending AI changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "When the assistant edits or writes files, live diffs will appear here in real time.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("${changes.size} modified files", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val totalAdditions = changes.sumOf { it.additions }
                        val totalDeletions = changes.sumOf { it.deletions }
                        Text(
                            "+$totalAdditions / -$totalDeletions lines",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onUndoChanges) {
                            Icon(Icons.Default.Undo, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Revert All", fontSize = 12.sp)
                        }
                        Button(onClick = onKeepChanges) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Accept All", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        items(changes, key = { it.path }) { item ->
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                item.path,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = PocketGreen.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    "+${item.additions}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PocketGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    "-${item.deletions}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    if (item.diffLines.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF14171E),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                item.diffLines.take(15).forEach { line ->
                                    val (bgColor, textColor, prefix) = when (line.type) {
                                        DiffLineType.ADDITION -> Triple(Color(0xFF1E3A2B), Color(0xFF4ADE80), "+ ")
                                        DiffLineType.DELETION -> Triple(Color(0xFF3F1F24), Color(0xFFF87171), "- ")
                                        DiffLineType.INFO -> Triple(Color.Transparent, Color(0xFF60A5FA), "@@ ")
                                        DiffLineType.CONTEXT -> Triple(Color.Transparent, Color(0xFF94A3B8), "  ")
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(bgColor)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = prefix + line.text,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (item.diffLines.size > 15) {
                                    Text(
                                        "+ ${item.diffLines.size - 15} more lines…",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B),
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onUndoFileChange(item.path) }) {
                            Text("Revert", fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onKeepFileChange(item.path) }) {
                            Text("Accept", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubPushDialog(
    githubConnected: Boolean,
    repositories: List<GitHubRepository>,
    onDismiss: () -> Unit,
    onPush: (repoName: String, isNewRepo: Boolean, isPrivate: Boolean, commitMessage: String) -> Unit,
) {
    var isNewRepo by rememberSaveable { mutableStateOf(repositories.isEmpty()) }
    var selectedRepoName by rememberSaveable { mutableStateOf(repositories.firstOrNull()?.fullName ?: "") }
    var newRepoName by rememberSaveable { mutableStateOf("") }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var commitMessage by rememberSaveable { mutableStateOf("Update from Mobile Harness") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Push to GitHub")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !isNewRepo,
                        onClick = { isNewRepo = false },
                        label = "Existing Repo",
                    )
                    FilterChip(
                        selected = isNewRepo,
                        onClick = { isNewRepo = true },
                        label = "Create New Repo",
                    )
                }

                if (!isNewRepo) {
                    if (repositories.isNotEmpty()) {
                        Text("Select repository:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        repositories.take(6).forEach { repo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRepoName = repo.fullName }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedRepoName == repo.fullName,
                                    onClick = { selectedRepoName = repo.fullName },
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(repo.fullName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(if (repo.private) "Private" else "Public", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = selectedRepoName,
                        onValueChange = { selectedRepoName = it },
                        label = { Text("Repository name or URL") },
                        placeholder = { Text("owner/repo or https://github.com/…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it },
                        label = { Text("New repository name") },
                        placeholder = { Text("my-awesome-app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPrivate = !isPrivate },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isPrivate, onClick = { isPrivate = true })
                        Text("Private repository", fontSize = 13.sp)
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = !isPrivate, onClick = { isPrivate = false })
                        Text("Public", fontSize = 13.sp)
                    }
                }

                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit message") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val repo = if (isNewRepo) newRepoName.trim() else selectedRepoName.trim()
                    if (repo.isNotBlank()) {
                        onPush(repo, isNewRepo, isPrivate, commitMessage)
                    }
                },
                enabled = if (isNewRepo) newRepoName.isNotBlank() else selectedRepoName.isNotBlank(),
            ) {
                Text("Push Code")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun fileIconAndColor(entry: WorkspaceEntry): Pair<ImageVector, Color> {
    if (entry.isDirectory) return Pair(Icons.Default.Folder, PocketOrange)
    val ext = entry.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts", "java" -> Pair(Icons.Default.Code, Color(0xFF7C3AED))
        "py" -> Pair(Icons.Default.Code, Color(0xFF3B82F6))
        "js", "jsx", "ts", "tsx" -> Pair(Icons.Default.Code, Color(0xFFEAB308))
        "html", "htm" -> Pair(Icons.Default.Code, Color(0xFFF97316))
        "css", "scss" -> Pair(Icons.Default.Code, Color(0xFF06B6D4))
        "json", "xml", "yaml", "yml", "toml" -> Pair(Icons.Default.Description, Color(0xFF10B981))
        "md", "txt", "log" -> Pair(Icons.Default.Description, Color(0xFF94A3B8))
        "png", "jpg", "jpeg", "webp", "gif", "svg" -> Pair(Icons.Default.Image, Color(0xFFEC4899))
        "zip", "tar", "gz" -> Pair(Icons.Default.FolderZip, Color(0xFFF59E0B))
        else -> Pair(Icons.Default.Description, Color(0xFF94A3B8))
    }
}
