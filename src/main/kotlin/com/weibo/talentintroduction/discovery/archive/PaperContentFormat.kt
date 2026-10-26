package com.weibo.talentintroduction.discovery.archive

/** Byte evidence takes precedence over historical HTTP metadata. */
object PaperContentFormat {
    const val VERSION = 1

    fun detect(bytes: ByteArray, kind: ArchiveAttemptKind? = null): String = when {
        bytes.size >= 5 && bytes[0] == 37.toByte() && bytes[1] == 80.toByte() &&
            bytes[2] == 68.toByte() && bytes[3] == 70.toByte() && bytes[4] == 45.toByte() -> "PDF"
        kind == ArchiveAttemptKind.PMC_XML || kind == ArchiveAttemptKind.PMC_OA_XML -> "XML"
        bytes.toString(Charsets.UTF_8).trimStart().startsWith("<") -> "HTML"
        else -> "TEXT"
    }
}
