package com.yunxi.ntegfx.core.root

/**
 * ANF 二进制补丁。
 *
 * 作用：把游戏真正加载的那份 `libUnreal.so` 里的 `ANFDeviceSupported()` 改成恒返回 1，
 * 使设置界面里出现 `ANF 超分` / `ANF 插帧` 两个开关。
 *
 * 三个必须知道的事实（都经过实测）：
 * 1. 游戏**不读** `/data/app/.../lib/arm64/libUnreal.so`，真正 mmap 的是
 *    `/data/data/<pkg>/app_libs/HT/So/arm64-v8a/libUnreal.so`；
 * 2. 这份文件每次启动都会被游戏原地重写回原样，所以打完补丁必须 `chattr +i`；
 * 3. 线上存在两份构建，代码整体位移，必须按文件大小挑偏移，并按原字节校验。
 *
 * 原始 8 字节会备份到 [BACKUP_FILE]，出问题可一键还原。
 */
object AnfPatcher {

    /** 构建识别：文件大小 -> 函数虚拟地址。偏移 = VA - 0x4000（.text 的 sh_addr - sh_offset）。 */
    private val BUILDS = mapOf(
        262_547_776L to 0x861C8AC, // app_libs 热更新构建（当前线上，真身）
        262_277_952L to 0x860F77C, // APK 构建（游戏不加载，兜底）
    )

    private const val TEXT_DELTA = 0x4000L

    /** `sub sp, sp, #0x50` —— 函数序言的原始字节，用来确认偏移没错。 */
    private const val EXPECT_HEX = "ff4301d1"

    /** `mov w0, #1 ; ret` */
    private const val PATCH_SHELL = "printf '\\x20\\x00\\x80\\x52\\xc0\\x03\\x5f\\xd6'"

    const val BACKUP_FILE = "/data/local/tmp/ntegfx_anf_orig.bin"
    private const val PATCH_FILE = "/data/local/tmp/ntegfx_anf_patch.bin"

    /** 游戏真正加载的库路径。 */
    fun libPath(pkg: String): String = "/data/data/$pkg/app_libs/HT/So/arm64-v8a/libUnreal.so"

    enum class State {
        /** 库文件不存在（游戏没装或没解包）。 */
        MISSING,

        /** 文件大小不在已知构建表里，无法安全定位。 */
        UNSUPPORTED_BUILD,

        /** 原始状态。 */
        PRISTINE,

        /** 已打补丁。 */
        PATCHED,

        /** 函数头既不是原始值也不是补丁值——被别的工具改过或文件损坏。 */
        UNKNOWN,
    }

    data class Status(
        val state: State,
        val libPath: String,
        val size: Long = -1L,
        val fileOffset: Long = -1L,
        val currentHex: String = "",
        val detail: String? = null,
    ) {
        val usable: Boolean get() = state == State.PRISTINE || state == State.PATCHED
    }

    /** 只读探测，不改任何东西。 */
    fun status(pkg: String): Status {
        val path = libPath(pkg)
        val sizeRes = Shell.run("stat -c %s ${Shell.quote(path)} 2>/dev/null", 8_000L)
        val size = sizeRes.stdout.trim().toLongOrNull()
            ?: return Status(State.MISSING, path, detail = sizeRes.message)

        val va = BUILDS[size] ?: return Status(
            State.UNSUPPORTED_BUILD, path, size,
            detail = "未登记的构建（$size 字节），为安全起见不提供补丁",
        )
        val off = va - TEXT_DELTA
        val hex = readHex(path, off)
        val state = when {
            hex.isEmpty() -> State.UNKNOWN
            hex == PATCH_HEX -> State.PATCHED
            hex.startsWith(EXPECT_HEX) -> State.PRISTINE
            else -> State.UNKNOWN
        }
        return Status(state, path, size, off, hex, null)
    }

    /** 打补丁。调用前请确保游戏进程已退出。 */
    fun apply(pkg: String): Result<String> {
        val st = status(pkg)
        if (st.state == State.MISSING) return Result.failure(IllegalStateException("找不到 app_libs 里的 libUnreal.so"))
        if (st.state == State.UNSUPPORTED_BUILD) return Result.failure(IllegalStateException(st.detail ?: "构建不支持"))
        if (st.state == State.PATCHED) return Result.success("已经是补丁状态，无需重复操作")
        if (st.state == State.UNKNOWN) {
            return Result.failure(IllegalStateException("函数头不匹配（${st.currentHex}），可能是别的版本或已被修改"))
        }

        val path = Shell.quote(st.libPath)
        val off = st.fileOffset

        // 1) 备份原始 8 字节
        Shell.run("dd if=$path bs=1 skip=$off count=8 of=${Shell.quote(BACKUP_FILE)} 2>/dev/null", 10_000L)

        // 2) 解除 immutable（没设过会失败，忽略）
        Shell.run("chattr -i $path 2>/dev/null", 8_000L)

        // 3) 生成补丁字节并写入
        Shell.run("$PATCH_SHELL > ${Shell.quote(PATCH_FILE)}", 8_000L)
        val write = Shell.run(
            "dd if=${Shell.quote(PATCH_FILE)} of=$path bs=1 seek=$off count=8 conv=notrunc 2>/dev/null",
            20_000L,
        )
        if (!write.ok) return Result.failure(IllegalStateException("写入失败：${write.message}"))

        Shell.run("sync", 15_000L)

        // 4) 锁死，防止游戏启动时重写回去
        val lock = Shell.run("chattr +i $path 2>/dev/null", 8_000L)

        // 5) 复核
        val after = readHex(st.libPath, off)
        if (after != PATCH_HEX) {
            return Result.failure(IllegalStateException("写入后复核失败（当前 $after）"))
        }
        return Result.success(
            if (lock.ok) "补丁已写入并锁定" else "补丁已写入，但 chattr +i 失败，重启后可能被游戏还原",
        )
    }

    /** 还原为原始字节。 */
    fun restore(pkg: String): Result<String> {
        val st = status(pkg)
        if (st.state == State.MISSING) return Result.failure(IllegalStateException("找不到 libUnreal.so"))
        if (st.state == State.UNSUPPORTED_BUILD) return Result.failure(IllegalStateException(st.detail ?: "构建不支持"))

        val has = Shell.run("ls ${Shell.quote(BACKUP_FILE)} 2>/dev/null", 8_000L)
        if (has.stdout.isBlank()) return Result.failure(IllegalStateException("没有找到备份文件，无法还原"))

        val path = Shell.quote(st.libPath)
        Shell.run("chattr -i $path 2>/dev/null", 8_000L)
        val r = Shell.run(
            "dd if=${Shell.quote(BACKUP_FILE)} of=$path bs=1 seek=${st.fileOffset} count=8 conv=notrunc 2>/dev/null",
            20_000L,
        )
        if (!r.ok) return Result.failure(IllegalStateException("还原失败：${r.message}"))
        Shell.run("sync", 15_000L)
        val after = readHex(st.libPath, st.fileOffset)
        return if (after.startsWith(EXPECT_HEX)) Result.success("已还原为原始字节")
        else Result.failure(IllegalStateException("还原后复核失败（当前 $after）"))
    }

    private fun readHex(path: String, offset: Long): String =
        Shell.run("dd if=${Shell.quote(path)} bs=1 skip=$offset count=8 2>/dev/null | od -An -tx1", 10_000L)
            .stdout
            .replace(Regex("\\s+"), "")
            .trim()

    private const val PATCH_HEX = "20008052c0035fd6"
}
