package com.example.fcmkeeper.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.fcmkeeper.BuildConfig
import com.example.fcmkeeper.KeeperApplication
import com.example.fcmkeeper.data.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LightColors = lightColorScheme(
    primary = Color(0xFF3859A8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E8FF), onPrimaryContainer = Color(0xFF132C62),
    background = Color(0xFFF8F9FF), surface = Color(0xFFF8F9FF),
    secondaryContainer = Color(0xFFE9EDF7), onSecondaryContainer = Color(0xFF37445D),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeeperApp(model: InboxViewModel) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else LightColors) {
        val all by model.items.collectAsStateWithLifecycle()
        val loading by model.loading.collectAsStateWithLifecycle()
        val loadError by model.loadError.collectAsStateWithLifecycle()
        val selectedId by model.selectedId.collectAsStateWithLifecycle()
        val selected = all.firstOrNull { it.id == selectedId }
        val token by model.token.collectAsStateWithLifecycle()
        val firebaseStatus by model.firebaseStatus.collectAsStateWithLifecycle()
        var filterName by rememberSaveable { mutableStateOf(InboxFilter.ALL.name) }
        var sortName by rememberSaveable { mutableStateOf(InboxSort.NEWEST.name) }
        var query by rememberSaveable { mutableStateOf("") }
        var settings by rememberSaveable { mutableStateOf(false) }
        var overflow by remember { mutableStateOf(false) }
        var clearDialog by rememberSaveable { mutableStateOf(false) }
        var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        val context = LocalContext.current
        val app = context.applicationContext as KeeperApplication
        var notificationsEnabled by remember { mutableStateOf(app.publisher.enabled()) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) notificationsEnabled = app.publisher.enabled()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsEnabled = app.publisher.enabled()
        }
        val preferences = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
        fun notificationSettings() {
            val intent = if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, "assignment")
            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        }
        val enableNotifications: () -> Unit = {
            if (Build.VERSION.SDK_INT >= 33 && !preferences.getBoolean("permissionRequested", false)) {
                preferences.edit().putBoolean("permissionRequested", true).apply()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else notificationSettings()
        }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(all) { now = System.currentTimeMillis(); while (true) { delay(60_000); now = System.currentTimeMillis() } }
        BackHandler(selectedId != null) { model.inbox() }
        val filter = InboxFilter.valueOf(filterName)
        val sort = InboxSort.valueOf(sortName)
        val visible = remember(all, filter, sort, query) { selectNotifications(all, filter, sort, query) }
        val unread = all.count { !it.isRead }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (selectedId == null) "FCM Keeper" else "Notification", fontWeight = FontWeight.SemiBold)
                            if (selectedId == null) Text("A little order for every update", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        if (selectedId != null) IconButton(onClick = model::inbox) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to inbox")
                        }
                    },
                    actions = {
                        if (selected != null) IconButton(onClick = { deleteId = selected.id }) {
                            Icon(Icons.Outlined.DeleteOutline, "Delete notification")
                        }
                        IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, "Settings and Firebase") }
                        if (selectedId == null) Box {
                            IconButton(onClick = { overflow = true }) { Icon(Icons.Outlined.MoreVert, "Inbox actions") }
                            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                DropdownMenuItem(text = { Text("Mark all as read") }, enabled = unread > 0,
                                    leadingIcon = { Icon(Icons.Outlined.DoneAll, null) },
                                    onClick = { overflow = false; model.markAllRead() })
                                DropdownMenuItem(text = { Text("Clear inbox") }, enabled = all.isNotEmpty(),
                                    leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                                    onClick = { overflow = false; clearDialog = true })
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                val content = Modifier.widthIn(max = 760.dp).fillMaxSize()
                when {
                    loading -> Box(content, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    loadError -> EmptyState(content, "Couldn't open your inbox", "Your saved notifications have not been changed.", "Try again", model::reload)
                    selectedId != null && selected == null -> EmptyState(content, "Notification unavailable", "This notification may have been deleted.", "Back to inbox", model::inbox)
                    selected != null -> Details(selected, content)
                    else -> LazyColumn(content, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Your inbox", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                    Text(if (unread == 0) "You're all caught up" else "$unread unread ${if (unread == 1) "notification" else "notifications"}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } },
                                        modifier = Modifier.padding(18.dp)) { Icon(Icons.Outlined.Notifications, "Unread notifications: $unread") }
                                }
                            }
                        }
                        if (!notificationsEnabled) item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Keep up with new updates", fontWeight = FontWeight.SemiBold)
                                    Text("Enable system alerts. Delivered messages are still saved here when alerts are off.", style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = enableNotifications) { Text("Enable notifications") }
                                }
                            }
                        }
                        item {
                            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search your notifications") }, label = { Text("Search inbox") },
                                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } },
                                singleLine = true, shape = RoundedCornerShape(18.dp))
                        }
                        item {
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InboxFilter.entries.forEach { choice ->
                                        FilterChip(selected = filter == choice, onClick = { filterName = choice.name },
                                            label = { Text(when (choice) { InboxFilter.ALL -> "All"; InboxFilter.UNREAD -> "Unread"; InboxFilter.READ -> "Read" }) })
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${visible.size} ${if (visible.size == 1) "notification" else "notifications"}",
                                        Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { sortName = (if (sort == InboxSort.NEWEST) InboxSort.OLDEST else InboxSort.NEWEST).name }) {
                                        Icon(Icons.AutoMirrored.Outlined.Sort, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (sort == InboxSort.NEWEST) "Newest first" else "Oldest first")
                                    }
                                }
                            }
                        }
                        if (visible.isEmpty()) item {
                            EmptyState(Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                if (all.isEmpty()) "A fresh start" else "No matching notifications",
                                if (all.isEmpty()) "Your updates will appear here. Everything you receive is kept together, ready when you are." else "Try another filter or search to find what you're looking for.",
                                if (all.isEmpty()) null else "Reset filters",
                                { filterName = InboxFilter.ALL.name; query = "" })
                        }
                        items(visible, key = { it.id }) { item ->
                            NotificationCard(item, now, { model.open(item.id) }, { deleteId = item.id })
                        }
                        item {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text("Saved on this device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, icon = { Icon(Icons.Outlined.DeleteSweep, null) },
            title = { Text("Clear your inbox?") }, text = { Text("All ${all.size} saved notifications will be permanently deleted from this device.") },
            confirmButton = { TextButton(onClick = { clearDialog = false; model.clear() }) { Text("Clear inbox") } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } })
        deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null },
            title = { Text("Delete notification?") }, text = { Text("This notification will be permanently removed from your inbox.") },
            confirmButton = { TextButton(onClick = { deleteId = null; model.delete(id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }) }
        if (settings) AlertDialog(onDismissRequest = { settings = false },
            title = { Text("Settings & connection") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("System notifications", style = MaterialTheme.typography.titleSmall)
                    Text(if (notificationsEnabled) "Alerts are enabled on the Assignment channel." else "Alerts are off. Your inbox remains available.")
                    OutlinedButton(onClick = { notificationSettings() }) { Text("Notification settings") }
                    HorizontalDivider()
                    Text("Firebase Cloud Messaging", style = MaterialTheme.typography.titleSmall)
                    Text(firebaseStatus)
                    token?.let { value ->
                        OutlinedButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FCM device token", value))
                        }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy device token") }
                    }
                    if (model.firebaseConfigured) TextButton(onClick = model::refreshToken) { Text("Refresh connection") }
                    if (BuildConfig.DEBUG) {
                        HorizontalDivider()
                        Text("Developer tools", style = MaterialTheme.typography.titleSmall)
                        Text("Create a local test message to check the inbox and system notification flow. This does not test Firebase delivery.")
                        Button(onClick = { settings = false; model.addLocalTest() }) { Text("Create local test notification") }
                    }
                    Text("FCM Keeper · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
                }
            }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } })
    }
}

