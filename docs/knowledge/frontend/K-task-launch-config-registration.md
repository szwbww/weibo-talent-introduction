---
id: K-task-launch-config-registration
domain: frontend
created: 2026-10-18
last_used: 2026-10-23
hit_count: 11
source: create-p:enrichment-improvement-v2
---

新增或扩展任务入口必须审计app.js taskLaunchConfigs的preload/run、handle入口、execute启动请求与实际execution绑定、相关中文reason映射；不要漏真实DOM节点和源文本测试。

2026-10-18更正：连续发现模式POST返回202仅表示受理，executeDiscover走applyContinuousLaunch，再从pipeline状态绑定执行窗口；不能套用所有任务都“POST后立即完成通知”的规则。旧同步路径仍按响应真实executionId绑定。新增配置须在切换modal前冻结，异步响应校验taskType+generation，轮询不得重置用户输入。

证据：app.js:9534、10081、10144及docs/plans/2026-10-18/apollo-evidence/frontend-before.txt。
