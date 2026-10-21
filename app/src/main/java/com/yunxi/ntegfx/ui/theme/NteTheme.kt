package com.yunxi.ntegfx.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunxi.ntegfx.core.prefs.AccentColor
import com.yunxi.ntegfx.core.prefs.DarkMode
import com.yunxi.ntegfx.core.prefs.ThemeStyle

// ---------------------------------------------------------------- 设计令牌

@Immutable
data class NteDimens(
    val pagePadding: Dp = 16.dp,
    val cardCorner: Dp = 20.dp,
    val dialogCorner: Dp = 28.dp,
    val cardPadding: Dp = 16.dp,
    val cardGap: Dp = 12.dp,
    val sectionGap: Dp = 24.dp,
    val rowVertical: Dp = 14.dp,
    val outlined: Boolean = false,
    val hairline: Boolean = false,
)

/** 行首图标样式：MD3 圆形色块、极光玻璃方块。 */
enum class IconTileStyle { CIRCLE, GLASS }

/** 开关样式：M3 厚重滑块、霓虹发光。 */
enum class SwitchStyle { MATERIAL, NEON }

/** 背景：纯色 / 极光流动渐变。 */
enum class BackdropStyle { PLAIN, AURORA }

@Immutable
data class NteStyleFeatures(
    val titleGradient: Boolean = false,
    val cardElevation: Dp = 1.dp,
    val insetDivider: Boolean = false,
    val iconTile: IconTileStyle = IconTileStyle.CIRCLE,
    val switchStyle: SwitchStyle = SwitchStyle.MATERIAL,
    val backdrop: BackdropStyle = BackdropStyle.PLAIN,
    val pressScale: Float = 0.972f,
    val navIndicator: Boolean = true,
    val glass: Boolean = false,
    val hairlineNav: Boolean = false,
)

@Immutable
data class NteExtraColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val accent: Color,
    val neutral: Color,
)

@Immutable
data class NteMotion(
    val quick: Int = 160,
    val standard: Int = 260,
    val slow: Int = 420,
    val stagger: Int = 45,
    val damping: Float = 0.72f,
    val stiffness: Float = 640f,
)

/** 极光背景的调色板：光斑、光幕、底色、暗角，全部由强调色推导。 */
@Immutable
data class AuroraBackdropSpec(
    val blobs: List<Color>,
    val curtainA: Color,
    val curtainB: Color,
    val base: List<Color>,
    val vignette: Color,
    val blobAlpha: Float,
)

val LocalNteFeatures = staticCompositionLocalOf { NteStyleFeatures() }
val LocalNteDimens = staticCompositionLocalOf { NteDimens() }
val LocalNteStyle = staticCompositionLocalOf { ThemeStyle.AURORA }
val LocalNteMotion = staticCompositionLocalOf { NteMotion() }
val LocalNteExtra = staticCompositionLocalOf {
    NteExtraColors(
        success = Color(0xFF2E9E63),
        warning = Color(0xFFC7861B),
        danger = Color(0xFFC63A3F),
        accent = Color(0xFF17B7A8),
        neutral = Color(0xFF6B7280),
    )
}
val LocalAuroraBackdrop = staticCompositionLocalOf {
    AuroraBackdropSpec(
        blobs = listOf(
            Color(0xFF8B5CF6),
            Color(0xFF22D3EE),
            Color(0xFFF472B6),
            Color(0xFF4C6FFF),
            Color(0xFF2DD4BF),
        ),
        curtainA = Color(0xFF8B5CF6),
        curtainB = Color(0xFF22D3EE),
        base = listOf(Color(0xFF0A0714), Color(0xFF0B1226), Color(0xFF05050B)),
        vignette = Color(0xFF05050B),
        blobAlpha = 0.55f,
    )
}

// ---------------------------------------------------------------- MD3

private val Md3Light = lightColorScheme(
    primary = Color(0xFF2C63E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B2B66),
    secondary = Color(0xFF4C5B77),
    secondaryContainer = Color(0xFFE4EAF5),
    background = Color(0xFFFAFBFE),
    onBackground = Color(0xFF14171C),
    surface = Color(0xFFFAFBFE),
    onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF586070),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F8FC),
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerHigh = Color(0xFFEAEFF6),
    surfaceContainerHighest = Color(0xFFE3E9F2),
    outline = Color(0xFFC3CAD6),
    outlineVariant = Color(0xFFDDE3EC),
    error = Color(0xFFB3261E),
)

private val Md3Dark = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF002E6B),
    primaryContainer = Color(0xFF1B4590),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFB9C6DD),
    secondaryContainer = Color(0xFF2B3547),
    background = Color(0xFF0F1216),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF0F1216),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1B2028),
    onSurfaceVariant = Color(0xFFAEB7C6),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF14181D),
    surfaceContainer = Color(0xFF171B21),
    surfaceContainerHigh = Color(0xFF1E242C),
    surfaceContainerHighest = Color(0xFF262D37),
    outline = Color(0xFF3C444F),
    outlineVariant = Color(0xFF2A313A),
    error = Color(0xFFFFB4AB),
)

