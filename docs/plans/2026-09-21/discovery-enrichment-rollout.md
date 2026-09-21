# 深度发现扩量 · 上线操作与验证记录（子计划 09：公平额度与逐步扩量）

状态：代码与定向测试已完成（本文档随子计划 09 的提交入库）；**分档实测的数字由运营在测试环境逐档执行后填入下表**。
本文件不含任何 Key/凭据。OpenAlex Key 只来自环境变量 `OPENALEX_API_KEY`，不写文件、不进日志、不进任务详情。

对应主方案验收：A-4（多来源与公平额度）、本页 A-2（分档扩量）、A-3（保持原业务）。
对应不变量：I-1（批量 ≠ 任务总量）、I-2（公平且不越界）、I-3（按明确停止原因续跑）、I-4（上线凭新增实测）。

## 1. 本次调参后的默认值（`application.yml` → 环境变量）

| 配置项 | 环境变量 | 默认值 | 语义 |
|---|---|---:|---|
| `expert-discovery.max-papers-per-run` | `EXPERT_DISCOVERY_MAX_PAPERS` | 15000 | **全部论文源**的单次运行上限（全局 cap） |
| `expert-discovery.max-authors-per-run` | `EXPERT_DISCOVERY_MAX_AUTHORS` | 20000 | 作者总数防护（ORCID 记录同受约束） |
| `expert-discovery.time-budget` | `EXPERT_DISCOVERY_TIME_BUDGET` | `4h` | 单次运行时间预算；到点按 `TIME_BUDGET` 停止 |
| `expert-discovery.fetch-concurrency` | `EXPERT_DISCOVERY_FETCH_CONCURRENCY` | 4 | 全文/邮箱抽取并发，本轮**不放大** |
| `openalex.max-papers-per-source` | `OPENALEX_MAX_PAPERS` | 10000 | OpenAlex 本源上限 |
| `crossref.max-papers-per-source` | `CROSSREF_MAX_PAPERS` | 1000 | Crossref 本源上限 |
| `core.max-papers-per-source` | `CORE_MAX_PAPERS` | 1000 | CORE 本源上限 |
| `arxiv.max-papers-per-source` | `ARXIV_MAX_PAPERS` | 2000 | arXiv 本源上限 |
| `europe-pmc.max-papers-per-source` | `EUROPE_PMC_MAX_PAPERS` | 1500 | 保持原值（默认 scope 排除） |
| `pmc-oa.max-papers-per-source` | `PMC_OA_MAX_PAPERS` | 1000 | 保持原值（默认 scope 排除） |
| `expert-discovery.orcid.max-records-per-run` | `ORCID_MAX_RECORDS` | 1000 | ORCID 记录限额，独立于论文全局 cap |
| `expert-discovery.auto-enrichment-enabled` | `EXPERT_DISCOVERY_AUTO_ENRICHMENT_ENABLED` | false | 自动补全 worker 开关（08），默认关 |

论文页 100 与补全批量 100 只是**每批大小**，不是每日总量：默认 scope 下四源基础份额合计
`4 × min(100, cap) = 400`，Default 配置的全局 cap（15000）远大于它。

额度分配口径（I-2）：按来源顺序，`本源额度 = min(本源上限, 全局剩余 - 后来源保留份额)`；
后来源保留份额 = `Σ min(页大小, 后来源上限)`，即每个后来源先保底一页。
因此 OpenAlex 不可能吃掉其他来源的保底份额，而穷尽/失效来源没用掉的份额会留在全局剩余里给后来源复用。
启动校验：`全局上限 ≥ Σ min(页大小, 各启用来源上限)`，不满足即**拒绝启动**并给出配置错误
（不会静默饿死后来源）；`0` 一律是配置错误，不代表无限量。

## 2. 本次实施已完成的验证（可复现）

- 定向命令（本工作区、JDK 11）：
  `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest`
  → `BUILD SUCCESS`，`Tests run: 122, Failures: 0, Errors: 0, Skipped: 0`（Service 114 / Scheduler 8）。
- 配置绑定冒烟（`time-budget: 4h` 的 Spring 转换）：`mvn test -Dtest=ExpertDiscoveryControllerMvcTest -Dtalent-introduction.expert-discovery.time-budget=4h` → 3/3 通过。
- 关键断言（真实请求/持久化边界，非内部函数调用）：
  - 全局上限恰好覆盖各源一页（400 = 四源各 100）时，每源恰好发出一次请求、总论文 400；
  - 一个来源真实穷尽（只有 40 篇）后释放的 60 篇进入后来源配额（后来源实际 160），总量仍不越过全局 cap；
  - 全局 cap 不足基础份额（300 < 400）时**抛配置错误且零请求、零检查点写入**；
  - 手选单源（`sources=["ARXIV"]`）全额使用自身额度、OpenAlex 零请求；
  - 时间预算到点后不再发请求，半页不推进（检查点停在进入页），`stopReason=TIME_BUDGET`、`pendingWork=true`；
  - `max-papers-per-run=0`、`time-budget=0`、本源上限为 0 三种情形分别报配置错误 / 不发请求，绝不当作无限量；
  - 汇总把「论文 X」与「ORCID 记录 Y」分列，ORCID 的 `runBudget` 保持 1000（不受论文全局 cap 影响）。
- 报告边界：任务 details 的 `bySource.<来源>` 新增 `runBudget`（本次运行额度）与 `unit`（`PAPER`/`RECORD`），
  旧字段与含义不变（`papersSearched` 对 ORCID 仍是记录数）；不含任何凭据。
