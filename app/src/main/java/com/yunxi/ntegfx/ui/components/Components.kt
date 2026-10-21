package com.yunxi.ntegfx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunxi.ntegfx.core.prefs.ThemeStyle
import com.yunxi.ntegfx.ui.theme.IconTileStyle
import com.yunxi.ntegfx.ui.theme.LocalNteDimens
import com.yunxi.ntegfx.ui.theme.LocalNteFeatures
import com.yunxi.ntegfx.ui.theme.LocalNteMotion
import com.yunxi.ntegfx.ui.theme.LocalNteStyle
import com.yunxi.ntegfx.ui.theme.NteExtraColors
import com.yunxi.ntegfx.ui.theme.SwitchStyle
import com.yunxi.ntegfx.ui.theme.pressScale
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- 卡片

@Composable
fun NteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = LocalNteDimens.current.cardPadding,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalNteDimens.current
    val features = LocalNteFeatures.current
    val shape = RoundedCornerShape(dimens.cardCorner)
    val glass = features.glass

    val fill: Brush = if (glass) {
        // 极光的卡片自带 alpha（玻璃层），这里只叠一层朝向光方向的微渐变，
        // 不能再 copy(alpha=…)，否则会把玻璃改成实心色块
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
    } else {
        SolidColor(containerColor)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (features.cardElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = 5.dp,
                        shape = shape,
                        spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(fill)
            .pressScale(onClick = onClick)
            .then(
                if (glass) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                                Color.White.copy(alpha = 0.10f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.26f),
                            ),
                        ),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

// ---------------------------------------------------------------- 文本

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val style = LocalNteStyle.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style != ThemeStyle.MD3) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(11.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                        ),
                    ),
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Box(
                Modifier
                    .width(3.dp)
                    .height(11.dp)
                    .clip(CircleShape)
                    .background(SolidColor(MaterialTheme.colorScheme.primary)),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 页面主标题。两套主题在这里差异最大：
 * MD3 规整左对齐，AURORA 三段渐变粗字。
 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, collapse: Float = 0f) {
    val style = LocalNteStyle.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 560),
        label = "titleEnter",
    )
    val shrink = 1f - 0.10f * collapse

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = shrink * (0.94f + 0.06f * enter)
                scaleY = shrink * (0.94f + 0.06f * enter)
                alpha = enter * (1f - 0.5f * collapse)
                translationY = (1f - enter) * 18f
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    ) {
        when (style) {
            ThemeStyle.AURORA -> Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    brush = Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                ),
            )

            ThemeStyle.MD3 -> Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// ---------------------------------------------------------------- 行

/** 行首图标：MD3 圆形色块 / 极光玻璃方块。 */
@Composable
private fun LeadingGlyph(icon: ImageVector, tint: Color) {
    when (LocalNteFeatures.current.iconTile) {
        IconTileStyle.CIRCLE -> Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }

        IconTileStyle.GLASS -> Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    leading: ImageVector? = null,
    leadingTint: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val dimens = LocalNteDimens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val highlight by androidx.compose.animation.animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else Color.Transparent,
        animationSpec = tween(140),
        label = "rowHighlight",
    )
    val chevronShift by animateFloatAsState(
        targetValue = if (pressed) 3.5f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
        label = "chevronShift",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(highlight)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        enabled = enabled,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 16.dp, vertical = dimens.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            LeadingGlyph(leading, leadingTint)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(20.dp)
                    .offset(x = chevronShift.dp),
            )
        }
    }
}

@Composable
fun DividerThin(modifier: Modifier = Modifier, startInset: Dp = 0.dp) {
    val features = LocalNteFeatures.current
    val height = if (features.insetDivider) 0.7.dp else 1.dp
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(height)
            .background(
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = if (features.insetDivider) 0.8f else 1f,
                ),
            ),
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 12.dp),
    )
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MonoValue(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = com.yunxi.ntegfx.ui.theme.NumericStyle.copy(
            fontSize = MaterialTheme.typography.titleLarge.fontSize,
            fontWeight = FontWeight.Medium,
        ),
        color = color,
    )
}

/** 数字滚动展示，用于统计卡上的大数字。 */
@Composable
fun AnimatedNumber(
    value: Int,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    suffix: String = "",
) {
    val animated = com.yunxi.ntegfx.ui.theme.rememberCountUp(value)
    Text(text = "$animated$suffix", style = style, color = color, maxLines = 1)
}

// ---------------------------------------------------------------- 开关

/**
 * 自绘开关：一套控件三种外观。
 * AURORA 走霓虹渐变，MD3 保留 M3 的厚重滑块 + 对勾。
 */
