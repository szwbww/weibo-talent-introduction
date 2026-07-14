---
id: K-batch-console-log-timeline
domain: frontend
created: 2026-07-14
last_used: 2026-07-14
hit_count: 5
source: fix-v:batch-send-task-console-frontend:fix-1
severity: P1
---
经验：日志需求若包含批次时间线，只有聚合指标、原因和错误样例仍不足以让操作员定位每轮进展。
正确做法：日志 DOM 与渲染函数须同时提供批次时间线和明确空态，并对所有服务端文本安全输出。
反例：`src/main/resources/static/index.html:1276-1300` 的日志抽屉没有时间线节点，`app.js` 也无对应渲染。
