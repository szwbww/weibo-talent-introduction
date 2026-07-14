---
id: K-batch-console-source-identity
domain: frontend
created: 2026-07-14
last_used: 2026-07-14
hit_count: 5
source: fix-v:batch-send-task-console-frontend:fix-1
severity: P1
---
经验：由列表行带入的“来源配置”既是差异 baseline，也是执行与日志的稳定身份；tab 切换或 clone 丢失 id 会把配置级执行降级为独立执行。
正确做法：将来源、baseline、draft 分开保存；仅直接进入手动 tab 时清空来源；完整来源快照必须保留 id 与 updatedAt。
反例：`src/main/resources/static/app.js:12415-12421` 在切 tab 后清空来源，且 `12440-12455` 未复制 id。
