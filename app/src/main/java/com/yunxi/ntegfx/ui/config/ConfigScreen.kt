package com.yunxi.ntegfx.ui.config

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunxi.ntegfx.core.root.AnfPatcher
import com.yunxi.ntegfx.data.AaMethod
import com.yunxi.ntegfx.data.ApplyStage
import com.yunxi.ntegfx.data.Channel
import com.yunxi.ntegfx.data.ConfigState
import com.yunxi.ntegfx.data.ParamKey
import com.yunxi.ntegfx.data.ParamSpec
import com.yunxi.ntegfx.data.ScalabilityGroup
import com.yunxi.ntegfx.data.TonePreset
import com.yunxi.ntegfx.ui.AppViewModel
import com.yunxi.ntegfx.ui.LocalStrings
import com.yunxi.ntegfx.ui.components.DividerThin
import com.yunxi.ntegfx.ui.components.NteCard
import com.yunxi.ntegfx.ui.components.NteChoiceButton
import com.yunxi.ntegfx.ui.components.NteSwitch
import com.yunxi.ntegfx.ui.components.NteToast
import com.yunxi.ntegfx.ui.components.PageTitle
import com.yunxi.ntegfx.ui.components.PrimaryActionButton
import com.yunxi.ntegfx.ui.components.ProgressBarThin
import com.yunxi.ntegfx.ui.components.SectionHeader
import com.yunxi.ntegfx.ui.components.StatusPill
import com.yunxi.ntegfx.ui.theme.LocalNteDimens
import com.yunxi.ntegfx.ui.theme.LocalNteExtra
import com.yunxi.ntegfx.ui.theme.LocalNteStyle
import com.yunxi.ntegfx.ui.theme.NumericStyle
import com.yunxi.ntegfx.ui.theme.Stagger
import com.yunxi.ntegfx.ui.theme.pressScale
import com.yunxi.ntegfx.ui.theme.rotationFor
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(vm: AppViewModel) {
    val strings = LocalStrings.current
    val extra = LocalNteExtra.current
    val dimens = LocalNteDimens.current
    val snapshot = vm.snapshot
    val canEdit = vm.canEdit
    val params = vm.editing
    val installed = snapshot?.installed == true
    val initialized = snapshot?.state == ConfigState.APPLIED

    val anfState = vm.anfState
    val anfPatched = anfState?.state == AnfPatcher.State.PATCHED
    val anfPatchUsable = anfState?.usable == true
    val anfPatchSuffix = when {
        anfState == null -> ""
        anfState.state == AnfPatcher.State.MISSING -> " · " + strings.anfPatchMissing
        anfState.state == AnfPatcher.State.UNSUPPORTED_BUILD -> " · " + strings.anfPatchUnsupported
        anfState.state == AnfPatcher.State.UNKNOWN -> " · " + strings.anfPatchUnknown
        else -> ""
    }

    var expanded by remember { mutableStateOf<ParamKey?>(null) }
    var expandedSg by remember { mutableStateOf<ScalabilityGroup?>(null) }
    var helpKey by remember { mutableStateOf<ParamKey?>(null) }
    var showServerSheet by remember { mutableStateOf(false) }
    var showServerHelp by remember { mutableStateOf(false) }
    var sheetMessage by remember { mutableStateOf<String?>(null) }
    var showApplyDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    val collapse by remember { derivedStateOf { (scroll.value / 340f).coerceIn(0f, 1f) } }

    val configStateText = when {
        !installed -> strings.notInstalled
        snapshot?.state == ConfigState.APPLIED -> strings.stateApplied
        snapshot?.state == ConfigState.UNKNOWN -> strings.stateUnknown
        else -> strings.initTitle
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1600)
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
            PageTitle(strings.configTitle, collapse = collapse)
            Spacer(Modifier.height(16.dp))

            Stagger(0) {
                NteCard(onClick = { showServerSheet = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(if (LocalNteStyle.current == com.yunxi.ntegfx.core.prefs.ThemeStyle.AURORA) {
                                    RoundedCornerShape(12.dp)
                                } else {
                                    CircleShape
                                })
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = vm.channel.shortName.take(1),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(strings.channelName(vm.channel), style = MaterialTheme.typography.titleMedium)
                            Text(
                                vm.channel.pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        StatusPill(
                            text = if (installed) strings.installed else strings.notInstalled,
                            color = if (installed) extra.success else extra.neutral,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = strings.serverPicker,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = configStateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (snapshot?.state) {
                            ConfigState.APPLIED -> extra.success
                            ConfigState.UNKNOWN -> extra.danger
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (!initialized && installed && vm.root.granted) {
                Spacer(Modifier.height(dimens.cardGap))
                Stagger(1) {
                    NteCard {
                        Text(
                            strings.initTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            strings.initBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.initializeConfig() },
                            enabled = !vm.applying,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(strings.initAction) }
                    }
                }
            } else if (!canEdit) {
                Spacer(Modifier.height(dimens.cardGap))
                Stagger(1) {
                    NteCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = when {
                                !vm.root.granted -> strings.stateNoRoot
                                !installed -> strings.emptyHint
                                snapshot?.state == ConfigState.UNKNOWN -> strings.stateUnknown
                                else -> strings.initRequired
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (initialized) {
                Spacer(Modifier.height(20.dp))
                Stagger(2) { SectionHeader(strings.groupPerformance) }
                Stagger(2) {
                    NteCard(contentPadding = 0.dp) {
                        ParamRow(
                            title = strings.frameRate,
                            value = "${params.frameRateLimit}",
                            enabled = canEdit,
                            expanded = expanded == ParamKey.FRAME_RATE,
                            onToggle = { expanded = if (expanded == ParamKey.FRAME_RATE) null else ParamKey.FRAME_RATE },
                            onHelp = { helpKey = ParamKey.FRAME_RATE },
                        ) {
                            FrameRateEditor(
                                current = params.frameRateLimit,
                                enabled = canEdit,
                                onPick = { value -> vm.updateParams { it.copy(frameRateLimit = value) } },
                            )
                        }
                        DividerThin(startInset = 16.dp)
                        SwitchRow(
                            title = strings.vsyncNote,
                            checked = params.useVSync,
                            enabled = canEdit,
                            onCheckedChange = { value -> vm.updateParams { it.copy(useVSync = value) } },
                            onHelp = { helpKey = ParamKey.VSYNC },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Stagger(3) { SectionHeader(strings.groupGraphics) }
                Stagger(3) {
                    NteCard(contentPadding = 0.dp) {
                        ParamRow(
                            title = strings.renderResolution,
                            value = ParamSpec.scaleLabel(params.renderScale),
                            enabled = canEdit,
                            expanded = expanded == ParamKey.RENDER_SCALE,
                            onToggle = { expanded = if (expanded == ParamKey.RENDER_SCALE) null else ParamKey.RENDER_SCALE },
                            onHelp = { helpKey = ParamKey.RENDER_SCALE },
                        ) {
                            ScaleEditor(
                                current = params.renderScale,
                                enabled = canEdit,
                                onChange = { value -> vm.updateParams { it.copy(renderScale = value) } },
                            )
                        }
                        DividerThin(startInset = 16.dp)
                        SwitchRow(
                            title = strings.frameGeneration,
                            checked = params.frameGeneration,
                            enabled = canEdit,
                            onCheckedChange = { value -> vm.updateParams { it.copy(frameGeneration = value) } },
                            onHelp = { helpKey = ParamKey.FRAME_GENERATION },
                        )
                        DividerThin(startInset = 16.dp)
                        // ANF 补丁总开关：补丁没打，游戏里连 ANF 两个选项都不会出现，
                        // 所以要放在 ANF 设置上方。
                        SwitchRow(
                            title = strings.anfPatch + anfPatchSuffix,
                            checked = anfPatched,
                            enabled = canEdit && anfPatchUsable && !vm.applying,
                            onCheckedChange = { vm.setAnfPatch(it) },
                            onHelp = null,
                        )
                        // 测试功能提示：紧贴补丁开关下方的小红字
                        Text(
                            text = strings.anfPatchNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.danger,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        )
                        DividerThin(startInset = 16.dp)
                        SwitchRow(
                            title = strings.anfSr,
                            checked = params.anfSr,
                            enabled = canEdit,
                            onCheckedChange = { value ->
                                if (!anfPatched) vm.showMessage(strings.anfPatchRequired)
                                else vm.updateParams { it.copy(anfSr = value) }
                            },
                            onHelp = null,
                        )
                        DividerThin(startInset = 16.dp)
                        SwitchRow(
                            title = strings.anfFrc,
                            checked = params.anfFrc,
                            enabled = canEdit,
                            onCheckedChange = { value ->
                                if (!anfPatched) vm.showMessage(strings.anfPatchRequired)
                                else vm.updateParams { it.copy(anfFrc = value) }
                            },
                            onHelp = null,
                        )
                        DividerThin(startInset = 16.dp)
                        ParamRow(
                            title = strings.sharpen,
                            value = String.format(java.util.Locale.US, "%.2f", params.sharpen),
                            enabled = canEdit,
                            expanded = expanded == ParamKey.SHARPEN,
                            onToggle = { expanded = if (expanded == ParamKey.SHARPEN) null else ParamKey.SHARPEN },
                            onHelp = { helpKey = ParamKey.SHARPEN },
                        ) {
                            FloatEditor(
                                value = params.sharpen,
                                range = ParamSpec.SHARPEN_MIN..ParamSpec.SHARPEN_MAX,
                                steps = 15,
                                enabled = canEdit,
                                onChange = { value -> vm.updateParams { it.copy(sharpen = value) } },
                            )
                        }
                        DividerThin(startInset = 16.dp)
                        ParamRow(
                            title = strings.tonePreset,
                            value = strings.tonePresetName(TonePreset.currentOf(params)),
                            enabled = canEdit,
                            expanded = expanded == ParamKey.TONE_PRESET,
                            onToggle = { expanded = if (expanded == ParamKey.TONE_PRESET) null else ParamKey.TONE_PRESET },
                            onHelp = { helpKey = ParamKey.TONE_PRESET },
                        ) {
                            OptionList(
                                options = TonePreset.entries.map { strings.tonePresetName(it) },
                                selected = TonePreset.entries.indexOf(TonePreset.currentOf(params)),
                                enabled = canEdit,
                                onSelect = { index ->
                                    val preset = TonePreset.entries[index]
                                    vm.updateParams {
                                        it.copy(
                                            colorMin = preset.colorMin,
                                            colorMid = preset.colorMid,
                                            colorMax = preset.colorMax,
                                        )
                                    }
                                },
                            )
                        }
                        DividerThin(startInset = 16.dp)
                        ParamRow(
                            title = strings.colorMin,
                            value = String.format(java.util.Locale.US, "%.2f", params.colorMin),
                            enabled = canEdit,
                            expanded = expanded == ParamKey.COLOR_MIN,
                            onToggle = { expanded = if (expanded == ParamKey.COLOR_MIN) null else ParamKey.COLOR_MIN },
                            onHelp = { helpKey = ParamKey.COLOR_MIN },
                        ) {
                            FloatEditor(
                                value = params.colorMin,
                                range = ParamSpec.COLOR_MIN_MIN..ParamSpec.COLOR_MIN_MAX,
                                steps = 39,
                                enabled = canEdit,
                                onChange = { value -> vm.updateParams { it.copy(colorMin = value) } },
                            )
                        }
                        DividerThin(startInset = 16.dp)
                        ParamRow(
                            title = strings.colorMid,
                            value = String.format(java.util.Locale.US, "%.2f", params.colorMid),
                            enabled = canEdit,
                            expanded = expanded == ParamKey.COLOR_MID,
                            onToggle = { expanded = if (expanded == ParamKey.COLOR_MID) null else ParamKey.COLOR_MID },
                            onHelp = { helpKey = ParamKey.COLOR_MID },
                        ) {
                            FloatEditor(
                                value = params.colorMid,
                                range = ParamSpec.COLOR_MID_MIN..ParamSpec.COLOR_MID_MAX,
                                steps = 43,
                                enabled = canEdit,
                                onChange = { value -> vm.updateParams { it.copy(colorMid = value) } },
                            )
                        }
                        DividerThin(startInset = 16.dp)
                        ParamRow(
                            title = strings.colorHigh,
                            value = String.format(java.util.Locale.US, "%.2f", params.colorMax),
                            enabled = canEdit,
                            expanded = expanded == ParamKey.COLOR_MAX,
                            onToggle = { expanded = if (expanded == ParamKey.COLOR_MAX) null else ParamKey.COLOR_MAX },
                            onHelp = { helpKey = ParamKey.COLOR_MAX },
                        ) {
                            FloatEditor(
                                value = params.colorMax,
                                range = ParamSpec.COLOR_MAX_MIN..ParamSpec.COLOR_MAX_MAX,
                                steps = 83,
                                enabled = canEdit,
                                onChange = { value -> vm.updateParams { it.copy(colorMax = value) } },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Stagger(4) { SectionHeader(strings.groupGameSettings) }
                NteCard {
                    ParamRow(
                        title = strings.qualityPreset,
                        value = strings.qualityPresetName(params.qualityPreset),
                        enabled = canEdit,
                        expanded = expanded == ParamKey.QUALITY_PRESET,
                        onToggle = {
                            expanded = if (expanded == ParamKey.QUALITY_PRESET) null else ParamKey.QUALITY_PRESET
                        },
                        onHelp = { helpKey = ParamKey.QUALITY_PRESET },
                    ) {
                        OptionList(
                            options = listOf(strings.customQuality) +
                                (0..4).map { strings.qualityPresetName(it) },
                            selected = if (params.qualityPreset < 0) 0 else params.qualityPreset + 1,
                            enabled = canEdit,
                            onSelect = { index ->
                                val level = if (index == 0) -1 else index - 1
                                vm.updateParams { it.copy(qualityPreset = level) }
                            },
                        )
                    }
                    // 自定义档位：逐项暴露画质分组（只列游戏界面里有入口的，
                    // 反射/阴影/GI 等无 UI 项先隐藏，等实测确认后再放开）
                    if (params.qualityPreset < 0) {
                        ScalabilityGroup.entries.filter { it.hasGameUi }.forEach { group ->
                            DividerThin(startInset = 16.dp)
                            ParamRow(
                                title = strings.sgGroupName(group),
                                value = strings.sgLevelName(group.of(params)),
                                enabled = canEdit,
                                expanded = expandedSg == group,
                                onToggle = { expandedSg = if (expandedSg == group) null else group },
                                onHelp = { expandedSg = group },
                            ) {
                                OptionList(
                                    options = (0..4).map { strings.sgLevelName(it) },
                                    selected = group.of(params),
                                    enabled = canEdit,
                                    onSelect = { level ->
                                        vm.updateParams { current -> group.apply(current, level) }
                                    },
                                )
                            }
                        }
                    }
                    DividerThin(startInset = 16.dp)
                    ParamRow(
                        title = strings.npcQuality,
                        value = strings.npcQualityName(params.npcQuality),
                        enabled = canEdit,
                        expanded = expanded == ParamKey.NPC_QUALITY,
                        onToggle = {
                            expanded = if (expanded == ParamKey.NPC_QUALITY) null else ParamKey.NPC_QUALITY
                        },
                        onHelp = null,
                    ) {
                        OptionList(
                            options = (0..4).map { strings.npcQualityName(it) },
                            selected = params.npcQuality,
                            enabled = canEdit,
                            onSelect = { level -> vm.updateParams { it.copy(npcQuality = level) } },
                        )
                    }
                    DividerThin(startInset = 16.dp)
                    ParamRow(
                        title = strings.antiAliasing,
                        value = params.antiAliasing.iniValue,
                        enabled = canEdit,
                        expanded = expanded == ParamKey.AA_METHOD,
                        onToggle = { expanded = if (expanded == ParamKey.AA_METHOD) null else ParamKey.AA_METHOD },
                        onHelp = { helpKey = ParamKey.AA_METHOD },
                    ) {
                        OptionList(
                            options = AaMethod.entries.map { it.iniValue },
                            selected = params.antiAliasing.ordinal,
                            enabled = canEdit,
                            onSelect = { index ->
                                vm.updateParams { it.copy(antiAliasing = AaMethod.entries[index]) }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { vm.initializeConfig() },
                    enabled = canEdit && !vm.applying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.rewriteConfig, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(
                    onClick = { showResetDialog = true },
                    enabled = canEdit && !vm.applying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.restoreDefaults, color = extra.danger)
                }
            }

            Spacer(Modifier.height(130.dp))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                .padding(horizontal = dimens.pagePadding, vertical = 12.dp),
        ) {
            // 初始化按钮与应用/启动双按钮之间平滑交接，不再瞬切
            AnimatedContent(
                targetState = initialized,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 3 })
                        .togetherWith(fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 4 })
                },
                label = "bottomAction",
            ) { isInitialized ->
            if (!isInitialized) {
                PrimaryActionButton(
                    text = strings.initAction,
                    enabled = installed && vm.root.granted,
                    loading = vm.applying,
                    onClick = { vm.initializeConfig() },
                )
            } else {
                Column {
                AnimatedVisibility(
                    visible = vm.dirtyCount > 0,
                    enter = fadeIn(tween(200)) + expandVertically(tween(240)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
                ) {
                    Text(
                        text = strings.undoChangesTitle.format(vm.dirtyCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            if (vm.settings.skipCountdown) vm.applyConfig() else showApplyDialog = true
                        },
                        enabled = canEdit && vm.dirtyCount > 0 && !vm.applying,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        // 转圈与文字交叉淡换，宽度稳定不跳动
                        AnimatedContent(
                            targetState = vm.applying,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "applyBtnContent",
                        ) { busy ->
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(strings.applyChanges, fontSize = 15.sp)
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { vm.launchGame() },
                        enabled = vm.canLaunchGame && !vm.applying,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = strings.launchGame,
                            fontSize = 15.sp,
                            color = if (vm.canLaunchGame) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                }
                }
            }
            }
        }

        NteToast(
            text = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }

    if (showServerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showServerSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strings.serverPicker,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    HelpDot { showServerHelp = true }
                }
                Spacer(Modifier.height(12.dp))
                val installedMap = remember { Channel.entries.associateWith { vm.isInstalled(it) } }
                if (sheetMessage != null) {
                    Text(
                        text = sheetMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.danger,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Channel.entries.forEachIndexed { index, channel ->
                    val channelInstalled = installedMap[channel] == true
                    // 底栏列表逐项上浮入场，避免整列同时砸下来
                    Stagger(index) {
                        ServerRow(
                            channel = channel,
                            installed = channelInstalled,
                            selected = vm.channel == channel,
                            stateLabel = when {
                                !channelInstalled -> strings.notInstalled
                                vm.channel == channel -> configStateText
                                else -> strings.installed
                            },
                            onSelect = {
                                if (!channelInstalled) {
                                    sheetMessage = strings.notInstalledCannotSelect
                                } else {
                                    sheetMessage = null
                                    vm.selectChannel(channel)
                                    showServerSheet = false
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showServerHelp) {
        AlertDialog(
            onDismissRequest = { showServerHelp = false },
            title = { Text(strings.serverPicker) },
            text = { Text(strings.serverHelp, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showServerHelp = false }) { Text(strings.gotIt) }
            },
        )
    }

    val currentHelp = helpKey
    if (currentHelp != null) {
        AlertDialog(
            onDismissRequest = { helpKey = null },
            title = { Text(strings.paramLabel(currentHelp)) },
            text = { Text(strings.paramHelp(currentHelp), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { helpKey = null }) { Text(strings.gotIt) }
            },
        )
    }

    if (showApplyDialog) {
        ApplyConfirmDialog(
            vm = vm,
            onDismiss = { showApplyDialog = false },
            onConfirm = {
                showApplyDialog = false
                vm.applyConfig()
            },
        )
    }

    if (showResetDialog) {
        ResetConfirmDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = {
                showResetDialog = false
                vm.resetConfig()
            },
        )
    }

    if (vm.applying || vm.applyProgress.stage != ApplyStage.IDLE) {
        ApplyingDialog(vm)
    }
}

@Composable
private fun HelpDot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pressScale(scaleDown = 0.85f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "?",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ParamRow(
    title: String,
    value: String,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onHelp: (() -> Unit)?,
    editor: @Composable () -> Unit,
) {
    val dimens = LocalNteDimens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val highlight by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else Color.Transparent,
        animationSpec = tween(140),
        label = "paramHighlight",
    )
    val rotation = rotationFor(expanded)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(highlight)
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = androidx.compose.material3.ripple(),
                            onClick = onToggle,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = dimens.rowVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    if (onHelp != null) HelpDot(onHelp)
                }
            }
            Text(
                text = value,
                style = NumericStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(320)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(120)),
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { editor() }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onHelp: (() -> Unit)?,
) {
    val dimens = LocalNteDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = dimens.rowVertical - 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onHelp != null) HelpDot(onHelp)
            }
        }
        NteSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun OptionList(
    options: List<String>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else Color.Transparent,
                    )
                    .clickable(enabled = enabled) { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // 选中对勾弹入，取消时收回；避免 if 直接增减造成的跳变
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.52f, stiffness = 720f), initialScale = 0.4f),
                    exit = fadeOut(tween(110)) + scaleOut(tween(130), targetScale = 0.4f),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FrameRateEditor(current: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    var custom by remember { mutableStateOf("") }
    var customMode by remember { mutableStateOf(current !in ParamSpec.frameRateOptions) }

    Column(Modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ParamSpec.frameRateOptions.forEach { option ->
                NteChoiceButton(
                    label = "$option",
                    selected = !customMode && current == option,
                    height = 38.dp,
                    onClick = {
                        customMode = false
                        onPick(option)
                    },
                )
            }
            NteChoiceButton(
                label = LocalStrings.current.custom,
                selected = customMode,
                height = 38.dp,
                onClick = {
                    customMode = true
                    custom.toIntOrNull()?.let { if (it in ParamSpec.frameRateRange) onPick(it) }
                },
            )
        }
        AnimatedVisibility(visible = customMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { input ->
                        custom = input.filter { it.isDigit() }.take(3)
                        custom.toIntOrNull()?.let { value ->
                            if (value in ParamSpec.frameRateRange) onPick(value)
                        }
                    },
                    enabled = enabled,
                    singleLine = true,
                    label = { Text("30–240") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

@Composable
private fun SliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
private fun ScaleEditor(current: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val strings = LocalStrings.current
    val extra = LocalNteExtra.current
    Column(Modifier.padding(bottom = 12.dp)) {
        Slider(
            value = current,
            onValueChange = { onChange(ParamSpec.clampScale(it)) },
            valueRange = ParamSpec.RENDER_SCALE_MIN..ParamSpec.RENDER_SCALE_MAX,
            steps = 62,
            enabled = enabled,
            colors = SliderColors(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings.native, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(strings.hd, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(strings.supersampleDefault, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current > 3.0f) {
            Text(
                text = strings.highScaleWarning,
                style = MaterialTheme.typography.bodySmall,
                color = extra.warning,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun FloatEditor(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Slider(
            value = value,
            onValueChange = { onChange(((it * 100f).toInt() / 100f).coerceIn(range.start, range.endInclusive)) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderColors(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                String.format(java.util.Locale.US, "%.2f", range.start),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                String.format(java.util.Locale.US, "%.2f", range.endInclusive),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerRow(
    channel: Channel,
    installed: Boolean,
    selected: Boolean,
    stateLabel: String,
    onSelect: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalNteExtra.current
    val bg by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            !installed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else -> Color.Transparent
        },
        animationSpec = tween(240),
        label = "serverRowBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .then(
                if (installed) {
                    Modifier.clickable(onClick = onSelect)
                } else {
                    Modifier.clickable(onClick = onSelect)
                },
            )
            .alpha(if (installed) 1f else 0.55f)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                strings.channelName(channel),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                channel.pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(140))) },
            label = "serverSelected",
        ) { isSelected ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = stateLabel,
                    color = when {
                        !installed -> extra.neutral
                        isSelected -> extra.accent
                        else -> extra.success
                    },
                )
                if (isSelected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplyConfirmDialog(vm: AppViewModel, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val strings = LocalStrings.current
    var countdown by remember { mutableIntStateOf(3) }
    var skipNext by remember { mutableStateOf(false) }
    val diffs = vm.editing.diffFrom(vm.baseline)

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.confirmApplyTitle) },
        text = {
            Column {
                diffs.forEach { diff ->
                    Text(
                        strings.diffLine(diff),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = skipNext,
                        onCheckedChange = { checked ->
                            skipNext = checked
                            vm.setSkipCountdown(checked)
                        },
                    )
                    Text(strings.doNotAskAgain, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = countdown == 0) {
                Text(if (countdown > 0) strings.confirmApplyCountdown.format(countdown) else strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
    )
}

@Composable
private fun ResetConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val strings = LocalStrings.current
    var countdown by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.resetTitle) },
        text = { Text(strings.resetBody, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = countdown == 0) {
                Text(if (countdown > 0) strings.confirmApplyCountdown.format(countdown) else strings.restoreDefaults)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
    )
}

@Composable
private fun ApplyingDialog(vm: AppViewModel) {
    val strings = LocalStrings.current
    val progress = vm.applyProgress
    val failed = progress.stage == ApplyStage.FAILED

    AlertDialog(
        onDismissRequest = { },
        title = { Text(if (failed) strings.applyFailed else strings.applying) },
        text = {
            Column {
                ProgressBarThin(progress.percent / 100f)
                if (failed && !progress.detail.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        progress.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (failed) {
                TextButton(onClick = { vm.clearApplyProgress() }) { Text(strings.close) }
            }
        },
    )
}