// ---------------------------------------------------------------- AURORA（极光玻璃）

/**
 * 极光调色板。强调色决定主光，其余光斑取邻近色系补足光谱，
 * 所以四个强调色不是简单换一个主色，而是换一整套极光。
 */
private data class AuroraPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val blobs: List<Color>,
    val curtainA: Color,
    val curtainB: Color,
    val base: List<Color>,
    val vignette: Color,
    val blobAlpha: Float,
)

private fun auroraPalette(accent: AccentColor, dark: Boolean): AuroraPalette {
    val blobsDark: List<Color>
    val blobsLight: List<Color>
    val primary: Color
    val secondary: Color
    val tertiary: Color
    val baseDark: List<Color>

    when (accent) {
        AccentColor.DEFAULT, AccentColor.PURPLE -> {
            primary = if (dark) Color(0xFFA78BFA) else Color(0xFF6C4CE0)
            secondary = if (dark) Color(0xFF4FE0D2) else Color(0xFF0F9E92)
            tertiary = if (dark) Color(0xFFFF8AD1) else Color(0xFFD1479F)
            blobsDark = listOf(
                Color(0xFF8B5CF6),
                Color(0xFF22D3EE),
                Color(0xFFF472B6),
                Color(0xFF4C6FFF),
                Color(0xFF2DD4BF),
            )
            blobsLight = listOf(
                Color(0xFFC4B5FD),
                Color(0xFFA5F3FC),
                Color(0xFFFBCFE8),
                Color(0xFFBFDBFE),
                Color(0xFF99F6E4),
            )
            baseDark = listOf(Color(0xFF0A0714), Color(0xFF0D1028), Color(0xFF05050B))
        }

        AccentColor.BLUE -> {
            primary = if (dark) Color(0xFF63B4FF) else Color(0xFF2E7BE0)
            secondary = if (dark) Color(0xFF5EEAD4) else Color(0xFF0E9384)
            tertiary = if (dark) Color(0xFFA78BFA) else Color(0xFF6D3FD8)
            blobsDark = listOf(
                Color(0xFF3B82F6),
                Color(0xFF22D3EE),
                Color(0xFF818CF8),
                Color(0xFF0EA5E9),
                Color(0xFFF472B6),
            )
            blobsLight = listOf(
                Color(0xFFBFDBFE),
                Color(0xFFA5F3FC),
                Color(0xFFC7D2FE),
                Color(0xFFBAE6FD),
                Color(0xFFFBCFE8),
            )
            baseDark = listOf(Color(0xFF060B16), Color(0xFF0A1530), Color(0xFF04060D))
        }

        AccentColor.GREEN -> {
            primary = if (dark) Color(0xFF4ADE9F) else Color(0xFF0E9E74)
            secondary = if (dark) Color(0xFF7DD3FC) else Color(0xFF1E86C9)
            tertiary = if (dark) Color(0xFFD9F99D) else Color(0xFF6E9B18)
            blobsDark = listOf(
                Color(0xFF22C55E),
                Color(0xFF2DD4BF),
                Color(0xFFA3E635),
                Color(0xFF38BDF8),
                Color(0xFFFBBF24),
            )
            blobsLight = listOf(
                Color(0xFFBBF7D0),
                Color(0xFF99F6E4),
                Color(0xFFE9F9C0),
                Color(0xFFBAE6FD),
                Color(0xFFFDE68A),
            )
            baseDark = listOf(Color(0xFF04100B), Color(0xFF071A18), Color(0xFF03080A))
        }

        AccentColor.ORANGE -> {
            primary = if (dark) Color(0xFFFFB067) else Color(0xFFD96A1E)
            secondary = if (dark) Color(0xFFFDA4AF) else Color(0xFFC2437A)
            tertiary = if (dark) Color(0xFFC4B5FD) else Color(0xFF7B5BE0)
            blobsDark = listOf(
                Color(0xFFF97316),
                Color(0xFFFB7185),
                Color(0xFFFBBF24),
                Color(0xFFA855F7),
                Color(0xFFF472B6),
            )
            blobsLight = listOf(
                Color(0xFFFED7AA),
                Color(0xFFFECDD3),
                Color(0xFFFDE68A),
                Color(0xFFD8B4FE),
                Color(0xFFFBCFE8),
            )
            baseDark = listOf(Color(0xFF120803), Color(0xFF1A0C12), Color(0xFF08050A))
        }
    }

    return if (dark) {
        AuroraPalette(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            blobs = blobsDark,
            curtainA = primary,
            curtainB = secondary,
            base = baseDark,
            vignette = Color(0xFF04040A),
            blobAlpha = 0.62f,
        )
    } else {
        AuroraPalette(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            blobs = blobsLight,
            curtainA = primary,
            curtainB = secondary,
            base = listOf(Color(0xFFFDFBFF), Color(0xFFF3F0FF), Color(0xFFFBF4FA)),
            vignette = Color(0xFFF7F4FE),
            blobAlpha = 0.78f,
        )
    }
}

