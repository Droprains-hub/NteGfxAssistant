/**
 * 把 config-src/*.ini 加密为 app/src/main/assets/config/*.bin
 *
 * 密钥硬编码在脚本与 App 两端。这挡的是"解包后直接读文本"的低成本破解，
 * 不是密码学意义上的密钥保护——密钥最终仍在 APK 内，能被逆向提取。
 *
 * 产物格式：magic(4) + iv(12) + ciphertext + tag(16)
 * tag 放末尾是为了对齐 Java/Kotlin 的 AES-GCM 约定（密文||tag）。
 *
 * 用法：node tools/encrypt-config.mjs
 */
import { createCipheriv, randomBytes } from 'node:crypto'
import { readFileSync, writeFileSync, readdirSync, mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(ROOT, 'config-src')
const OUT = join(ROOT, 'app', 'src', 'main', 'assets', 'config')

// 32 字节主密钥。末尾注释仅供维护参考，勿改动已发布的值。
const KEY = Buffer.from('6b3e91d4c8a75f02be4639ac17d8e5f4a9c2b7e6135d80fc47a19b3e6d2c850f', 'hex')
const MAGIC = Buffer.from('NGF1', 'ascii')

mkdirSync(OUT, { recursive: true })

const files = readdirSync(SRC).filter((f) => f.endsWith('.ini'))
for (const name of files) {
  const plain = readFileSync(join(SRC, name))
  const iv = randomBytes(12)
  const cipher = createCipheriv('aes-256-gcm', KEY, iv)
  const body = Buffer.concat([cipher.update(plain), cipher.final()])
  const tag = cipher.getAuthTag()
  const packed = Buffer.concat([MAGIC, iv, body, tag])
  const target = join(OUT, name.replace(/\.ini$/, '.bin'))
  writeFileSync(target, packed)
  console.log(`${name}  ${plain.length} B  ->  ${target.split(/[\\/]/).pop()}  ${packed.length} B`)
}
