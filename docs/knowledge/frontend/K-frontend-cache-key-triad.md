---
id: K-frontend-cache-key-triad
domain: frontend
created: 2026-08-13
last_used: 2026-08-14
hit_count: 4
source: create-p:v6-topnav-glass-navy-restyle
severity: P1
---

经验：`index.html` 中 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 三个缓存键**必须同值、同时 bump**。

- `src/test/js/trustReplyWorkbenchSharedMount.test.js`(~:288)断言三键相等。
- `src/test/js/batchSendTaskConsoleVisualFix.test.js`(~:36,"bumps the stylesheet cache key")断言三键等于具体字符串。

只 bump 部分键 → 构建期 node 测试直接失败（2026-08-13 发布 eda4853 时实测踩坑,WAR 构建中止）。任何涉及静态资源变更的计划,必须把这 4 处(3 引用 + 1 测试断言)列入变更文件清单。
