# Phase 4：专家资料文件浏览、下载和在线预览

> 目标：专家联系详情中可以看到该专家上传的资料文件，支持下载和 PDF/image/text 在线浏览。

## 1. 当前基础

现有能力：

- `MailAttachmentService.saveInboundAttachments(...)` 会保存附件到磁盘。
- 配置类：`MailAttachmentStorageProperties`
- 表：`mail_attachment`
- 表：`expert_document`

现有路径规则：

```text
{basePath}/{expertContactId}/{mailRecordId}/{uuid}-{safeFileName}
```

执行前检查：

```bash
sed -n '1,220p' src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt
sed -n '1,240p' src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt
sed -n '1,220p' src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentRepository.kt
sed -n '1,220p' src/main/kotlin/com/weibo/talentintroduction/document/repository/ExpertDocumentRepository.kt
```

## 2. Repository 补充查询

文件：

```text
src/main/kotlin/com/weibo/talentintroduction/document/repository/ExpertDocumentRepository.kt
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentRepository.kt
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt
```

需要能力：

- 按 `expertContactId` 查 `ExpertDocument`。
- 按 `attachmentId` 查 `MailAttachment`。
- 按 `mailRecordId` 查 `MailRecord` 校验归属。

现有若已有方法，复用；不要重复添加同义方法。

## 3. 新增 ExpertDocumentBrowseService

新增：

```text
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt
```

职责：

- 返回某专家资料文件列表。
- 校验附件属于该专家。
- 安全读取文件。
- 判断可预览类型。

建议 DTO：

```kotlin
data class ExpertDocumentFile(
    val documentId: Long,
    val attachmentId: Long,
    val mailRecordId: Long,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long,
    val documentType: String,
    val documentStatus: String,
    val createdAt: LocalDateTime?,
    val previewable: Boolean
)

data class DocumentFileResource(
    val fileName: String,
    val contentType: String,
    val path: Path,
    val fileSize: Long
)
```

核心校验：

```text
1. document.expertContactId == contactId
2. attachment.mailRecordId 对应 mail_record.expertContactId == contactId
3. storagePath normalize 后必须 startsWith(basePath.normalize())
4. Files.exists(path) 且 Files.isRegularFile(path)
```

可预览类型：

```text
application/pdf
image/*
text/*
```

如果 `contentType` 为空，用文件后缀兜底：

- `.pdf` -> `application/pdf`
- `.png/.jpg/.jpeg/.gif/.webp` -> `image/*`
- `.txt/.csv/.log/.md` -> `text/plain`
- 其他 -> `application/octet-stream`

## 4. Controller API

新增或放入现有专家 controller：

```text
src/main/kotlin/com/weibo/talentintroduction/document/controller/ExpertDocumentBrowseController.kt
```

接口：

```http
GET /api/expert-contacts/{contactId}/documents
GET /api/expert-contacts/{contactId}/attachments/{attachmentId}/download
GET /api/expert-contacts/{contactId}/attachments/{attachmentId}/preview
```

列表响应：

```json
[
  {
    "documentId": 1,
    "attachmentId": 2,
    "mailRecordId": 3,
    "fileName": "cv.pdf",
    "contentType": "application/pdf",
    "fileSize": 10000,
    "documentType": "CV",
    "documentStatus": "PENDING_REVIEW",
    "createdAt": "2026-06-04T12:00:00",
    "previewable": true,
    "downloadUrl": "/api/expert-contacts/11/attachments/2/download",
    "previewUrl": "/api/expert-contacts/11/attachments/2/preview"
  }
]
```

下载响应：

- `Content-Disposition: attachment; filename="..."`
- `Content-Type` 使用推断类型或 `application/octet-stream`

预览响应：

- 如果不可预览，返回 400 或 415。
- `Content-Disposition: inline; filename="..."`

## 5. 安全要求

必须有路径安全校验：

```kotlin
val base = Path.of(properties.basePath).toAbsolutePath().normalize()
val target = Path.of(attachment.storagePath).toAbsolutePath().normalize()
require(target.startsWith(base)) { "Attachment path is outside configured base path" }
```

不要直接相信数据库里的 `storagePath`。

## 6. 前端接口预留

Phase 6 会接 UI。此阶段可先不改页面，但 API 返回必须包含 `downloadUrl`、`previewUrl`，方便前端直接使用。

## 7. 测试

新增：

```text
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt
```

最低覆盖：

- 正常文件能列出。
- 只能访问该专家自己的文件。
- attachment 对应 mailRecord 不属于该 contact 时拒绝。
- `storagePath` 在 basePath 外时拒绝。
- PDF/image/text 判定为 `previewable=true`。
- Office/未知类型 `previewable=false`。

## 8. 验证命令

```bash
node --check src/main/resources/static/app.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

## 9. 验收标准

- `GET /api/expert-contacts/{id}/documents` 能返回专家资料列表。
- 下载接口能下载文件。
- PDF/image/text 能 inline 预览。
- 非该专家附件不能访问。
- 路径穿越被拒绝。

## 10. 禁止事项

- 不要第一版强做 Office 在线预览。
- 不要把绝对 `storagePath` 直接暴露给前端。
- 不要跳过 contactId 归属校验。