- 未执行项：本文档不含生产实测数字（I-4 要求的分档实测由运营按第 3 节执行后填写）。

## 3. 分档扩量操作步骤（2500 → 5000 → 10000）

前置：测试环境；邮件调度关闭（`MAIL_SCHEDULING_ENABLED=false`，且确认自动外发不会因新候选触发）；
`OPENALEX_API_KEY` 由环境变量提供；先恢复一次**小量端到端**（把全局 cap 设为 `400`，确认四源各 100、
任务详情里每源 `runBudget=100`、`stopReason` 明确），确认端到端可用后再进档。

每一档的操作：

1. 设置该档的全局上限（其余来源上限保持默认），例如 2500 档：
   `EXPERT_DISCOVERY_MAX_PAPERS=2500`（OpenAlex 会自动只拿到 `2500 - 300 = 2200`，其余三源各 100）。
2. 触发一次定时/手动发现，等它自然结束（不打断）；记录任务执行记录的 `processedCount`、`summaryText`、details。
3. 打开专家详情与任务记录，按第 4 节表格逐项抄录**实测**数字。
4. 与上一档比较：唯一新增专家是否随额度近似线性增长、补全成功率是否不下降、耗时/失败分布是否可控。
5. 全部满足再进下一档；任一档不满足（新增回落、补全失败率上升、失败集中在同一来源），停在该档并按第 5 节回滚。

完成指标（I-4）：只用**唯一新增专家数**（details `bySource[*].indexed` 之和）与补全链路计数
（`claimed/succeeded/pending/unmatched/failed`）判断，**不得**用 `papersSearched`/供应商 `totalHits` 等
搜索元数据数或名义 API 最大量作为完成指标。

## 4. 各档实测记录表（由运营填写）

| 档位 | 日期/执行ID | 唯一新增专家 | 重复 | 补全 成功/待补/未匹配/失败 | 总耗时 | 各来源 papersSearched（含 runBudget） | stopReason 分布 | 结论 |
|---|---|---|---|---|---|---|---|---|
| 小量端到端（cap 400） | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |
| 2500 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |
| 5000 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |
| 10000 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |

抄录位置：任务执行记录 `details.bySource.<来源>` 的 `papersSearched / duplicates / indexed / promoted /
runBudget / unit / stopReason / failureReasons / apiRequests / pendingWork`；补全批次用
`details.claimed/succeeded/pending/unmatched/failed` 与逐源 `bySource`。

## 5. 回滚开关

| 目标 | 操作 | 说明 |
|---|---|---|
| 退到上一档量级 | 把 `EXPERT_DISCOVERY_MAX_PAPERS`（以及需要时 `OPENALEX_MAX_PAPERS` 等单源上限）改回上一档值并重启 | 游标/检查点与档位无关（检查点 key 只含查询条件/scope/年份/页大小），**无需重置游标** |
| 暂停定时发现 | `EXPERT_DISCOVERY_ENABLED=false` 并重启（或把 `EXPERT_DISCOVERY_CRON` 设为 `-`） | 不影响既有三个手动补采 scope |
| 停自动补全 | `EXPERT_DISCOVERY_AUTO_ENRICHMENT_ENABLED=false` | worker 仍存在但什么都不做；未完成任务留在队列表等恢复 |
| 限缩来源 | 手动运行只传 `sources=[...]` | 人工少源任务只分配所选来源，不受其他来源保留份额限制 |

回滚不需要改数据库；已写入的 `discovery_source_cursor` 行继续可用。

## 6. 游标保存与恢复（含「重扫期」判定）

- 权威位置在 `discovery_source_cursor`：`source_name = "<SOURCE>:v2:<hash>"`，`cursor_value = "v2|ACTIVE|<cursor>"`
  或 `"v2|EXHAUSTED|"`；`papers_processed_total` **只是累计计数**，绝不能用来反推恢复位置。
- 停止原因与检查点：API 额度（`BUDGET_DEFERRED`）、源 cap（`SOURCE_LIMIT`）、全局 cap（`GLOBAL_PAPER_LIMIT`）、
  时间预算（`TIME_BUDGET`）、取消（`CANCELLED`）、供应商分页窗口（`WINDOW_LIMIT`）都保存**进入当前页**的游标；
  页内 RAW 写入失败（`RAW_WRITE_INCOMPLETE`）与补全入队失败（`ENQUEUE_INCOMPLETE`）同样保留进入页游标以便重放。
- 恢复前核对（先核验完整性再续跑）：
  1. 目标 key 的行存在，且 `cursor_value` 是 `v2|` envelope —— 旧格式（裸 source_name + 裸游标）**不会被采信**，
     系统按「无检查点」从头开始；
  2. `last_run_at` 与上次运行吻合；
  3. 用 `EXHAUSTED` 判断上一周期是否已翻到底（已翻到底则本次从头重开，靠去重避免重复收录）。
- 无法恢复时：明确标记**重扫期**（从该来源的头开始重跑，重复由去重逻辑吸收，`indexed` 只计新增），
  不要从截断日志里猜游标，也不要用 `papers_processed_total` 倒推位置。

## 7. 调度与额度重置

- 定时发现仍是 `0 0 2 * * ?`（服务器时区，生产为 Asia/Shanghai）—— 本轮不改。
- OpenAlex 日额度按 **UTC 日**重置：额度不足时任务按 `BUDGET_DEFERRED` 结束并记录 `resetAt`，
  自动补全 worker 在该 reset 时刻之后才会重新领取被延期的任务（不密集重试）。
- 全文/邮箱抽取并发保持 `fetchConcurrency=4`（本轮不放大并发，避免请求队列膨胀）。