@Composable
fun NteSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val switchStyle = LocalNteFeatures.current.switchStyle
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val fraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 720f),
        label = "switchFraction",
    )
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 1100f),
        label = "switchSquash",
    )

    val trackWidth = when (switchStyle) {
        SwitchStyle.MATERIAL -> 52.dp
        SwitchStyle.NEON -> 54.dp
    }
    val trackHeight = when (switchStyle) {
        SwitchStyle.MATERIAL -> 32.dp
        SwitchStyle.NEON -> 30.dp
    }
    val thumbSize = when (switchStyle) {
        SwitchStyle.MATERIAL -> 24.dp
        SwitchStyle.NEON -> 22.dp
    }
    val padding = 4.dp
    val travel = trackWidth - thumbSize - padding * 2

    val onColor = MaterialTheme.colorScheme.primary
    val offColor = when (switchStyle) {
        SwitchStyle.NEON -> Color.White.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val trackColor by androidx.compose.animation.animateColorAsState(
        targetValue = androidx.compose.ui.graphics.lerp(offColor, onColor, fraction),
        animationSpec = tween(220),
        label = "trackColor",
    )
    val thumbColor = if (switchStyle == SwitchStyle.MATERIAL && !checked) {
        MaterialTheme.colorScheme.outline
    } else {
        Color.White
    }

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(
                if (switchStyle == SwitchStyle.NEON && checked) {
                    Brush.horizontalGradient(
                        listOf(onColor, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary),
                    )
                } else {
                    SolidColor(trackColor)
                },
            )
            .then(
                if (switchStyle == SwitchStyle.NEON) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                enabled = enabled && onCheckedChange != null,
                onClick = { onCheckedChange?.invoke(!checked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = padding + travel * fraction)
                .size(thumbSize)
                .graphicsLayer {
                    scaleY = squash
                    scaleX = 1f + (1f - squash) * 0.35f
                }
                .shadow(3.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                .background(thumbColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (switchStyle == SwitchStyle.MATERIAL) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (checked) onColor else Color.Transparent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 其它

@Composable
fun StatusPill(
    text: String,
    color: Color,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) Color.White else color,
        )
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, CircleShape))
}

@Composable
fun PrimaryActionButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val style = LocalNteStyle.current
    val shape = RoundedCornerShape(LocalNteDimens.current.cardCorner)
    val active = enabled && !loading
    val fill: Brush = when {
        !active -> SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
        style == ThemeStyle.AURORA -> Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
            ),
        )

        else -> SolidColor(MaterialTheme.colorScheme.primary)
    }
    val contentColor = if (!active) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (style == ThemeStyle.MD3) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color.White
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressScale(enabled = active, scaleDown = 0.975f, onClick = { if (!loading) onClick() })
            .clip(shape)
            .background(fill)
            .then(
                if (style == ThemeStyle.AURORA && active) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), shape)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
fun ProgressBarThin(progress: Float, modifier: Modifier = Modifier) {
    val motion = LocalNteMotion.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 260f),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(5.dp)
                .clip(CircleShape)
                .background(
                    if (LocalNteStyle.current == ThemeStyle.AURORA) {
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        )
                    } else {
                        SolidColor(MaterialTheme.colorScheme.primary)
                    },
                ),
        )
    }
}

/** 环形进度，主页内存占用等场景。 */
@Composable
fun RingGauge(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    label: String? = null,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 90f),
        label = "ring",
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.10f
            drawArc(
                color = primary.copy(alpha = 0.14f),
                startAngle = 130f,
                sweepAngle = 280f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(primary, tertiary, primary)),
                startAngle = 130f,
                sweepAngle = 280f * animated,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = label ?: "${(animated * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 轻提示：从底部弹入，自动消失。 */
@Composable
fun NteToast(text: String?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf("") }
    SideEffect { if (text != null) last = text }
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(180)) + slideInVertically(tween(280)) { it / 2 } + scaleIn(tween(280), initialScale = 0.92f),
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 3 } + scaleOut(tween(180), targetScale = 0.94f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                last,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 设置页的普通按钮式选项，替代原来的缩略图卡片。 */
@Composable
fun NteChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    onClick: () -> Unit,
) {
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(240),
        label = "choiceBg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            if (LocalNteStyle.current == ThemeStyle.MD3) MaterialTheme.colorScheme.onPrimary else Color.White
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(240),
        label = "choiceFg",
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (LocalNteStyle.current == ThemeStyle.AURORA && !selected) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                } else {
                    Modifier
                },
            )
            .pressScale(scaleDown = 0.95f, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.4f),
                exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.4f),
            ) {
                Row {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
            }
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}

/** 强调色圆点。 */
@Composable
fun AccentSwatch(
    color: Color,
    selected: Boolean,
    label: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ring by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "swatchRing",
    )
    Column(
        modifier = modifier.pressScale(scaleDown = 0.88f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.10f * ring
                    scaleY = 1f + 0.10f * ring
                }
                .border(
                    width = (2.4f * ring).dp,
                    color = if (ring > 0.02f) color else Color.Transparent,
                    shape = CircleShape,
                )
                .padding(4.dp)
                .background(color, CircleShape),
        )
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 极光主题下给卡片画的一层柔光，其它主题不生效。 */
@Composable
fun GlassSheen(modifier: Modifier = Modifier) {
    val features = LocalNteFeatures.current
    if (!features.glass) return
    Box(
        modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                ),
            ),
    )
}
