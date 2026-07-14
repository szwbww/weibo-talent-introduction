---
id: K-batch-console-time-unit-normalization
domain: frontend
created: 2026-07-14
last_used: 2026-07-14
hit_count: 1
source: create-p:batch-send-task-console-frontend-reverification-fix
severity: P1
---
经验：表单以秒展示、API 以毫秒存储时，读取边界必须立即转换；同名 `*Ms` 字段不能暂存秒值。
正确做法：表单读取函数输出 API 规范单位，baseline、diff、确认 payload 全程只用同一单位；测试同时覆盖未编辑无 diff 与真实秒值变更。
