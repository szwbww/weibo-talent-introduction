---
id: K-render-preview-response-consumers
domain: qa
created: 2026-07-08
last_used: 2026-07-13
hit_count: 7
source: create-p:unified-mail-preview-drawer
---
经验：`POST /api/qa/render-preview` 的响应契约（`rendered` + `fallbackKeys`）有组装台内联预览这个"沉默消费者"（`app.js refreshComposedRenderedPreview`，≈7391-7433 行），它不在任何预览组件/抽屉的显式调用清单里，改响应结构前必须 grep `render-preview` 全部前端调用点。任何响应演进只做加法（新增 `variables`/`invalidTokens` 类字段），不得改旧字段名称与语义。
另（ES 语义）：text 类型字段（institution/keyword/employment）的 `exists` 查询无法排除空串，按"字段有值"筛选专家时必须服务层对随机批二次过滤空白；keyword/integer 类型 exists 即可信。
关联：K-preview-mirrors-pipeline、K-qa-outbound-render-seams、K-pending-qa-reply-rule-source（同款"共享接口的隐蔽消费者"教训）。