@Composable
private fun NotificationCard(item: InboxNotification, now: Long, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (item.isRead) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer)) {
        Banner(item.bannerUrl, Modifier.fillMaxWidth().height(140.dp))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (item.isRead) "READ" else "UNREAD", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = if (item.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(friendlyTime(item.timestamp, now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge,
                fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.description, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("View notification", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete ${item.title}", Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun Banner(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                Text("FCM KEEPER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        if (url == null) fallback() else SubcomposeAsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            contentDescription = "Notification banner", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            loading = { fallback() }, error = { fallback() },
        )
    }
}

@Composable
private fun Details(item: InboxNotification, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Banner(item.bannerUrl, Modifier.fillMaxWidth().height(220.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircleOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Read", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(item.timestamp)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text(item.description, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text("Saved in your inbox", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(modifier: Modifier, title: String, description: String, action: String?, onAction: () -> Unit) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Outlined.Inbox, null, Modifier.padding(24.dp).size(44.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}
internal fun friendlyTime(timestamp: Long, now: Long): String {
    val elapsed = now - timestamp
    return when {
        elapsed < 0 -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
        elapsed < 60_000 -> "Just now"
        elapsed < 3_600_000 -> "${elapsed / 60_000} min ago"
        elapsed < 86_400_000 -> "${elapsed / 3_600_000} hr ago"
        else -> SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}