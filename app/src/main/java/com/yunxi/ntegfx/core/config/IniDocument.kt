package com.yunxi.ntegfx.core.config

/**
 * 保真型 INI 编辑器：只改动目标键所在的行，其余内容（注释、空行、顺序、未知键）原样保留。
 * 规格 3.3 要求写入要基于现有完整文件，避免因缺键导致引擎回退默认值。
 */
class IniDocument private constructor(
    private val lines: MutableList<String>,
) {

    private val sectionPattern = Regex("""^\s*\[(.+?)]\s*$""")
    private val entryPattern = Regex("""^\s*([^=;#\[\]]+?)\s*=(.*)$""")

    fun toText(): String {
        val body = lines.joinToString("\n")
        return if (body.endsWith("\n")) body else "$body\n"
    }

    fun hasSection(name: String): Boolean =
        lines.any { line ->
            val m = sectionPattern.matchEntire(line)
            m != null && m.groupValues[1].trim().equals(name, ignoreCase = true)
        }

    /** 返回包含指定键的节名；键不存在时返回 null。 */
    fun sectionContaining(key: String): String? {
        var current: String? = null
        for (line in lines) {
            val sec = sectionPattern.matchEntire(line)
            if (sec != null) {
                current = sec.groupValues[1].trim()
                continue
            }
            val entry = entryPattern.matchEntire(line) ?: continue
            if (entry.groupValues[1].trim().equals(key, ignoreCase = true)) return current
        }
        return null
    }

    fun get(key: String, section: String? = null): String? {
        var current: String? = null
        for (line in lines) {
            val sec = sectionPattern.matchEntire(line)
            if (sec != null) {
                current = sec.groupValues[1].trim()
                continue
            }
            if (section != null && !current.equals(section, ignoreCase = true)) continue
            val entry = entryPattern.matchEntire(line) ?: continue
            if (entry.groupValues[1].trim().equals(key, ignoreCase = true)) {
                return entry.groupValues[2].trim()
            }
        }
        return null
    }

    fun keysIn(section: String? = null): List<String> {        var current: String? = null
        val result = mutableListOf<String>()
        for (line in lines) {
            val sec = sectionPattern.matchEntire(line)
            if (sec != null) {
                current = sec.groupValues[1].trim()
                continue
            }
            if (section != null && !current.equals(section, ignoreCase = true)) continue
            val entry = entryPattern.matchEntire(line) ?: continue
            result += entry.groupValues[1].trim()
        }
        return result
    }

    /** 返回 true 表示命中了已有键并就地替换。 */
    fun set(key: String, value: String, section: String? = null): Boolean {
        var current: String? = null
        var lastSectionHeaderIndex = -1
        var lastEntryInSection = -1
        var sectionFound = section == null

        for (index in lines.indices) {
            val line = lines[index]
            val sec = sectionPattern.matchEntire(line)
            if (sec != null) {
                current = sec.groupValues[1].trim()
                lastSectionHeaderIndex = index
                if (section != null && current.equals(section, ignoreCase = true)) {
                    sectionFound = true
                    lastEntryInSection = index
                }
                continue
            }
            if (section != null && !current.equals(section, ignoreCase = true)) continue
            val entry = entryPattern.matchEntire(line) ?: continue
            if (entry.groupValues[1].trim().equals(key, ignoreCase = true)) {
                lines[index] = "$key=$value"
                return true
            }
            if (section == null || current.equals(section, ignoreCase = true)) {
                lastEntryInSection = index
            }
        }

        if (section == null) {
            lines.add("$key=$value")
            return false
        }

        if (!sectionFound) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add("[$section]")
            lines.add("$key=$value")
            return false
        }

        val insertAt = if (lastEntryInSection >= 0) lastEntryInSection + 1 else lastSectionHeaderIndex + 1
        lines.add(insertAt, "$key=$value")
        return false
    }

    fun remove(key: String, section: String? = null) {
        var current: String? = null
        val iterator = lines.listIterator()
        while (iterator.hasNext()) {
            val line = iterator.next()
            val sec = sectionPattern.matchEntire(line)
            if (sec != null) {
                current = sec.groupValues[1].trim()
                continue
            }
            if (section != null && !current.equals(section, ignoreCase = true)) continue
            val entry = entryPattern.matchEntire(line) ?: continue
            if (entry.groupValues[1].trim().equals(key, ignoreCase = true)) iterator.remove()
        }
    }

    companion object {
        fun parse(text: String): IniDocument {
            val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
            return IniDocument(normalized.split('\n').toMutableList())
        }

        fun empty(): IniDocument = IniDocument(mutableListOf())
    }
}