private fun auroraScheme(accent: AccentColor, dark: Boolean): ColorScheme {
    val p = auroraPalette(accent, dark)
    return if (dark) {
        darkColorScheme(
            primary = p.primary,
            onPrimary = Color(0xFF100A26),
            primaryContainer = lerp(p.primary, Color.Black, 0.62f),
            onPrimaryContainer = lerp(p.primary, Color.White, 0.82f),
            secondary = p.secondary,
            secondaryContainer = p.secondary.copy(alpha = 0.18f),
            tertiary = p.tertiary,
            background = Color(0xFF07070E),
            onBackground = Color(0xFFEDEBFA),
            surface = Color(0xFF0B0B14),
            onSurface = Color(0xFFEDEBFA),
            surfaceVariant = Color(0x14FFFFFF),
            onSurfaceVariant = Color(0xFFABA7CE),
            surfaceContainerLowest = lerp(p.primary, Color.White, 0.94f).copy(alpha = 0.05f),
            surfaceContainerLow = lerp(p.primary, Color.White, 0.92f).copy(alpha = 0.08f),
            surfaceContainer = lerp(p.primary, Color.White, 0.90f).copy(alpha = 0.11f),
            surfaceContainerHigh = lerp(p.primary, Color.White, 0.86f).copy(alpha = 0.16f),
            surfaceContainerHighest = lerp(p.primary, Color.White, 0.82f).copy(alpha = 0.22f),
            outline = p.primary.copy(alpha = 0.32f),
            outlineVariant = Color.White.copy(alpha = 0.12f),
            error = Color(0xFFFF8FA3),
            inverseSurface = Color(0xFFEDEBFA),
            inverseOnSurface = Color(0xFF14121F),
        )
    } else {
        lightColorScheme(
            primary = p.primary,
            onPrimary = Color.White,
            primaryContainer = lerp(p.primary, Color.White, 0.80f),
            onPrimaryContainer = lerp(p.primary, Color.Black, 0.45f),
            secondary = p.secondary,
            secondaryContainer = p.secondary.copy(alpha = 0.16f),
            tertiary = p.tertiary,
            background = Color(0xFFF7F4FE),
            onBackground = Color(0xFF1A1630),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1630),
            surfaceVariant = Color(0x8CFFFFFF),
            onSurfaceVariant = Color(0xFF6C678C),
            surfaceContainerLowest = Color(0xF2FFFFFF),
            surfaceContainerLow = Color(0xE6FFFFFF),
            surfaceContainer = Color(0xDEFFFFFF),
            surfaceContainerHigh = Color(0xF0FFFFFF),
            surfaceContainerHighest = Color(0xFAFFFFFF),
            outline = Color.White.copy(alpha = 0.62f),
            outlineVariant = Color.White.copy(alpha = 0.42f),
            error = Color(0xFFC13B57),
            inverseSurface = Color(0xFF1A1630),
            inverseOnSurface = Color(0xFFF5F2FC),
        )
    }
}

// ---------------------------------------------------------------- 排版

private val NteTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/** AURORA：紧凑字距 + 重字重，冷冽的科技感。 */
private val AuroraTypography = NteTypography.copy(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, letterSpacing = 0.2.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

val NumericStyle = TextStyle(
    fontSize = 20.sp,
    fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.Monospace,
)

// ---------------------------------------------------------------- 主题入口

@Composable
fun resolveDarkMode(mode: DarkMode): Boolean = when (mode) {
    DarkMode.SYSTEM -> isSystemInDarkTheme()
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
}

/** 主题切换时逐色插值，切换过程是平滑过渡而不是硬跳。 */
@Composable
private fun animated(target: ColorScheme, spec: androidx.compose.animation.core.AnimationSpec<Color>): ColorScheme {
    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, spec, label = "secondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, spec, label = "secondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, spec, label = "tertiary")
    val background by animateColorAsState(target.background, spec, label = "background")
    val onBackground by animateColorAsState(target.onBackground, spec, label = "onBackground")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, spec, label = "surfaceContainer")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, spec, label = "surfaceContainerHigh")
    val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, spec, label = "surfaceContainerLow")
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, spec, label = "surfaceContainerHighest")
    val outline by animateColorAsState(target.outline, spec, label = "outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, spec, label = "outlineVariant")
    val error by animateColorAsState(target.error, spec, label = "error")
    val inverseSurface by animateColorAsState(target.inverseSurface, spec, label = "inverseSurface")
    val inverseOnSurface by animateColorAsState(target.inverseOnSurface, spec, label = "inverseOnSurface")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        secondaryContainer = secondaryContainer,
        tertiary = tertiary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline,
        outlineVariant = outlineVariant,
        error = error,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
    )
}

