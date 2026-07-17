---
id: K-qa-fact-refactor-template-boundary
domain: qa
created: 2026-07-17
last_used: 2026-07-17
hit_count: 4
source: create-p:qa-fact-card-trust-reply-master-plan
---

# QA 事实正文改造必须先切断模板引用

## 结论

当 `mail_compose_template_block` 允许 `QA_RULE` 块时，修改 `qa_rule.reply_body` 的语义会同时改变项目介绍信，不能直接进行。

## 安全顺序

1. 部署前确认 QA 变体为空；非空则先冻结并人工处理。
2. 把有效 `QA_RULE` 块的主 `reply_body` 原样快照为 `CUSTOM_TEXT`，不选择变体。
3. 保留模板变量到实际渲染时再解析，避免迁移时固化联系人或项目数据。
4. 禁止创建或更新新的 `QA_RULE` 块；旧块仅保留一个发布周期的只读兼容。
5. 完成边界切断后，才能把 QA 主体迁移为事实正文。

## 验证点

- 迁移前后同一介绍信的最终正文一致。
- 模板编辑器不再暴露 QA 块入口。
- `REPLY_SNIPPET` 及其变体不受影响。
