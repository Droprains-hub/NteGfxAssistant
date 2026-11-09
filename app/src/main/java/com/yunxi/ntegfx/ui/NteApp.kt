package com.yunxi.ntegfx.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunxi.ntegfx.BuildConfig
import com.yunxi.ntegfx.core.prefs.Language
import com.yunxi.ntegfx.core.prefs.ThemeStyle
import com.yunxi.ntegfx.ui.config.ConfigScreen
import com.yunxi.ntegfx.ui.home.HomeScreen
import com.yunxi.ntegfx.ui.onboarding.OnboardingScreen
import com.yunxi.ntegfx.ui.settings.SettingsScreen
import com.yunxi.ntegfx.ui.text.UiStrings
import com.yunxi.ntegfx.ui.theme.AuroraBackdrop
import com.yunxi.ntegfx.ui.theme.BackdropStyle
import com.yunxi.ntegfx.ui.theme.LocalNteFeatures
import com.yunxi.ntegfx.ui.theme.LocalNteStyle
import com.yunxi.ntegfx.ui.theme.NteAnimatedDialog
import com.yunxi.ntegfx.ui.theme.NteTheme
import com.yunxi.ntegfx.ui.theme.resolveDarkMode
import kotlinx.coroutines.delay
import java.util.Locale
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.yunxi.ntegfx.core.net.UpdateCheck
import com.yunxi.ntegfx.ui.text.MarkdownText
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh

val LocalStrings = staticCompositionLocalOf { UiStrings(false) }

enum class Tab { HOME, CONFIG, SETTINGS }

@Composable
fun buildStrings(language: Language): UiStrings {
    val configuration = LocalConfiguration.current
    val english = when (language) {
        Language.EN -> true
        Language.ZH -> false
        Language.SYSTEM -> !Locale.getDefault().language.startsWith("zh")
    }
    return remember(language, configuration.locales) { UiStrings(english) }
}

@Composable
fun NteApp(vm: AppViewModel = viewModel()) {
    val settings = vm.settings
    val dark = resolveDarkMode(settings.darkMode)
    val strings = buildStrings(settings.language)
    val context = LocalContext.current
    val skipOnboarding = BuildConfig.DEBUG &&
        (context.findActivity()?.intent?.getBooleanExtra("skip_onboarding", false) == true)

    NteTheme(
        style = settings.themeStyle,
        dark = dark,
        dynamicColor = settings.dynamicColor,
        accent = settings.accentColor,
    ) {
        CompositionLocalProvider(LocalStrings provides strings) {
            val features = LocalNteFeatures.current
            val aurora = features.backdrop == BackdropStyle.AURORA
            SystemBarTone(lightIcons = dark)

            // 冷启动淡入 + 切换主题时的一次轻微回弹，让换肤动作有「落地感」
            var entered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(40)
                entered = true
            }
            var pulse by remember { mutableStateOf(false) }
            var lastStyle by remember { mutableStateOf(settings.themeStyle) }
            LaunchedEffect(settings.themeStyle) {
                if (settings.themeStyle != lastStyle) {
                    lastStyle = settings.themeStyle
                    pulse = true
                    delay(260)
                    pulse = false
                }
            }
            val enterAlpha by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(340),
                label = "appEnterAlpha",
            )
            val enterScale by animateFloatAsState(
                targetValue = if (entered) 1f else 0.985f,
                animationSpec = tween(460),
                label = "appEnterScale",
            )
            val pulseScale by animateFloatAsState(
                targetValue = if (pulse) 0.986f else 1f,
                animationSpec = spring(dampingRatio = 0.52f, stiffness = 540f),
                label = "themePulse",
            )

            Box(Modifier.fillMaxSize()) {
                if (aurora) {
                    AuroraBackdrop(dark = dark, modifier = Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = enterAlpha
                            val s = enterScale * pulseScale
                            scaleX = s
                            scaleY = s
                        }
                        .background(if (aurora) Color.Transparent else MaterialTheme.colorScheme.background),
                ) {
                    if (!settings.onboardingDone && !skipOnboarding) {
                        OnboardingScreen(vm)
                    } else {
                        MainScaffold(vm)
                    }
                }
                if (settings.onboardingDone || skipOnboarding) {
                    GlobalPrompts(vm)
                }
            }
        }
    }
}

@Composable
private fun GlobalPrompts(vm: AppViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val update = vm.updateState

    // 更新优先于公告：两者同时到达只弹一个，避免叠层
    if (update is UpdateCheck.Available) {
        val info = update.info
        PromoDialog(
            icon = Icons.Filled.Refresh,
            title = strings.updateAvailable,
            subtitle = "v${info.versionName}",
            primaryText = strings.updateGoDownload,
            onPrimary = { launchUrl(context, info.url) },
            secondaryText = if (info.forced) null else strings.updateLater,
            onSecondary = if (info.forced) null else ({ vm.consumeUpdate() }),
            dismissible = !info.forced,
            onDismiss = { vm.consumeUpdate() },
        ) {
            MarkdownText(info.changelog)
        }
        return
    }

    val notice = vm.announcement
    val unseen = notice != null && notice.id != vm.seenAnnouncementId

    // 冷启动时首页还在铺首帧，公告立刻盖上来会显得很冲；
    // 稍等一拍再淡入，视觉上有先后顺序。
    var ready by remember(notice?.id, vm.seenAnnouncementId) { mutableStateOf(false) }
    LaunchedEffect(notice?.id, vm.seenAnnouncementId) {
        if (notice != null && notice.id != vm.seenAnnouncementId) {
            kotlinx.coroutines.delay(450)
            ready = true
        } else {
            ready = false
        }
    }

    if (unseen && ready && notice != null) {
        run {
            val close = { vm.markAnnouncementSeen(notice.id) }
            PromoDialog(
                icon = Icons.Filled.Info,
                title = notice.title,
                subtitle = null,
                primaryText = notice.buttonText ?: strings.gotIt,
                onPrimary = {
                    vm.markAnnouncementSeen(notice.id)
                    notice.url?.let { launchUrl(context, it) }
                },
                secondaryText = if (notice.forced) null else strings.gotIt,
                onSecondary = if (notice.forced) null else close,
                dismissible = !notice.forced,
                onDismiss = close,
            ) {
                MarkdownText(notice.body)
            }
        }
    }
}

