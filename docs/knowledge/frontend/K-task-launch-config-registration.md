---
id: K-task-launch-config-registration
domain: frontend
created: 2026-07-07
last_used: 2026-08-21
hit_count: 8
source: create-p:enrichment-improvement-v2
---

新增可从前端触发的任务须在 `app.js` 中注册四项：① `taskLaunchConfigs` 对象新增条目（title/desc/preload/run）；② `handleXxx()` 入口函数走 `openTaskLaunchModal(taskType)` 路径；③ `executeXxx()` 执行函数走 POST→bindTaskModalExecution→notifyTaskCompletionOnce；④ `filterReasonLabels` 对象新增该任务的失败/过滤原因中文映射。跳过任一步都会导致弹窗功能缺失或显示异常。
