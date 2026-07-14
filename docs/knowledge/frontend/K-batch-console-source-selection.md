---
id: K-batch-console-source-selection
domain: frontend
created: 2026-07-14
last_used: 2026-07-14
hit_count: 5
source: fix-v:batch-send-task-console-frontend:fix-1
severity: P1
---
经验：模糊搜索结果不能隐式选择首项；替换已编辑表单的来源会丢失操作员参数，必须显式确认。
正确做法：显示可点击候选项，用户明确选择后才加载；有 diff 时先确认放弃，取消保持当前来源和 draft。
反例：`src/main/resources/static/app.js:12790-12815` 直接采用 `configs[0]` 并覆盖 draft。
