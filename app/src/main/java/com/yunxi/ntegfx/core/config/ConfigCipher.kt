package com.yunxi.ntegfx.core.config

import android.content.Context
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 配置模板解密。
 *
 * assets/config 下存放的是 AES-256-GCM 密文（由 tools/encrypt-config.mjs 生成），
 * 避免解包 APK 后直接读到明文 ini。密文格式：
 *
 *     magic(4 = "NGF1") + iv(12) + ciphertext + tag(16)
 *
 * 说明：密钥同时存在于 APK 与构建脚本中，只能挡住"解包即得明文"的低成本破解，
 * 无法抵御有针对性的逆向。真正需要保密的逻辑不应依赖此机制。
 */
object ConfigCipher {

    private const val MAGIC = "NGF1"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    /** 与构建脚本中的主密钥一致，按 0x5A 异或后存放，避免 strings 直接命中。 */
    private val KEY_MASKED = intArrayOf(
        0x31, 0x64, 0xCB, 0x8E, 0x92, 0xFD, 0x05, 0x58,
        0xE4, 0x1C, 0x63, 0xF6, 0x4D, 0x82, 0xBF, 0xAE,
        0xF3, 0x98, 0xED, 0xBC, 0x49, 0x07, 0xDA, 0xA6,
        0x1D, 0xFB, 0xC1, 0x64, 0x37, 0x76, 0xDF, 0x55,
    )

    private fun key(): ByteArray {
        val k = ByteArray(KEY_MASKED.size)
        for (i in KEY_MASKED.indices) k[i] = (KEY_MASKED[i] xor 0x5A).toByte()
        return k
    }

    /** 读取并解密一个配置模板，返回原文。 */
    fun read(context: Context, binName: String): String {
        val raw = context.assets.open("config/$binName").use { it.readBytes() }
        check(raw.size > 4 + IV_LEN) { "配置资源损坏：$binName" }
        val magic = String(raw, 0, 4, Charsets.US_ASCII)
        check(magic == MAGIC) { "配置资源格式不匹配：$binName" }

        val iv = raw.copyOfRange(4, 4 + IV_LEN)
        val payload = raw.copyOfRange(4 + IV_LEN, raw.size) // 密文 || tag
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key(), "AES"),
            GCMParameterSpec(TAG_BITS, iv),
        )
        val plain = cipher.doFinal(payload)
        return String(plain, Charsets.UTF_8)
    }
}
