---
id: K-js-tests-run-via-exec-plugin
domain: build
created: 2026-08-19
last_used: 2026-08-25
hit_count: 3
source: create-p:workbench-repair-01-tab-focus-selector
severity: P2
---

经验：`CLAUDE.md` 的「Commands」章节只列了 Maven 命令，没有前端测试的跑法。任何触及 `src/main/resources/static/` 的计划，如果只写 `mvn test` 作为验收依据，验证方无法单独快速迭代前端测试。

事实（`pom.xml:186-232`，2026-08-19 实测）：JS 测试由 `exec-maven-plugin` 的三个 execution 在 `test` 阶段执行——
- `node-test`：`bash -lc "node --test src/test/js/*.test.js"`
- `node-check-app`：`node --check src/main/resources/static/app.js`
- `node-check-task-modal-runtime`：`node --check src/main/resources/static/task-modal-runtime.js`

三者都受 `${skipNodeTests}` 控制，但该属性**未在 `<properties>` 中声明**（`grep -n skipNodeTests pom.xml` 只命中 :201/:216/:231 三处 `<skip>`），因此默认不跳过，`mvn test` 会跑到它们。

可直接复制的独立命令（无需 JAVA_HOME 前缀，实测 node v22.23.2 通过）：
- 单个文件：`node --test src/test/js/trustReplyWorkbench.test.js`
- 全部：`node --test src/test/js/*.test.js`
- 语法检查：`node --check src/main/resources/static/<file>.js`

通过判据：退出码 0，输出含 `# fail 0`。

建议：把这三条提升进 `CLAUDE.md` 的「Commands」章节，省得每份前端计划都重新查一遍 pom。
