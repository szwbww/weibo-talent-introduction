# Manual Acceptance — docs/plans/2026-09-20/00-manual-expert-material-upload-main.md

## Epoch 3 — 2026-09-20T09:33:40Z

- Reviewed code boundary: `d2a7f65ecbc46b5165863dfcab94ae5972f50605..5f4967b8d5f94663266c095e6a2e9ec69f570505`
- Machine report epoch: 3
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | 完整上传闭环 | POST `/uploads` 201；列表新增 STORED/PENDING_REVIEW 手动上传；下载字节一致、预览 200、AI 可读。 | PENDING |  |  |  |
| A-2 | Yes | 多文件串行与部分失败 | 最多一个并发请求；第 2 项失败不阻塞第 3 项；仅重试失败项。 | PENDING |  |  |  |
| A-3 | Yes | 精确容量与旧上传回归 | 104857600 成功；104857601 前端拒绝且直调后端 413；人工回复 11 MiB 仍 413。 | PENDING |  |  |  |
| A-4 | Yes | 邮件附件链路隔离 | 材料数 +1；邮箱附件数和 operatorStatus 不变；原邮件附件原状态机不变。 | PENDING |  |  |  |
| A-5 | Yes | 跨专家安全 | A 下载/预览 200；改为 B 的 contactId 被拒绝，且不泄露路径/临时文件名。 | PENDING |  |  |  |
| A-6 | Yes | 三种材料视图与样式 | inline/drawer 有入口、selectionOnly 无；dialog 的尺寸、白底、圆角、焦点、Esc 行为符合主计划。 | PENDING |  |  |  |
| A-7 | Yes | 缓存与既有 WIP 回归 | 11 资源均 `?v=20260920-manual-material-upload`；SharePoint 文件卡行为保持。 | PENDING |  |  |  |
| A-8 | Yes | 发布门禁与回滚演练 | 后端先、前端后；前端回滚不回退 V130 且旧页面恢复。 | PENDING |  |  |  |

## Human Sign-off

- Decision: PENDING
- Boundary: `5f4967b8d5f94663266c095e6a2e9ec69f570505`
- Reporter: user
- Timestamp:
- Note:
