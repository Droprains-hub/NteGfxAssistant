package com.yunxi.ntegfx.ui.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yunxi.ntegfx.ui.components.DividerThin

/* ------------------------------------------------------------------ 数据模型 */

/** Markdown 行内片段，与 UiStrings 的 RichPart 同一思路，只是样式更全。 */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
)

sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Bullet(val spans: List<MdSpan>) : MdBlock
    data class Numbered(val index: Int, val spans: List<MdSpan>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    data object Divider : MdBlock
}

/* ------------------------------------------------------------------ 解析 */

private val DIVIDER = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val BULLET = Regex("^[-*+]\\s+(.+)$")
private val NUMBERED = Regex("^(\\d+)[.)]\\s+(.+)$")

/**
 * 极简 Markdown 解析器（零第三方依赖）。
 * 只覆盖公告 / 更新说明实际用得到的语法：
 * # 标题、**加粗**、*斜体*、`行内代码`、~~删除线~~、[文字](链接)、
 * - 列表、1. 有序列表、> 引用、--- 分割线。
 */
object Markdown {

    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val para = mutableListOf<String>()

        fun flush() {
            if (para.isNotEmpty()) {
                blocks += MdBlock.Paragraph(parseSpans(para.joinToString("\n")))
                para.clear()
            }
        }

        for (raw in source.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flush()
                DIVIDER.matches(trimmed) -> {
                    flush()
                    blocks += MdBlock.Divider
                }
                trimmed.startsWith("#") -> {
                    flush()
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 4)
                    blocks += MdBlock.Heading(level, parseSpans(trimmed.drop(level).trim()))
                }
                trimmed.startsWith(">") -> {
                    flush()
                    blocks += MdBlock.Quote(parseSpans(trimmed.removePrefix(">").trim()))
                }
                BULLET.matches(trimmed) -> {
                    flush()
                    blocks += MdBlock.Bullet(parseSpans(BULLET.matchEntire(trimmed)!!.groupValues[1]))
                }
                NUMBERED.matches(trimmed) -> {
                    flush()
                    val m = NUMBERED.matchEntire(trimmed)!!
                    blocks += MdBlock.Numbered(
                        m.groupValues[1].toIntOrNull() ?: 1,
                        parseSpans(m.groupValues[2]),
                    )
                }
                else -> para += line
            }
        }
        flush()
        return blocks
    }

    /** 行内解析：支持加粗 / 斜体 / 行内代码 / 删除线 / 链接的嵌套组合。 */
    fun parseSpans(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        val plain = StringBuilder()

        fun flush() {
            if (plain.isNotEmpty()) {
                spans += MdSpan(plain.toString())
                plain.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        flush()
                        spans += MdSpan(text.substring(i + 1, end), code = true)
                        i = end + 1
                        continue
                    }
                }
                c == '*' && text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        flush()
                        spans += parseSpans(text.substring(i + 2, end)).map { it.copy(bold = true) }
                        i = end + 2
                        continue
                    }
                }
                c == '~' && text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        flush()
                        spans += parseSpans(text.substring(i + 2, end)).map { it.copy(strike = true) }
                        i = end + 2
                        continue
                    }
                }
                c == '*' || c == '_' -> {
                    val end = text.indexOf(c, i + 1)
                    val wordStart = i == 0 || !text[i - 1].isLetterOrDigit()
                    if (end > i + 1 && wordStart) {
                        flush()
                        spans += parseSpans(text.substring(i + 1, end)).map { it.copy(italic = true) }
                        i = end + 1
                        continue
                    }
                }
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    if (close > i + 1 && close + 1 < text.length && text[close + 1] == '(') {
                        val urlEnd = text.indexOf(')', close + 2)
                        if (urlEnd > close + 2) {
                            flush()
                            val label = text.substring(i + 1, close)
                            val url = text.substring(close + 2, urlEnd)
                            spans += parseSpans(label).map { it.copy(link = url) }
                            i = urlEnd + 1
                            continue
                        }
                    }
                }
            }
            plain.append(c)
            i++
        }
        flush()
        return spans
    }

    /** 去掉 Markdown 记号的纯文本，用于列表副标题等预览场景。 */
    fun plain(source: String): String = source
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("(\\*\\*|__|`|~~)"), "")
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s{0,3}>\\s?"), "")
        .replace(Regex("(?m)^\\s{0,3}[-*+]\\s+"), "")
        .replace(Regex("(?m)^\\s{0,3}\\d+[.)]\\s+"), "")
        .trim()
}

/* ------------------------------------------------------------------ 渲染 */

/** 渲染 Markdown 文本；链接点击走系统浏览器。 */
@Composable
fun MarkdownText(
    source: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(source) { Markdown.parse(source) }
    if (blocks.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> MarkdownBlock(block) }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    val colors = MaterialTheme.colorScheme
    when (block) {
        is MdBlock.Divider -> DividerThin()

        is MdBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                3 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.bodyLarge
            }
            Text(
                text = block.spans.toAnnotated(colors),
                style = style.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
        }

        is MdBlock.Paragraph -> Text(
            text = block.spans.toAnnotated(colors),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )

        is MdBlock.Bullet -> Row {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = block.spans.toAnnotated(colors),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is MdBlock.Numbered -> Row {
            Text(
                "${block.index}.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = block.spans.toAnnotated(colors),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is MdBlock.Quote -> Text(
            text = block.spans.toAnnotated(colors),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
        )
    }
}

@Composable
private fun List<MdSpan>.toAnnotated(colors: androidx.compose.material3.ColorScheme): AnnotatedString =
    buildAnnotatedString {
        this@toAnnotated.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.SemiBold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.strike) TextDecoration.LineThrough else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                color = when {
                    span.link != null -> colors.primary
                    span.code -> colors.onSurface
                    else -> Color.Unspecified
                },
                background = if (span.code) colors.surfaceVariant else Color.Unspecified,
            )
            val link = span.link
            if (link != null) {
                withLink(
                    LinkAnnotation.Url(
                        url = link,
                        styles = TextLinkStyles(style = SpanStyle(color = colors.primary)),
                    ),
                ) {
                    withStyle(style) { append(span.text) }
                }
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
