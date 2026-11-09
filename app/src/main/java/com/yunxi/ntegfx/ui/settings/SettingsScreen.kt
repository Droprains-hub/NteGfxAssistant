package com.yunxi.ntegfx.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunxi.ntegfx.BuildConfig
import com.yunxi.ntegfx.R
import com.yunxi.ntegfx.core.log.Logx
import com.yunxi.ntegfx.core.net.Announcement
import com.yunxi.ntegfx.core.net.UpdateCheck
import com.yunxi.ntegfx.core.net.UpdateInfo
import com.yunxi.ntegfx.core.prefs.AccentColor
import com.yunxi.ntegfx.core.prefs.DarkMode
import com.yunxi.ntegfx.core.prefs.Language
import com.yunxi.ntegfx.core.prefs.ThemeStyle
import com.yunxi.ntegfx.ui.AppViewModel
import com.yunxi.ntegfx.ui.LocalStrings
import com.yunxi.ntegfx.ui.components.AccentSwatch
import com.yunxi.ntegfx.ui.components.DividerThin
import com.yunxi.ntegfx.ui.components.NteCard
import com.yunxi.ntegfx.ui.components.NteChoiceButton
import com.yunxi.ntegfx.ui.components.NteSwitch
import com.yunxi.ntegfx.ui.components.NteToast
import com.yunxi.ntegfx.ui.components.PageTitle
import com.yunxi.ntegfx.ui.components.SectionHeader
import com.yunxi.ntegfx.ui.components.SettingRow
import com.yunxi.ntegfx.ui.theme.LocalNteDimens
import com.yunxi.ntegfx.ui.theme.LocalNteExtra
import com.yunxi.ntegfx.ui.theme.LocalNteStyle
import com.yunxi.ntegfx.ui.theme.Stagger
import com.yunxi.ntegfx.ui.theme.pressScale
import com.yunxi.ntegfx.ui.text.Markdown
import com.yunxi.ntegfx.ui.text.MarkdownText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.delay


