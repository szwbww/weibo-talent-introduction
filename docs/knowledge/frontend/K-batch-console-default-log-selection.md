---
id: K-batch-console-default-log-selection
domain: frontend
created: 2026-07-14
last_used: 2026-07-14
hit_count: 1
source: create-p:batch-send-task-console-frontend-reverification-fix
severity: P1
---
经验：异步列表默认选中一条记录时，更新 select 不等于更新状态机身份；依赖 state identity 的轮询会静默失效。
正确做法：默认/显式选择都先写入当前 configId 与 executionId，再请求详情；异步响应须确认目标仍是当前抽屉，防止迟到响应覆盖。
