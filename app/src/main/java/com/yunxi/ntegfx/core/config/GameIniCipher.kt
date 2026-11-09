package com.yunxi.ntegfx.core.config

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 游戏 `Saved/Config/Android/` 下各个 ini 的落盘编解码。
 *
 * 格式（逐行，实测逐字节可复现）：
 *
 *     line    = base64( AES-256-ECB( PKCS7( utf8(原文行 + "|SPLIT|") ) ) )
 *     文件    = line + "\r\n" 依次拼接
 *
 * 读取侧不用校验 PKCS7：先按 "|SPLIT|" 切一刀，左边的就是原始行，
 * 所以填充字节是任意值都不影响。
 *
 * 密钥是 32 字节 ASCII，硬编码在 libUnreal.so 的 .rodata（UTF-16LE 存放，偏移 0x366bd8）。
 */
object GameIniCipher {

    /** 32 字节 ASCII，正好 AES-256。 */
    private val KEY = "g1XOq7PPbxoNiVZ2QEsmblMgGsMOpQBp".toByteArray(Charsets.US_ASCII)
    private val SEP = "|SPLIT|".toByteArray(Charsets.US_ASCII)
    private const val BLOCK = 16

    private val base64Line = Regex("^[A-Za-z0-9+/]+={0,2}$")

    /** 粗判：每行都是 4 的倍数长度、只含 Base64 字符。 */
    fun looksEncrypted(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(12).toList()
        if (lines.isEmpty()) return false
        return lines.all { it.length % 4 == 0 && base64Line.matches(it) }
    }

    /** 有密文就解，没有就原样返回；看起来是密文但解不全时返回 null（由调用方决定如何处理）。 */
    fun decodeIfNeeded(text: String?): String? {
        if (text == null) return null
        if (!looksEncrypted(text)) return text
        return decryptOrNull(text)
    }

    /**
     * 严格解密：任何一行解不出来就整体返回 null。
     * 绝不能"跳过坏行继续拼"——那样会把半截配置写回去。
     */
    fun decryptOrNull(encrypted: String): String? {
        val out = StringBuilder()
        var expected = 0
        var decoded = 0
        var first = true
        for (raw in encrypted.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            expected++
            val ct = runCatching { Base64.decode(line, Base64.DEFAULT) }.getOrNull() ?: return null
            if (ct.isEmpty() || ct.size % BLOCK != 0) return null
            val pt = runCatching {
                val cipher = Cipher.getInstance("AES/ECB/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES"))
                cipher.doFinal(ct)
            }.getOrNull() ?: return null
            if (indexOf(pt, SEP) < 0) return null
            if (!first) out.append('\n')
            out.append(cutAtSeparator(pt))
            first = false
            decoded++
        }
        return if (expected == decoded) out.toString() else null
    }

    fun decrypt(encrypted: String): String =
        decryptOrNull(encrypted) ?: throw IllegalStateException("配置解密失败：存在无法解析的行")

    fun encrypt(plain: String): String {
        val lines = plain.split("\n").map { it.trimEnd('\r') }
        // 去掉末尾空行，避免多出一条"只有分隔符"的记录
        val body = lines.dropLastWhile { it.isEmpty() }
        val sb = StringBuilder()
        body.forEachIndexed { index, line ->
            if (index > 0) sb.append("\r\n")
            sb.append(encryptLine(line))
        }
        return sb.toString()
    }

    private fun cutAtSeparator(pt: ByteArray): String {
        val idx = indexOf(pt, SEP)
        val end = if (idx >= 0) idx else pt.size
        return String(pt, 0, end, Charsets.UTF_8)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun encryptLine(line: String): String {
        val raw = line.toByteArray(Charsets.UTF_8) + SEP
        val pad = BLOCK - raw.size % BLOCK
        val padded = raw + ByteArray(pad) { pad.toByte() }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY, "AES"))
        val ct = cipher.doFinal(padded)
        return Base64.encodeToString(ct, Base64.NO_WRAP)
    }
}