private const val URL_SOURCE_CODE = "https://github.com/Droprains-hub/NteGfxAssistant"
private const val URL_EASTER_EGG = "https://ys.mihoyo.com/"

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val settings = vm.settings
    val dimens = LocalNteDimens.current
    val style = LocalNteStyle.current

    var showLicenses by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showAnnouncement by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    var versionClicks by remember { mutableIntStateOf(0) }
    var updateClicks by remember { mutableIntStateOf(0) }
    var lastClickAt by remember { mutableLongStateOf(0L) }

    val scroll = rememberScrollState()

    // 每次进入设置页都重新拉一次公告，不依赖启动那一次
    LaunchedEffect(Unit) { vm.loadAnnouncement() }
    val collapse by remember { derivedStateOf { (scroll.value / 340f).coerceIn(0f, 1f) } }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = exportLogs(context, uri, Logx.listLogFiles())
            toast = if (ok) strings.exportDone else strings.applyFailed
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = dimens.pagePadding),
        ) {
            Spacer(Modifier.height(26.dp))
            PageTitle(strings.tabSettings, collapse = collapse)

            Spacer(Modifier.height(18.dp))
            Stagger(0) { SectionHeader(strings.appearance) }
            Stagger(0) {
                NteCard(contentPadding = 16.dp) {
                    Text(
                        text = strings.themeStyle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NteChoiceButton(
                            label = "MD3",
                            selected = settings.themeStyle == ThemeStyle.MD3,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setThemeStyle(ThemeStyle.MD3) },
                        )
                        NteChoiceButton(
                            label = strings.themeAurora,
                            selected = settings.themeStyle == ThemeStyle.AURORA,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setThemeStyle(ThemeStyle.AURORA) },
                        )
                    }

                    AnimatedVisibility(
                        visible = style != ThemeStyle.MD3,
                        enter = expandVertically(tween(280)) + fadeIn(tween(220)),
                        exit = shrinkVertically(tween(200)) + fadeOut(tween(120)),
                    ) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = strings.accentColor,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AccentSwatch(
                                    color = Color(0xFFA78BFA),
                                    selected = settings.accentColor == AccentColor.DEFAULT ||
                                        settings.accentColor == AccentColor.PURPLE,
                                    label = strings.accentDefault,
                                    onClick = { vm.setAccentColor(AccentColor.DEFAULT) },
                                )
                                AccentSwatch(
                                    color = Color(0xFF3B82F6),
                                    selected = settings.accentColor == AccentColor.BLUE,
                                    label = strings.accentBlue,
                                    onClick = { vm.setAccentColor(AccentColor.BLUE) },
                                )
                                AccentSwatch(
                                    color = Color(0xFF22C55E),
                                    selected = settings.accentColor == AccentColor.GREEN,
                                    label = strings.accentGreen,
                                    onClick = { vm.setAccentColor(AccentColor.GREEN) },
                                )
                                AccentSwatch(
                                    color = Color(0xFFF97316),
                                    selected = settings.accentColor == AccentColor.ORANGE,
                                    label = strings.accentOrange,
                                    onClick = { vm.setAccentColor(AccentColor.ORANGE) },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    DividerThin()
                    Spacer(Modifier.height(14.dp))

                    Text(strings.darkMode, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NteChoiceButton(
                            label = strings.followSystem,
                            selected = settings.darkMode == DarkMode.SYSTEM,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setDarkMode(DarkMode.SYSTEM) },
                        )
                        NteChoiceButton(
                            label = strings.light,
                            selected = settings.darkMode == DarkMode.LIGHT,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setDarkMode(DarkMode.LIGHT) },
                        )
                        NteChoiceButton(
                            label = strings.dark,
                            selected = settings.darkMode == DarkMode.DARK,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setDarkMode(DarkMode.DARK) },
                        )
                    }

                    AnimatedVisibility(
                        visible = settings.themeStyle == ThemeStyle.MD3,
                        enter = expandVertically(tween(240)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
                    ) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(strings.dynamicColor, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        strings.dynamicColorHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                NteSwitch(
                                    checked = settings.dynamicColor,
                                    onCheckedChange = { vm.setDynamicColor(it) },
                                    enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Stagger(1) { SectionHeader(strings.language) }
            Stagger(1) {
                NteCard(contentPadding = 16.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NteChoiceButton(
                            label = strings.followSystem,
                            selected = settings.language == Language.SYSTEM,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setLanguage(Language.SYSTEM) },
                        )
                        NteChoiceButton(
                            label = strings.simplifiedChinese,
                            selected = settings.language == Language.ZH,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setLanguage(Language.ZH) },
                        )
                        NteChoiceButton(
                            label = strings.englishLabel,
                            selected = settings.language == Language.EN,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setLanguage(Language.EN) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Stagger(2) { SectionHeader(strings.announcement) }
            Stagger(2) {
                NteCard(contentPadding = 0.dp) {
                    val announcement = vm.announcement
                    SettingRow(
                        title = when {
                            vm.announcementLoading -> strings.announcementLoading
                            vm.announcementFailed -> strings.announcementFailed
                            announcement != null -> announcement.title
                            else -> strings.announcementEmpty
                        },
                        subtitle = when {
                            vm.announcementFailed -> strings.announcementRetry
                            announcement != null -> Markdown.plain(announcement.body).take(48)
                            else -> null
                        },
                        leading = Icons.Filled.Info,
                        chevron = announcement != null && !vm.announcementLoading,
                        enabled = !vm.announcementLoading,
                        onClick = {
                            if (announcement != null) showAnnouncement = true else vm.loadAnnouncement()
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Stagger(3) { SectionHeader(strings.runningLog) }
            Stagger(3) {
                NteCard(contentPadding = 0.dp) {
                    SettingRow(
                        title = strings.runningLog,
                        subtitle = Logx.currentFileName(),
                        leading = Icons.Filled.Info,
                        chevron = true,
                        onClick = { showLogs = true },
                    )
                    DividerThin(startInset = 16.dp)
                    SettingRow(
                        title = strings.exportLog,
                        leading = Icons.Filled.Send,
                        chevron = true,
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            exportLauncher.launch("nte-gfx-logs-$stamp.zip")
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Stagger(4) { SectionHeader(strings.aboutSupport) }
            Stagger(4) {
                NteCard(contentPadding = 0.dp) {
                    AboutHeader(
                        onClick = {
                            val now = System.currentTimeMillis()
                            versionClicks = if (now - lastClickAt < 1500) versionClicks + 1 else 1
                            lastClickAt = now
                            if (versionClicks >= 3) {
                                versionClicks = 0
                                openUrl(context, URL_EASTER_EGG)
                            }
                        },
                    )
                    DividerThin(startInset = 16.dp)
                    SettingRow(
                        title = strings.guideTitle,
                        leading = Icons.Filled.Info,
                        chevron = true,
                        onClick = { showGuide = true },
                    )
                    DividerThin(startInset = 16.dp)
                    SettingRow(
                        title = strings.sourceCode,
                        leading = Icons.Filled.Share,
                        chevron = true,
                        onClick = { openUrl(context, URL_SOURCE_CODE) },
                    )
                    DividerThin(startInset = 16.dp)
                    SettingRow(
                        title = strings.licenses,
                        leading = Icons.Filled.Warning,
                        chevron = true,
                        onClick = { showLicenses = true },
                    )
                    DividerThin(startInset = 16.dp)
                    SettingRow(
                        title = strings.checkUpdate,
                        subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        leading = Icons.Filled.Refresh,
                        chevron = true,
                        onClick = {
                            val now = System.currentTimeMillis()
                            updateClicks = if (now - lastClickAt < 1500) updateClicks + 1 else 1
                            lastClickAt = now
                            if (updateClicks >= 3) {
                                updateClicks = 0
                                showDebug = true
                            } else {
                                vm.checkUpdate(manual = true)
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        NteToast(
            text = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false }, onOpen = { url -> openUrl(context, url) })
    }

    if (showDebug) {
        DebugDialog(vm, onDismiss = { showDebug = false })
    }

    if (showLogs) {
        LogsDialog(vm, onDismiss = { showLogs = false })
    }

    if (showGuide) {
        GuideDialog(onDismiss = { showGuide = false })
    }

    vm.announcement?.let { announcement ->
        if (showAnnouncement) {
            AnnouncementDialog(
                announcement = announcement,
                onOpen = { url -> openUrl(context, url) },
                onDismiss = { showAnnouncement = false },
            )
        }
    }

    if (vm.updateUpToDate) {
        AlertDialog(
            onDismissRequest = { vm.consumeUpToDate() },
            title = { Text(strings.upToDateTitle) },
            text = { Text(strings.upToDateBody.format(BuildConfig.VERSION_NAME)) },
            confirmButton = {
                TextButton(onClick = { vm.consumeUpToDate() }) { Text(strings.gotIt) }
            },
        )
    }
}

@Composable
private fun AboutHeader(onClick: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                strings.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class LicenseEntry(val name: String, val license: String, val url: String)

@Composable
private fun LicensesDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val strings = LocalStrings.current
    val items = listOf(
        LicenseEntry("Jetpack Compose", "Apache-2.0", "https://github.com/androidx/androidx"),
        LicenseEntry("AndroidX Core / Activity / Lifecycle", "Apache-2.0", "https://github.com/androidx/androidx"),
        LicenseEntry("AndroidX DataStore", "Apache-2.0", "https://github.com/androidx/androidx"),
        LicenseEntry("Kotlin Standard Library", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
        LicenseEntry("KotlinX Coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.licenses) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpen(item.url) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${item.license} · ${item.url}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

@Composable
private fun DebugDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.debugPanel) },
        text = {
            Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    vm.deviceSummary(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

@Composable
private fun LogsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val tail = remember { vm.logTail() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.runningLog) },
        text = {
            Column(
                Modifier
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    tail.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

@Composable
private fun GuideDialog(onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.guideTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                strings.guideSteps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .width(20.dp)
                                .padding(top = 2.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.gotIt) } },
    )
}

/** 公告详情：Markdown 渲染正文，带可选跳转按钮。 */
@Composable
private fun AnnouncementDialog(
    announcement: Announcement,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(announcement.title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MarkdownText(announcement.body)
            }
        },
        confirmButton = {
            val url = announcement.url
            val label = announcement.buttonText
            if (!url.isNullOrBlank() && !label.isNullOrBlank()) {
                TextButton(onClick = { onOpen(url) }) { Text(label) }
            }
            TextButton(onClick = onDismiss) { Text(strings.close) }
        },
    )
}

/** 更新弹窗：Markdown 渲染 changelog，带下载按钮；强制更新时不可关闭。 */
@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = { if (!info.forced) onDismiss() },
        title = { Text("${strings.updateAvailable} · v${info.versionName}") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MarkdownText(info.changelog)
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpen(info.url) }) { Text(strings.updateGoDownload) }
        },
        dismissButton = {
            if (!info.forced) {
                TextButton(onClick = onDismiss) { Text(strings.updateLater) }
            }
        },
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun exportLogs(context: Context, uri: Uri, files: List<File>): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        ZipOutputStream(output).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    } != null
}.getOrDefault(false)

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return "-"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
