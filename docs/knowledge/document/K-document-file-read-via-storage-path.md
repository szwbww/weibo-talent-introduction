---
id: K-document-file-read-via-storage-path
domain: document
created: 2026-09-20
last_used: 2026-09-20
hit_count: 6
source: create-p:expert-document-ai-analysis
---
经验：读取专家上传文件物理内容时，路径来源为 `mail_attachment.storage_path`，必须经过 `MailAttachmentStorageProperties.basePath` 的 path-traversal 校验（`realPath.startsWith(realBasePath)`）。2026-09-20 复核后，权威实现已迁到 `document/ExpertMaterialService.resolveFileReady`；`ExpertDocumentBrowseService`、`DocumentTextExtractor` 等读路径委托该入口。新服务应复用统一 resolver，不复制旧的 browse 私有校验。