@Composable
fun NteTheme(
    style: ThemeStyle,
    dark: Boolean,
    dynamicColor: Boolean,
    accent: AccentColor = AccentColor.DEFAULT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val target = when (style) {
        ThemeStyle.MD3 -> when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> Md3Dark
            else -> Md3Light
        }
        ThemeStyle.AURORA -> auroraScheme(accent, dark)
    }

    val scheme = animated(target, tween(durationMillis = 360))

    val baseDimens = when (style) {
        ThemeStyle.MD3 -> NteDimens(
            cardCorner = 20.dp, dialogCorner = 28.dp, cardGap = 12.dp, sectionGap = 24.dp,
            rowVertical = 14.dp, cardPadding = 16.dp,
        )
        ThemeStyle.AURORA -> NteDimens(
            pagePadding = 18.dp, cardCorner = 26.dp, dialogCorner = 30.dp, cardPadding = 18.dp,
            cardGap = 14.dp, sectionGap = 26.dp, rowVertical = 15.dp, hairline = true,
        )
    }

    val corner by animateDpAsState(baseDimens.cardCorner, tween(360), label = "corner")
    val dialogCorner by animateDpAsState(baseDimens.dialogCorner, tween(360), label = "dialogCorner")
    val dimens = baseDimens.copy(cardCorner = corner, dialogCorner = dialogCorner)

    val features = when (style) {
        ThemeStyle.MD3 -> NteStyleFeatures(
            cardElevation = 1.dp, iconTile = IconTileStyle.CIRCLE,
            switchStyle = SwitchStyle.MATERIAL, pressScale = 0.975f, navIndicator = true,
        )
        ThemeStyle.AURORA -> NteStyleFeatures(
            titleGradient = true, cardElevation = 0.dp, insetDivider = true,
            iconTile = IconTileStyle.GLASS, switchStyle = SwitchStyle.NEON,
            backdrop = BackdropStyle.AURORA, pressScale = 0.965f,
            navIndicator = true, glass = true,
        )
    }

    val motion = when (style) {
        ThemeStyle.MD3 -> NteMotion(quick = 150, standard = 250, slow = 400, stagger = 40, damping = 0.70f, stiffness = 700f)
        ThemeStyle.AURORA -> NteMotion(quick = 190, standard = 320, slow = 520, stagger = 60, damping = 0.66f, stiffness = 420f)
    }

    val palette = if (style == ThemeStyle.AURORA) auroraPalette(accent, dark) else null

    val extra = if (dark) {
        NteExtraColors(
            success = Color(0xFF4CD98A),
            warning = Color(0xFFFFC15C),
            danger = if (style == ThemeStyle.AURORA) Color(0xFFFF8FA3) else Color(0xFFFF7B7B),
            accent = if (palette != null) palette.secondary else Color(0xFF7FD8FF),
            neutral = Color(0xFF8B94A3),
        )
    } else {
        NteExtraColors(
            success = Color(0xFF2E9E63),
            warning = Color(0xFFC7861B),
            danger = if (style == ThemeStyle.AURORA) Color(0xFFC13B57) else Color(0xFFC63A3F),
            accent = if (palette != null) palette.secondary else Color(0xFF1C87C9),
            neutral = Color(0xFF6B7280),
        )
    }

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(dimens.cardCorner / 2),
        small = RoundedCornerShape(dimens.cardCorner * 0.7f),
        medium = RoundedCornerShape(dimens.cardCorner),
        large = RoundedCornerShape(dimens.dialogCorner),
        extraLarge = RoundedCornerShape(dimens.dialogCorner + 4.dp),
    )

    val typography = when (style) {
        ThemeStyle.MD3 -> NteTypography
        ThemeStyle.AURORA -> AuroraTypography
    }

    val backdrop = if (palette != null) {
        AuroraBackdropSpec(
            blobs = palette.blobs,
            curtainA = palette.curtainA,
            curtainB = palette.curtainB,
            base = palette.base,
            vignette = palette.vignette,
            blobAlpha = palette.blobAlpha,
        )
    } else {
        LocalAuroraBackdrop.current
    }

    CompositionLocalProvider(
        LocalNteDimens provides dimens,
        LocalNteStyle provides style,
        LocalNteExtra provides extra,
        LocalNteFeatures provides features,
        LocalNteMotion provides motion,
        LocalAuroraBackdrop provides backdrop,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
