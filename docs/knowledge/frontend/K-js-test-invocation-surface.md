---
id: K-js-test-invocation-surface
domain: frontend
created: 2026-08-07
last_used: 2026-08-07
hit_count: 0
source: create-p:batch-timeline-running-status-render
---

经验：本项目的前端 JS 用例（`src/test/js/*.test.js`，`node:test` + `vm` 抽取 `app.js` 函数）
有两条互不等价的执行入口，写计划的「验证命令」时必须分清：

1. `mvn test` — `exec-maven-plugin` 把 `bash -lc 'node --test src/test/js/*.test.js'`
   绑定在 `test` phase（`pom.xml:188-203`），另有两条 `node --check` 语法检查
   （`app.js`、`task-modal-runtime.js`）。三者都带 `<skip>${skipNodeTests}</skip>`
   （`pom.xml:201/216/231`），而 `skipNodeTests` 在 `pom.xml:19-25` 的 `<properties>`
   中**未定义**（那里只有 `migrationIt`、`mysqlIt`）。未定义属性不解析为 true，
   故默认不跳过——但这是推断，首次执行应确认输出中出现 `node --test` 记录。
2. `verify.sh` — **只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件**，
   不是全量 JS 用例。**不可用作前端计划的回归门禁。**

正确做法：前端计划的权威门禁写成对目标测试文件的 `node --test <file>` 单跑命令
（可原样复制、已实测），把 `mvn test` 作为全量回归另列，并注明其 JS 覆盖为推断。
不要假设 `verify.sh` 覆盖了你改的 JS 用例。

关联：[[K-ui-removal-retires-obsolete-contract-tests]]
