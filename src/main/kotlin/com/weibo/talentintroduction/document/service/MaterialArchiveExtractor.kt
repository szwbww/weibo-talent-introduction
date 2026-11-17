package com.weibo.talentintroduction.document.service

import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.springframework.stereotype.Component
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

data class ExtractedMaterialFile(val entryPath: String, val path: Path, val size: Long)

/** Entry names are provenance only. Disk names are generated, never supplied by an archive. */
@Component
class MaterialArchiveExtractor {
    fun extract(source: Path, output: Path, maxBytes: Long = 500L * 1024 * 1024, maxEntries: Int = 500): List<ExtractedMaterialFile> {
        Files.createDirectories(output)
        val result = mutableListOf<ExtractedMaterialFile>()
        var total = 0L
        val deadline = System.nanoTime() + 60_000_000_000L
        fun budget(size: Int) {
            check(System.nanoTime() < deadline) { "解压超时，请拆分压缩包" }
            require(size.toLong() <= maxBytes - total) { "解压后内容超过 500 MB 限制" }
            total += size
        }
        try {
            RandomAccessFile(source.toFile(), "r").use { file ->
                val magic = ByteArray(8)
                val n = file.read(magic); file.seek(0)
                val format = when {
                    n >= 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4b.toByte() -> ArchiveFormat.ZIP
                    n >= 7 && magic.take(6) == listOf<Byte>(0x52,0x61,0x72,0x21,0x1a,0x07) ->
                        if (magic[6] == 1.toByte()) ArchiveFormat.RAR5 else ArchiveFormat.RAR
                    else -> throw IllegalArgumentException("仅支持 ZIP、RAR 压缩包")
                }
                SevenZip.openInArchive(format, RandomAccessFileInStream(file)).use { archive ->
                    require(archive.numberOfItems <= maxEntries) { "压缩包条目超过 $maxEntries 个限制" }
                    val paths = mutableSetOf<String>()
                    archive.simpleInterface.archiveItems.forEach { item ->
                        budget(0)
                        val entry = validatePath(item.path.orEmpty())
                        require(!item.isEncrypted) { "暂不支持加密压缩包" }
                        require(listOf(PropID.SYM_LINK, PropID.HARD_LINK).all {
                            archive.getProperty(item.itemIndex, it)?.toString().isNullOrEmpty()
                        }) { "压缩包包含链接，无法解析" }
                        val unixMode = (archive.getProperty(item.itemIndex, PropID.POSIX_ATTRIB) as? Number)?.toInt() ?: 0
                        require(unixMode.and(0xf000) != 0xa000) { "压缩包包含符号链接" }
                        if (!item.isFolder) {
                            require(paths.add(entry)) { "压缩包包含重复路径：$entry" }
                            require((item.size ?: 0) <= maxBytes - total) { "解压后内容超过 500 MB 限制" }
                            val target = output.resolve(UUID.randomUUID().toString())
                            Files.newOutputStream(target).use { stream ->
                                val status = item.extractSlow { bytes -> budget(bytes.size); stream.write(bytes); bytes.size }
                                require(status == ExtractOperationResult.OK) { "文件损坏或需要密码：$entry ($status)" }
                            }
                            result += ExtractedMaterialFile(entry, target, Files.size(target))
                        }
                    }
                }
            }
            require(result.isNotEmpty()) { "压缩包没有可保存的文件" }
            return result
        } catch (ex: Exception) {
            // Caller also cleans on transaction failure. No partial output is ever published.
            clean(output)
            if (ex is IllegalArgumentException || ex is IllegalStateException) throw ex
            throw IllegalArgumentException("解压失败：文件损坏、加密或分卷不完整", ex)
        } catch (ex: LinkageError) {
            clean(output)
            throw IllegalStateException("当前服务器无法加载解压组件", ex)
        }
    }

    internal fun validatePath(raw: String): String {
        val path = raw.replace('\\', '/').trimEnd('/')
        val parts = path.split('/')
        require(path.isNotBlank() && path.length <= 2048 && !path.startsWith('/') && !path.contains(':') &&
            path.none { it.isISOControl() } && parts.size <= 32 && parts.all { it.isNotEmpty() && it != "." && it != ".." && it.length <= 255 }) {
            "压缩包包含非法或过长的文件路径"
        }
        return path
    }

    fun clean(directory: Path) {
        if (Files.exists(directory)) Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }
}
