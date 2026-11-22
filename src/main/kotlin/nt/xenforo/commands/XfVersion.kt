package nt.xenforo.commands

object XfVersion {
    private const val STABLE = 7
    private const val PATCH_LEVEL = 9
    private const val DOUBLE_DIGIT_BUILD = 10
    private const val PART_LIMIT = 99

    private val VERSION = Regex(
        """^(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?(?:\s*(alpha|beta|release\s+candidate|rc|patch\s+level|patch|pl)(?:\s*(\d+))?)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val PLAIN = Regex("""^(\d+)\.(\d+)\.(\d+)$""")
    private val WHITESPACE = Regex("""\s+""")

    fun id(versionString: String): Int? {
        val match = VERSION.matchEntire(versionString.trim()) ?: return null
        val (major, minor, patch, fourth, stageName, stageBuild) = match.destructured
        if (minor.toInt() > PART_LIMIT || patch.toInt() > PART_LIMIT) return null
        var status = when (stageName.lowercase().replace(WHITESPACE, " ")) {
            "" -> STABLE
            "alpha" -> 1
            "beta" -> 3
            "rc", "release candidate" -> 5
            else -> PATCH_LEVEL
        }
        if (status != STABLE && fourth.isNotEmpty()) return null
        var build = (if (status == STABLE) fourth else stageBuild).ifEmpty { "0" }.toInt()
        if (build >= DOUBLE_DIGIT_BUILD && status != PATCH_LEVEL) {
            status += 1
            build -= DOUBLE_DIGIT_BUILD
        }
        if (build >= DOUBLE_DIGIT_BUILD) return null
        val id = major.toLong() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + status * 10 + build
        return id.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    fun next(versionString: String): String {
        val match = PLAIN.matchEntire(versionString.trim()) ?: return versionString
        val (major, minor, patch) = match.destructured
        return "$major.$minor.${patch.toInt() + 1}"
    }
}
