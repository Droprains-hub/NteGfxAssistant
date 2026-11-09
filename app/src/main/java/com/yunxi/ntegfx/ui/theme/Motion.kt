package com.yunxi.ntegfx.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/**
 * 交互动效的公共底座：按压回弹、入场错峰、数字滚动、极光背景。
 * 时长与阻尼取自当前主题的 [NteMotion]。
 */

/** 按压时整体轻微缩回，松手用弹簧回弹。 */
@Composable
fun Modifier.pressScale(
    enabled: Boolean = true,
    scaleDown: Float = LocalNteFeatures.current.pressScale,
    onClick: (() -> Unit)? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.44f, stiffness = 780f),
        label = "pressScale",
    )
    var result: Modifier = this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    if (onClick != null) {
        result = result.clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick,
        )
    }
    return result
}

/** 列表逐项浮现，index 越大入场越晚。 */
@Composable
fun Stagger(index: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = LocalNteMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * motion.stagger).toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motion.standard, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(motion.slow, easing = FastOutSlowInEasing)) { it / 7 },
        exit = fadeOut(tween(motion.quick)),
        modifier = modifier,
    ) {
        content()
    }
}

/** 数字滚动：状态变化时数值平滑推上去，而不是直接跳变。 */
@Composable
fun rememberCountUp(target: Int, duration: Int = 640): Int {
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        label = "countUp",
    )
    return value
}

/**
 * 极光主题的动态背景：底色渐变 + 五团游走光斑 + 两道光幕 + 暗角。
 * 光斑只用 offset() 改布局位置、不改绘制内容，因此每帧不会重建渐变着色器。
 */
@Composable
fun AuroraBackdrop(dark: Boolean, modifier: Modifier = Modifier) {
    val spec = LocalAuroraBackdrop.current
    val transition = rememberInfiniteTransition(label = "aurora")
    val slow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse),
        label = "slow",
    )
    val mid by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "mid",
    )
    val fast by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fast",
    )
    val drifts = listOf(slow, mid, fast, mid, slow)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(spec.base)),
        )

        // 光幕：两道斜向长条，缓慢上下飘移
        Box(
            Modifier
                .offset {
                    IntOffset(
                        x = (-w * 0.35f).roundToPx(),
                        y = (h * (0.02f + 0.20f * mid)).roundToPx(),
                    )
                }
                .size(width = w * 1.7f, height = h * 0.34f)
                .graphicsLayer { rotationZ = -16f }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            spec.curtainA.copy(alpha = if (dark) 0.20f else 0.26f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .offset {
                    IntOffset(
                        x = (-w * 0.25f).roundToPx(),
                        y = (h * (0.42f + 0.18f * slow)).roundToPx(),
                    )
                }
                .size(width = w * 1.7f, height = h * 0.26f)
                .graphicsLayer { rotationZ = 12f }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            spec.curtainB.copy(alpha = if (dark) 0.16f else 0.22f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // 光斑：尺寸、锚点、漂移速度各不相同，叠出层次
        val sizes = listOf(1.10f, 0.86f, 0.98f, 0.72f, 0.82f)
        val anchorX = listOf(0.02f, 0.58f, 0.22f, 0.80f, 0.38f)
        val anchorY = listOf(-0.08f, 0.12f, 0.46f, 0.58f, 0.80f)
        spec.blobs.forEachIndexed { index, color ->
            val diameter = w * sizes[index % sizes.size]
            val travel = w * 0.22f
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (w * anchorX[index] + travel * (drifts[index % drifts.size] - 0.5f) * 2f).roundToPx(),
                            y = (h * anchorY[index] + travel * 0.7f * (drifts[(index + 2) % drifts.size] - 0.5f) * 2f).roundToPx(),
                        )
                    }
                    .size(diameter)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                color.copy(alpha = spec.blobAlpha),
                                color.copy(alpha = spec.blobAlpha * 0.42f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }

        // 暗角：压住四边，保证前景文字对比度
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color.Transparent,
                            spec.vignette.copy(alpha = if (dark) 0.40f else 0.28f),
                            spec.vignette.copy(alpha = if (dark) 0.86f else 0.70f),
                        ),
                    ),
                ),
        )
    }
}

