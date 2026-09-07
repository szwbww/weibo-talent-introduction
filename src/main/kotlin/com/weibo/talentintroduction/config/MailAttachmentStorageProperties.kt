package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.mail-attachment-storage")
data class MailAttachmentStorageProperties(
    /** 历史附件存储根目录；worker 受控生成路径在其下 basePath/transfer/{id}/。 */
    val basePath: String = "/opt/talent/uploads/mail-attachments",
    /** 收信元数据模式开关（03 收信读取是否只登记元数据不取附件内容）；默认关闭。 */
    val metadataOnly: Boolean = false,
    /** 单文件实际字节上限（超过流式中断，FAILED/LIMIT_EXCEEDED）。 */
    val transferMaxBytes: Long = 100L * 1024 * 1024,
    /** 单次传输总时限；不能被续租延长，到时强制关闭连接。 */
    val transferTotalTimeoutSeconds: Long = 600,
    /** IMAP connect/read 超时。 */
    val transferConnectTimeoutMs: Int = 10_000,
    val transferReadTimeoutMs: Int = 10_000,
    /** 流式缓冲/IMAP partial fetch 块大小。 */
    val transferBufferBytes: Int = 64 * 1024,
    /** 租约时长：worker 每 transferRenewSeconds 续租；续租失败必须停止。 */
    val transferLeaseSeconds: Long = 15,
    val transferRenewSeconds: Long = 2,
    /** 单信正文读取字节上限（03：超出保留有界正文并置 bodyTruncated=true）。 */
    val metadataMaxBodyBytes: Int = 2 * 1024 * 1024,
    /** 单信 MIME 节点数上限（03：超出抛明确可重试错误，不返回假完整清单）。 */
    val metadataMaxMimeNodes: Int = 10_000,
    /** 单信元数据总时限秒数（03：每封邮件从结构读取到正文提取的预算，到时抛可重试错误）。 */
    val metadataTotalTimeoutSeconds: Long = 60
)
