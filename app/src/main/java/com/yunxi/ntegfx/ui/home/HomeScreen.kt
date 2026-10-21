package com.yunxi.ntegfx.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunxi.ntegfx.core.device.DeviceInfo
import com.yunxi.ntegfx.core.prefs.ThemeStyle
import com.yunxi.ntegfx.data.ConfigState
import com.yunxi.ntegfx.ui.AppViewModel
import com.yunxi.ntegfx.ui.LocalStrings
import com.yunxi.ntegfx.ui.components.DividerThin
import com.yunxi.ntegfx.ui.components.KeyValueRow
import com.yunxi.ntegfx.ui.components.NteCard
import com.yunxi.ntegfx.ui.components.PageTitle
import com.yunxi.ntegfx.ui.components.ProgressBarThin
import com.yunxi.ntegfx.ui.components.SectionHeader
import com.yunxi.ntegfx.ui.components.SettingRow
import com.yunxi.ntegfx.ui.theme.LocalNteDimens
import com.yunxi.ntegfx.ui.theme.LocalNteExtra
import com.yunxi.ntegfx.ui.theme.LocalNteStyle
import com.yunxi.ntegfx.ui.theme.Stagger
import com.yunxi.ntegfx.ui.theme.rememberCountUp
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel, onOpenConfig: () -> Unit) {
    val strings = LocalStrings.current
    val extra = LocalNteExtra.current
    val dimens = LocalNteDimens.current
    val context = LocalContext.current
    val root = vm.root
    var showGuide by remember { mutableStateOf(false) }

    val memory = remember { DeviceInfo.memory(context) }
    val scroll = rememberScrollState()
    val collapse by remember {
        derivedStateOf { (scroll.value / 340f).coerceIn(0f, 1f) }
    }

    val memoryPercent = remember(memory) {
        if (memory.second <= 0L) 0 else ((memory.first * 100) / memory.second).toInt()
    }
    val animatedPercent = rememberCountUp(memoryPercent)
    val configState = vm.snapshot?.state
    val configLabel = when {
        vm.snapshot?.installed == false -> strings.notInstalled
        configState == ConfigState.APPLIED -> strings.stateApplied
        configState == ConfigState.UNKNOWN -> strings.stateUnknown
        else -> strings.initTitle
    }
    val configColor = when {
        configState == ConfigState.APPLIED -> extra.success
        configState == ConfigState.UNKNOWN -> extra.danger
        else -> extra.warning
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = dimens.pagePadding),
    ) {
        Spacer(Modifier.height(26.dp))
        PageTitle(strings.appName, collapse = collapse)

        Spacer(Modifier.height(18.dp))

        // 授权状态卡：整卡可点击重新检测
        Stagger(0) {
            NteCard(onClick = { vm.refresh(requestRoot = true) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusMark(granted = root.granted, accent = if (root.granted) extra.success else extra.danger)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = root.granted,
                            transitionSpec = {
                                (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.94f))
                                    .togetherWith(fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.98f))
                            },
                            label = "rootState",
                        ) { granted ->
                            Text(
                                text = if (granted) {
                                    strings.rootGrantedWith(root.manager.displayName)
                                } else {
                                    strings.rootNotGranted
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (root.granted) strings.tapToRecheck else strings.rootInstalledNotGranted(root.manager.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                KeyValueRow(strings.rootManagerLabel, "${root.manager.displayName} ${root.version.orEmpty()}".trim())
                KeyValueRow(strings.selinuxLabel, root.selinux ?: "-")
                KeyValueRow(strings.kernelLabel, DeviceInfo.kernel())
                KeyValueRow(strings.abiLabel, DeviceInfo.abi())
            }
        }

        Spacer(Modifier.height(dimens.cardGap))

        Stagger(1) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.cardGap)) {
                StatCard(
                    label = strings.configStatus,
                    value = configLabel,
                    valueColor = configColor,
                    hint = strings.channelName(vm.channel),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = strings.memoryLabel,
                    value = "$animatedPercent%",
                    hint = DeviceInfo.formatBytes(memory.first),
                    progress = animatedPercent / 100f,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Stagger(2) { SectionHeader(strings.deviceAndGame) }
        Stagger(2) {
            NteCard(contentPadding = 4.dp) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    KeyValueRow(strings.processorLabel, DeviceInfo.processor())
                    KeyValueRow(
                        strings.memoryLabel,
                        "${DeviceInfo.formatBytes(memory.first)} / ${DeviceInfo.formatBytes(memory.second)}",
                    )
                    KeyValueRow(strings.screenLabel, DeviceInfo.display(context))
                    KeyValueRow(strings.androidVersion, DeviceInfo.androidVersion())
                    KeyValueRow(strings.deviceModel, DeviceInfo.manufacturerModel())
                    DividerThin(Modifier.padding(vertical = 4.dp))
                    KeyValueRow(strings.currentChannel, strings.channelName(vm.channel))
                    KeyValueRow(strings.gameVersion, vm.snapshot?.gameVersion ?: "-")
                }
            }
        }

        if (vm.root.granted && vm.snapshot?.state == ConfigState.NOT_INITIALIZED) {
            Spacer(Modifier.height(dimens.cardGap))
            Stagger(3) {
                NteCard(onClick = onOpenConfig) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = extra.warning,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(strings.initTitle, style = MaterialTheme.typography.titleSmall)
                            Text(
                                strings.initHintHome,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Stagger(4) {
            NteCard(contentPadding = 0.dp) {
                SettingRow(
                    title = strings.userGuide,
                    leading = Icons.Filled.Info,
                    chevron = true,
                    onClick = { showGuide = true },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text(strings.guideTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    strings.guideSteps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "${index + 1}",
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
            confirmButton = {
                TextButton(onClick = { showGuide = false }) { Text(strings.gotIt) }
            },
        )
    }
}

/** 授权状态圆标：未授权时外圈持续呼吸，授权后对勾弹入。 */
@Composable
private fun StatusMark(granted: Boolean, accent: Color) {
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center) {
        if (!granted) {
            Canvas(Modifier.size(46.dp)) {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = accent.copy(alpha = 0.35f - 0.2f * pulse),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = accent.copy(alpha = 0.12f + 0.10f * pulse),
                    radius = size.minDimension / 2f - 6.dp.toPx(),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = granted,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(320), initialScale = 0.5f))
                        .togetherWith(fadeOut(tween(120)) + scaleOut(tween(180), targetScale = 0.5f))
                },
                label = "statusIcon",
            ) { ok ->
                Icon(
                    imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

/** 小号统计卡：标签 + 大数字 + 可选进度条。 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    hint: String? = null,
    progress: Float? = null,
    modifier: Modifier = Modifier,
) {
    NteCard(modifier = modifier, contentPadding = 16.dp) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            maxLines = 1,
        )
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            ProgressBarThin(progress)
        } else if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
