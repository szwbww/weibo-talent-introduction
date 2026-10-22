# Repair Plan: batch-demand-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-master.md
Baseline identity: commit 1ba7003f2719165e4d420132255d071102cd6c3e; SHA-256 c8922f6c7ab011303844f3c8b7dae181eb99eb22d47bdc7db2662eec2f7c16d1; approved amendments A1–A4.
Verification report: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/review/batch-demand-master/machine-verification.md — aggregate epoch 1, V-1, FAIL / INITIAL, reviewer /root/aggregate_review.
Implementation boundary: d403caeb5dcc4810067293c10600ee57f94e276b..37a76a66a40e1614bec7c1e92324c7beee7c0be0
Pre-repair code head: 37a76a66a40e1614bec7c1e92324c7beee7c0be0
Evidence HEAD at review: 341eed4dfe1d82ae487f08c59a2533ca30f5c978
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master
Branch: fast/batch-demand-master

## Objective

同一表单的服务端缓存身份变化后，刷新失败不得继续显示前一个缓存键的人数与时间；无当前键可验证缓存时显示无值 ERROR。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 / REPAIRABLE | 06 I-3；05 I-2 → master X4/X5 | app.js:24146–24148 在 ERROR.cacheKey 与旧 envelope 不同时拒绝响应，却回退并展示旧 envelope。表单 JSON 无变化不能证明模板指纹未变。 |

复现：先显示 key=A、100人、原 cachedAt；保持表单字段，修改模板引用正文使服务端 key=B；随后 B 的统计失败，返回 HTTP503、ERROR、key=B、summary=null、cachedAt=null。实际 UI 仍保存 A，显示100与旧时间。临时只读诊断 `/private/tmp/batch-aggregate-key-repro.js` 调用生产 JS 函数，exit0并打印该错误结果；其 SHA-256 为97c5a9322f2376a1c11bd72c1813de8bfba3347304db26f5216ebebba4af49c9。现有缓存测试第135–137行反而断言不同键时继续显示100，须修正该断言。

## Findings Excluded

| Finding | Reason |
|---|---|
| Child04 F-1 | 已解决；本轮全量测试重新证明 demand RUNNING totalCount=0、终态为实际处理数。 |
| O-1 计划/证据文档空白字符 | 非产品行为；批准计划与历史证据只读，不作为修复任务。 |
| 人工验收与生产只读集合对照 | 仍待人工；没有伪造失败或把待验收项当修复授权。 |

## Unchanged Contract

- 缓存仅供参考；不改变发送、保存、启动、真实门禁/验证策略和候选资格。
- 同键 ERROR/REFRESHING 可保留该键旧 summary/cachedAt；真正网络错误可保留当前同 snapshot 且未被服务端新键否定的已展示值。
- 不同条件/已失效 seq 的成功或失败均不得覆盖当前界面；两面板独立，关闭后旧回调失效。
- MISS 与 READY/0 区分；cachedAt 与 asOf 区分；partial/原因样本/截断与 XSS 转义保留。
- 不自动全扫、不新增轮询；cacheOnly、显式 refresh、按钮 disabled/恢复合同保留。
- 03/06 S-1/S-2 的既有 DOM、class、文案及 CSS 保持；不新增样式。
- 后端 key/fingerprint/HTTP envelope、启动 versionToken、所有 Kotlin/SQL/配置与历史记录保持。

## Authorized Files

| File | Purpose |
|---|---|
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/resources/static/app.js | 仅修正 refreshRecipientPreview 的错误分支缓存身份处理。 |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/test/js/batchRecipientStatisticsCache.test.js | 修正错误的跨键保留期望；新增同表单、模板指纹换键后503的判别回归。 |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/src/main/resources/static/index.html | 仅将现有16处资源缓存键统一更新为20261023-batch-statistics-key-fix。依据CLAUDE.md:82的K-frontend-cache-key-triad，确保浏览器取得修正脚本。 |

## Repair Tasks

### R-1: 服务端换键后清除旧统计

