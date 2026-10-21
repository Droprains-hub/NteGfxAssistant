package com.yunxi.ntegfx.ui.onboarding

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import com.yunxi.ntegfx.BuildConfig
import com.yunxi.ntegfx.ui.AppViewModel
import com.yunxi.ntegfx.ui.LocalStrings
import com.yunxi.ntegfx.ui.components.NteCard
import com.yunxi.ntegfx.ui.components.PrimaryActionButton
import com.yunxi.ntegfx.ui.text.RichBlock
import com.yunxi.ntegfx.ui.text.RichPart
import com.yunxi.ntegfx.ui.theme.LocalNteExtra
import com.yunxi.ntegfx.ui.theme.Stagger
import java.util.Random
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog

/** 开屏使用说明：窗口式卡片 + 同意勾选 + 初中难度验证。 */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val strings = LocalStrings.current
    val extra = LocalNteExtra.current
    var accepted by remember { mutableStateOf(false) }
    var showVerify by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = strings.obTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                ) {
                    Spacer(Modifier.height(16.dp))
                    strings.obParagraphs.forEachIndexed { index, block ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        ParagraphRow(index + 1, block)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                            Text(
                                text = strings.obAgree,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (accepted) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PrimaryActionButton(
                            text = strings.obContinue,
                            enabled = accepted,
                            onClick = { showVerify = true },
                        )
                        Text(
                            text = "Root · ${if (vm.root.granted) strings.rootGrantedWith(vm.root.manager.displayName) else strings.rootNotGranted}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (vm.root.granted) extra.success else extra.neutral,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (showVerify) {
        VerificationDialog(
            onPass = {
                showVerify = false
                vm.completeOnboarding()
            },
        )
    }
}

@Composable
private fun ParagraphRow(index: Int, block: RichBlock) {
    val extra = LocalNteExtra.current
    val annotated = buildAnnotatedString {
        block.parts.forEach { part: RichPart ->
            if (part.strong) {
                withStyle(SpanStyle(color = extra.danger, fontWeight = FontWeight.SemiBold)) {
                    append(part.text)
                }
            } else {
                append(part.text)
            }
        }
    }
    Row {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------- 验证

private data class Challenge(val expression: String, val answer: Int)

/** 初中难度题库：一元一次方程、分数乘法、平方差、百分数，答案均为正整数。 */
private fun newChallenge(): Challenge {
    val random = Random()
    return when (random.nextInt(5)) {
        0 -> {
            val x = random.nextInt(2, 13)
            val a = random.nextInt(2, 9)
            val b = random.nextInt(1, 25)
            Challenge("${a}x + $b = ${a * x + b}", x)
        }
        1 -> {
            val x = random.nextInt(3, 16)
            val a = random.nextInt(3, 9)
            val b = random.nextInt(1, 20)
            Challenge("${a}x − $b = ${a * x - b}", x)
        }
        2 -> {
            val b = random.nextInt(3, 9)
            val k = random.nextInt(2, 6)
            val a = random.nextInt(1, b)
            Challenge("$a/$b × ${b * k} = ?", a * k)
        }
        3 -> {
            val a = random.nextInt(9, 21)
            val b = random.nextInt(2, a - 2)
            Challenge("${a}² − ${b}² = ?", a * a - b * b)
        }
        else -> {
            val base = random.nextInt(4, 21) * 5
            val pct = random.nextInt(1, 5) * 10
            Challenge("$base 的 $pct% 是？", base * pct / 100)
        }
    }
}

@Composable
private fun VerificationDialog(onPass: () -> Unit) {
    val strings = LocalStrings.current
    val extra = LocalNteExtra.current
    var challenge by remember { mutableStateOf(newChallenge()) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun submit() {
        val value = input.trim().toIntOrNull()
        if (value == challenge.answer) {
            onPass()
        } else {
            error = true
            input = ""
        }
    }

    Dialog(onDismissRequest = { }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = strings.verifyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (challenge.expression.endsWith("?") || challenge.expression.endsWith("？")) {
                        challenge.expression
                    } else {
                        "${challenge.expression} = ?"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.filter { ch -> ch.isDigit() }.take(5)
                        error = false
                    },
                    singleLine = true,
                    isError = error,
                    placeholder = { Text(strings.verifyPlaceholder) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                )
                if (error) {
                    Text(
                        text = strings.verifyWrong,
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.danger,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            challenge = newChallenge()
                            input = ""
                            error = false
                        },
                    ) { Text(strings.verifyRefresh, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { submit() }, enabled = input.isNotBlank()) {
                        Text(strings.confirm)
                    }
                }
            }
        }
    }
}
