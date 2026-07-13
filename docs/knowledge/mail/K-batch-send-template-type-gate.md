---
id: K-batch-send-template-type-gate
domain: mail
created: 2026-07-05
last_used: 2026-07-13
hit_count: 3
source: fix-v:batch-send-template-selector:fix-1
severity: P1
---
经验：批量发送配置里持久化的是裸 `templateId`，如果只在正常下拉列表中过滤 INTRODUCTION，仍可能因旧数据或直接 API 写入把非 INTRODUCTION 模板回填并继续保存。
正确做法：模板选择器回填当前已选模板时也必须执行 `mailType == INTRODUCTION` 类型闸门；只允许 disabled INTRODUCTION 作为例外展示并标注禁用，非 INTRODUCTION 一律不得继续作为可保存选项。
反例：`src/main/resources/static/app.js:4044-4047` 对 `selected && !selectedInEnabled` 无条件插回 option，enabled 但非 INTRODUCTION 的模板会被显示并保留。