- Resolves: V-1。
- Root cause: app.js:24148 的无条件旧 envelope 回退把同表单身份误当作同服务端缓存身份。
- Files: 仅上述三个 Authorized Files。
- Change: 当前 seq/snapshot 的 ERROR 明确携带不同 cacheKey 时，使本地旧键 summary/cachedAt 失效；不得将其作为当前条件统计显示。没有可验证当前键旧值时按既有无值 ERROR 渲染，按钮恢复。不得显示响应中未经当前键匹配的旧 summary；不得改为自动重扫。保留同键失败保留值与网络错误同 snapshot 回退。
- Regression test: 在 editor/manual 两面板分别先加载 A/100/原时间，表单字段不变，下一次 refresh 返回503/B/null summary/null cachedAt；断言不显示 A 的100或旧时间，状态“统计不可用”、hint“统计不可用，可重试。”、按钮恢复且总共只有两次请求。保留不同键999不得显示、同键503保留100/原时间、纯网络错误保留同 snapshot 旧值、A→B晚返回、关闭/重开、READY0、partial、XSS与发送不依赖缓存的既有回归。
- Asset delivery: 当前键20261022-batch-demand-cache在src/test/js中无字面量命中；修改index后重查引用。只同步现有16处?v=值，不改变资源顺序、URL、DOM或CSS。
- Existing verification: 执行以下三个JS命令。完整 aggregate re-review 再运行主计划全部必需机器门禁。
- Must not change: 上述 Unchanged Contract。
- Prohibited: Kotlin、SQL、DDL、后端API、cache key定义、发送控制流、通用api helper、CSS、其他测试/文档、计划修订、真实SMTP/生产数据操作。

## Verification Commands

工作目录全部为 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master。

执行者在产品提交前：

1. `node --check src/main/resources/static/app.js`
2. `node --test src/test/js/batchRecipientStatisticsCache.test.js`
3. `node --test src/test/js/*.test.js`

提交后由 fresh aggregate reviewer 必须重新核验以下主计划命令；本阶段不得把本次旧代码的PASS沿用到修复边界：

1. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test`
2. `node --test src/test/js/*.test.js`
3. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=ExpertContactCursorRepositoryIT -DmysqlIt=true`
4. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=ExpertSearchCursorTest -DesIt=true`

显式MySQL/ES IT跳过不算通过；所有命令记录exit/count。不得因验证失败扩大文件范围或执行第二轮修复。

## Completion Criteria

- 判别回归在原边界暴露V-1，在修复边界通过；两面板均不在新服务端键下展示前键人数/时间。
- 同键503及纯网络错误的允许保留语义仍通过；seq/snapshot、MISS/0、诊断、按钮、发送合同回归通过。
- 16处资源键全部相同，动态缓存键测试与全量JS通过。
- 产品改动仅在三个 Authorized Files；无额外业务/测试改动。
- 执行者三条验证命令成功后按下列handoff提交；机器最终PASS须fresh reviewer完成全主计划矩阵及必需命令。人工验收继续PENDING，不在本计划代验。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
明确、人类来源的 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/fix/batch-demand-master/repair.md` 即批准本文件当前内容；不授权其它路径或计划变更。

## Review-Fast-P Execution Handoff

明确、人类来源的 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/fix/batch-demand-master/repair.md` 调用授权：

1. 仅本计划 Authorized Files 与必需验证命令。
2. 完成R-1且执行者三条验证命令通过后，在发出READY_FOR_VERIFICATION前，恰好一个本地产品提交；仅stage上述三个文件，提交主题 `fix(batch-demand): clear statistics on cache key change`。保留分支与工作树，不amend或改写历史。
3. 追加 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/review/batch-demand-master/repair-execution.md，记录准确批准来源、repair文件身份、pre/post code SHA、变更文件、命令与退出/计数、偏离、可获取的executor身份及产品/index clean证据。pre code固定37a76a66a40e1614bec7c1e92324c7beee7c0be0，证据HEAD另行记录，不混同产品SHA。
4. 恰好一个docs-only证据提交，仅含该repair-execution.md，提交主题 `docs(review-fast-p): record batch-demand repair execution`。不得把其他review/fast-p/plan文件混入该提交。
5. 用户调用中要求回到本任务aggregate review时，执行者READY_FOR_VERIFICATION后，在同一任务自动继续 `$review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/fast/batch-demand-master/human-review-handoff.md`，使用已提交repair-execution.md；不让用户逐字段转述executor信息。fresh独立reviewer重验累计边界及全部主计划机器命令。

以上不授权额外文件、另一轮产品修复、push、merge、发布、部署、历史改写或工作树删除。
