package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.mail-attachment-storage")
data class MailAttachmentStorageProperties(
    val basePath: String = "/opt/talent/uploads/mail-attachments"
)