/** 供按压态读取的交互源，用于需要在按下时改变其它属性的场景。 */
@Composable
fun rememberPressInteraction(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return source to pressed
}

/** 呼吸点：用于「正在检测」这类持续状态。 */
@Composable
fun breatheAlpha(periodMillis: Int = 1400): Float {
    val transition = rememberInfiniteTransition(label = "breatheAlpha")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    return alpha
}

/** 让旋转箭头随展开状态平滑转动。 */
@Composable
fun rotationFor(expanded: Boolean, degrees: Float = 180f): Float {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) degrees else 0f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 520f),
        label = "rotation",
    )
    return rotation
}

// ---------------------------------------------------------------- 弹窗动画容器

/** 弹窗统一动效参数，各处弹窗保持一致体感。 */
object NteDialogMotion {
    const val ENTER_FADE = 220
    const val ENTER_SCALE = 280
    const val EXIT = 170
    /** 退场动画时长上限，外部延迟回调按此对齐。 */
    const val EXIT_SETTLE = 200
}

/**
 * 带入场与退场动画的 Dialog 容器。
 *
 * Compose 的 Dialog 一旦离开组合窗口立即销毁，直接 `visible = false` 看不到退场；
 * 因此这里用内部状态控制：先播退场，播完再执行真正的关闭动作。
 *
 * [content] 会拿到一个 `close` 函数，按钮调用 `close { 动作 }` 即可获得
 * 「先动画退场、再执行业务动作」的效果。若动作并未把弹窗移出组合
 * （例如只是跳转浏览器），容器会自动回到可见态，不会卡在空白窗口。
 */
@Composable
fun NteAnimatedDialog(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable (close: (() -> Unit) -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val requestClose: (() -> Unit) -> Unit = { action ->
        if (pendingAction == null) {
            visible = false
            pendingAction = action
        }
    }

    Dialog(
        onDismissRequest = { if (dismissible) requestClose(onDismiss) },
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(NteDialogMotion.ENTER_FADE, easing = FastOutSlowInEasing)) +
                scaleIn(
                    tween(NteDialogMotion.ENTER_SCALE, easing = FastOutSlowInEasing),
                    initialScale = 0.92f,
                ) +
                slideInVertically(
                    tween(NteDialogMotion.ENTER_SCALE, easing = FastOutSlowInEasing),
                ) { it / 14 },
            exit = fadeOut(tween(NteDialogMotion.EXIT - 30)) +
                scaleOut(tween(NteDialogMotion.EXIT, easing = FastOutSlowInEasing), targetScale = 0.95f) +
                slideOutVertically(tween(NteDialogMotion.EXIT, easing = FastOutSlowInEasing)) { it / 16 },
        ) {
            content(requestClose)
        }
    }

    LaunchedEffect(pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        delay(NteDialogMotion.EXIT_SETTLE.toLong())
        action()
        // 动作未把弹窗移出组合（如仅跳外链）：复位状态，弹窗淡回原位
        if (pendingAction === action) {
            pendingAction = null
            visible = true
        }
    }
}

// ---------------------------------------------------------------- 错误抖动

/**
 * 错误反馈抖动：每次 [trigger] 递增播放一次横向摆动，用于表单校验失败。
 * 返回 px 偏移量，挂到 `graphicsLayer { translationX = ... }` 上使用。
 */
@Composable
fun rememberShake(trigger: Int): Float {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            offset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 380
                    0f at 0
                    (-13f) at 45
                    11f at 100
                    (-8f) at 155
                    6f at 210
                    (-3f) at 270
                    0f at 380
                },
            )
        }
    }
    return offset.value
}