/**
 * 推送类弹窗的统一样式：圆角卡片 + 圆形图标 + 标题区 + 可滚动正文 + 主次按钮。
 * 替代 Material 默认 AlertDialog，避免长标题挤压正文、按钮散落两角。
 * 入场/退场动画由 [NteAnimatedDialog] 统一驱动，所有关闭路径先播退场再执行动作。
 */
@Composable
private fun PromoDialog(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    primaryText: String,
    onPrimary: () -> Unit,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    NteAnimatedDialog(onDismiss = onDismiss, dismissible = dismissible) { close ->
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) { body() }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (secondaryText != null && onSecondary != null) {
                        TextButton(
                            onClick = { close(onSecondary) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(secondaryText, fontSize = 14.sp) }
                    }
                    Button(
                        onClick = { close(onPrimary) },
                        modifier = Modifier
                            .weight(1.35f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(primaryText, fontSize = 14.sp) }
                }
            }
        }
    }
}

private fun launchUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel) {
    val strings = LocalStrings.current
    val features = LocalNteFeatures.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    var pendingTab by remember { mutableStateOf<Tab?>(null) }
    val snackbar = remember { SnackbarHostState() }

    fun switchTo(target: Tab) {
        if (target == tab) return
        if (tab == Tab.CONFIG && vm.dirtyCount > 0) {
            pendingTab = target
        } else {
            tab = target
        }
    }

    BackHandler(enabled = tab != Tab.HOME) { switchTo(Tab.HOME) }

    LaunchedEffect(vm.message) {
        val msg = vm.message
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = if (features.backdrop == BackdropStyle.AURORA) Color.Transparent else MaterialTheme.colorScheme.background,
        bottomBar = { NteBottomBar(current = tab, onSelect = { switchTo(it) }) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction = if (forward) 1 else -1
                    (
                        fadeIn(tween(260)) +
                            slideInHorizontally(tween(340)) { it / 10 * direction } +
                            scaleIn(tween(340), initialScale = 0.975f)
                        ).togetherWith(
                        fadeOut(tween(150)) +
                            slideOutHorizontally(tween(220)) { -it / 14 * direction },
                    )
                },
                label = "tab",
            ) { current ->
                when (current) {
                    Tab.HOME -> HomeScreen(vm, onOpenConfig = { switchTo(Tab.CONFIG) })
                    Tab.CONFIG -> ConfigScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }

    val pending = pendingTab
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingTab = null },
            title = { Text(strings.unsavedTitle) },
            text = { Text(strings.undoChangesTitle.format(vm.dirtyCount)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetEditing()
                    tab = pending
                    pendingTab = null
                }) { Text(strings.discard) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTab = null }) { Text(strings.keepEditing) }
            },
        )
    }
}

@Composable
private fun NteBottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val strings = LocalStrings.current
    val style = LocalNteStyle.current
    val features = LocalNteFeatures.current
    val items = listOf(
        Triple(Tab.HOME, strings.tabHome, Icons.Filled.Home),
        Triple(Tab.CONFIG, strings.tabConfig, Icons.Filled.Build),
        Triple(Tab.SETTINGS, strings.tabSettings, Icons.Filled.Settings),
    )

    if (style == ThemeStyle.MD3) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
        ) {
            items.forEach { (target, label, icon) ->
                val selected = current == target
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(target) },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
        return
    }

    val aurora = style == ThemeStyle.AURORA
    val barShape = RoundedCornerShape(if (aurora) 26.dp else 0.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = if (aurora) 16.dp else 0.dp)
            .padding(bottom = if (aurora) 12.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(barShape)
                .background(
                    if (aurora) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                )
                .then(
                    if (aurora) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            ),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (features.hairlineNav) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (aurora) 62.dp else 58.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                items.forEach { (target, label, icon) ->
                    NteTabItem(
                        label = label,
                        icon = icon,
                        selected = current == target,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(target) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NteTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val style = LocalNteStyle.current
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "tabTint",
    )
    val indicator by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 620f),
        label = "tabIndicator",
    )
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 700f),
        label = "tabIconScale",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = ripple(bounded = false), onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 26.dp)
                    .graphicsLayer {
                        alpha = indicator
                        scaleX = 0.72f + 0.28f * indicator
                        scaleY = 0.72f + 0.28f * indicator
                    }
                    .then(
                        if (style == ThemeStyle.AURORA) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.26f),
                                    ),
                                ),
                                RoundedCornerShape(13.dp),
                            )
                        } else {
                            Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(13.dp),
                            )
                        },
                    ),
            )
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(text = label, fontSize = 10.sp, color = tint)
    }
}

@Composable
private fun SystemBarTone(lightIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !lightIcons
            controller.isAppearanceLightNavigationBars = !lightIcons
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
