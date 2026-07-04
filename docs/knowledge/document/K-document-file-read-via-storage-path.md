---
id: K-document-file-read-via-storage-path
domain: document
created: 2026-07-04
last_used: 2026-07-04
hit_count: 2
source: create-p:expert-document-ai-analysis
---
经验：读取专家上传文件物理内容时，路径来源为 `mail_attachment.storage_path`，必须经过 `MailAttachmentStorageProperties.basePath` 的 path-traversal 校验（realPath.startsWith(realBasePath)），`ExpertDocumentBrowseService.validateAndResolve()` 已有此逻辑，新服务复用或复制此校验。
