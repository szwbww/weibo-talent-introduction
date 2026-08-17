# 全链执行顺序：批量控制台修复（A）+ 任务记录页重构（B）

创建日期：2026-08-16
本文件是本目录 **8 份子计划的唯一顺序权威**。两份主计划各自只负责本族的不变量与审计。

---

## 为什么合成一条链

两族独立成文（A 族 03:29，B 族 03:25），但落在同一分支、同一批前端文件上。三处硬重叠：

1. **缓存键三连**：`index.html` 的 `styles.css?v=` / `trust-reply-workbench.js?v=` / `app.js?v=` 必须同值同时 bump，且 `src/test/js/batchSendTaskConsoleVisualFix.test.js` 的 "bumps the stylesheet cache key" 用例硬断言了**具体字符串**。任意两份并行 → 构建期 node 测试红、WAR 构建中止。（来源: K-frontend-cache-key-triad）
2. **`app.js` / `index.html`**：A1/A2 改批量控制台与抽屉族函数，B1/B2/B4 改任务记录视图与跨视图跳转 —— 同文件不同区域，但缓存键与测试断言必冲突。
3. **`TaskExecutionService.kt`**：A2 新增 `listRecentByTaskType`，B1 改 `listExecutions` —— 同文件不同方法，git 可自动合并，但需明确先后。

另有一处**区域重叠**（非文件冲突，但会打架）：A3 把 `#bulkOutreachBtn` 迁到收发件箱面板标题栏；B4 要在收发件箱插入执行过滤提示条。B4 的 S2b-3 已写明须在 A3 落地后看实况再定位。

---

## 顺序（强制，不可并行）

| 序 | 计划 | 主题 | 缓存键取值 | 子系统 | 文件数 | 迁移 |
|---|---|---|---|---|---|---|
| 1 | `a1-batch-list-row-and-drawer-visual.md` | 列表行错位 + 抽屉视觉/层级 | `20260817-v1-batch-console-row-drawer` | 前端 | 5 | — |
| 2 | `a2-batch-manual-log-reachability.md` | 手动执行日志可达性 | `20260817-v2-batch-manual-log-entry` | 前端 + 后端 | 7 | — |
| 3 | `a3-expert-list-rename-and-entry-move.md` | 专家列表改名 + 入口迁移 | `20260817-v3-expert-list-entry-move` | 前端 | 4 | — |
| 4 | `b1-task-execution-list-performance.md` | 列表投影 + 分页 + 索引 | `20260817-v4-task-records-paging` | task + 前端 | 9 | **V100** |
| 5 | `b2-task-type-catalog-semantics.md` | TaskTypeCatalog + 单列语义 | `20260817-v5-task-type-catalog` | task + 前端 | 10 | — |
| 6 | `b3-mail-record-execution-link-backend.md` | `mail_record.task_execution_id` | 不适用（纯后端） | campaign + mail | 10 | **V101** |
| 7 | `b4-task-drilldown-frontend.md` | 明细跳转读取路径 + UI | `20260817-v6-task-drilldown` | mail + 前端 | 10 | — |
| 8 | `b5-task-audit-retention.md` | 90 天保留清理 | 不适用（纯后端） | task | 9 | **V102** |

**A 族在前的理由**：A1–A3 修的是**现存 bug**（列错位、抽屉半透明压住关闭按钮、日志关掉回不去），且结论已在 Chromium 中用真实 `index.html`+`styles.css`+`app.js` 复现取证；B 族是重构与新能力。先修坏的。

**B5 的位置弹性**：它只依赖 B1（需要 `idx_te_started`），与 B2/B3/B4 无冲突，且不碰前端。急需清理磁盘时可提前到序 5，届时迁移版本相应改为 V101，并把 B3 的迁移顺延为 V102。

---

## 缓存键链（唯一权威）

每份改前端的计划**必须**把 `index.html` 三处改为自己那一行的值，并同步 `batchSendTaskConsoleVisualFix.test.js` 的三条断言：

```
当前（未开工）  20260816-v1-batch-console-list-layout
A1              20260817-v1-batch-console-row-drawer
A2              20260817-v2-batch-manual-log-entry
A3              20260817-v3-expert-list-entry-move
B1              20260817-v4-task-records-paging
B2              20260817-v5-task-type-catalog
B3              （不改前端，跳过）
B4              20260817-v6-task-drilldown
B5              （不改前端，跳过）
```

**若实际合并顺序变化，缓存键值按新顺序重排，不得跳号占位。**

---

## 迁移版本链（唯一权威）

```
当前最大  V99__add_gate_filter_enabled_to_batch_send_task_config.sql
B1        V100__add_task_execution_indexes.sql
B3        V101__add_task_execution_id_to_mail_record.sql
B5        V102__add_task_progress_log_created_at_index.sql
```

A 族三份**均无迁移**。同样：合并顺序变化时按新顺序重编号。

---

## 两份主计划的分工

- `batch-console-log-drawer-main.md` —— A 族的共享不变量 M-1…M-4、共享审计 X-1…X-3、共享验证命令。
- `task-records-refactor-main.md` —— B 族的共享不变量 M-1…M-7、共享审计 X-1…X-8、共享验证命令。

⚠️ **两份主计划的不变量编号各自独立且会撞号**（都有 M-1/M-2/M-3/M-4）。引用时必须带族名，写「A 族 M-2」或「B 族 M-1」，不要只写 M-2。

---

## 验证命令（全链共用，权威文本）

> 本项目**必须**用 JDK 11（zulu-11），裸 `mvn` 会构建失败。

```bash
# 全量回归（含 node --test 与两条 node --check）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建（WAR）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 缓存键回归（每份改 index.html 的计划都必跑）
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js

# app.js 语法检查（pom 的 exec-maven-plugin 也跑这条）
node --check src/main/resources/static/app.js

# 全部 JS 用例（不走 Maven，迭代快）
node --test src/test/js/*.test.js

# 空白/换行卫生
git diff --check
```

通过判据：Maven 退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0` 与 `BUILD SUCCESS`；`node --test` 退出码 0 且末尾 `fail 0`；`git diff --check` 无输出。

⚠️ **`verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件**，任何计划都不得拿它当回归门禁。（来源: K-js-test-invocation-surface）

---

## 每份计划开工前的固定动作

1. 读本文件确认自己的序号、缓存键值、迁移版本号仍然有效（前面的计划可能已经改了顺序）。
2. 读本族主计划的不变量与共享审计。
3. 读本计划正文里所有标 ⏳ / ⚠️「执行前须核实」的条目，**先补齐再动手**。B 族已知的待核实项集中在 B2 的 `metricLabel` 表（3 项）、B4 的 `MailboxService` 读取路径与 `MailboxController` 文件名、B5 的 `@Modifying` + DELETE 先例 spike。
4. `git pull` 后确认 `index.html` 当前的三处 `?v=` 值等于链上你前一份的值 —— 若不等，说明有人跳序了，停下来回报。
