# Child 02-replay — Execution Report

_（由实施 agent 写入）_

## Pause — PLAN_CONFLICT（epoch 1，2026-10-13T23:50+0800，controller 记录）

- 状态：**zero commits**；工作树在 `bc84bac009177bc3911f00a8041ca2eb48f347b1` 原样保留（agent 未创建/修改任何文件，未运行构建/测试命令，仅只读检查）。
- 冲突：本计划 §I-4 要求新精确 DOI/PMID work 读取声明 `RequestKind.DISCOVERY + Operation.SINGLETON`；但 `OpenAlexRequestPolicy.deriveOperation` 对 `/works/*` 一律派生 `LIST(1 credit)`，`SINGLETON(0)` 仅覆盖 `^/authors/[^/]+$` 与 API_HOST 回退；`validateOperationTarget` 会因声明不一致抛异常。按字面实现需修改第 11 个文件 `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt`，且与计划「本方案不改 API 计价或额度策略」自相矛盾。
- 既有模型事实（openalex-daily-budget-design 运行确立）：cost 表 `LIST=1 / SEARCH=10 / SINGLETON=0 / CONTENT=100 / RATE_LIMIT=0`；「显式声明 + 结构化派生 + 目标校验」是既有约束；单实体=0 成本是该策略的既有语义，但未覆盖 `/works/{id}`。
- 待人工裁决（A10 修订）：
  - **A**：授权 `OpenAlexRequestPolicy.kt`（第 11 文件），为 `/works/{id}` 增加 SINGLETON 派生（0 credit，与既有单实体语义一致）。
  - **B**：声明既有策略派生的 `LIST`（1 credit/次，policy 文件不改，属计划措辞偏差）。
- 处理：按 fast-p 协议暂停；epoch 1 无产物；批准后以 epoch 2 重派实施者（起点 `bc84bac`）。

## Epoch 2

- 日期：2026-10-14；起点 HEAD `25c7455d51d7f69f247a3028ded351ea9c372af7`（含 controller 记账提交，未回退、未纳入提交）；工作树起点 `git status --porcelain` 为空。
- 计划身份：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/2026-10-13/discovery-identity-recovery-02-replay.md` @ sha256 `4bee186d37236f0e000e07c39941915a8eb37283eda44e4804d1b1ee9950c407`（A10 修订版，11 文件）。
- 修订依据：A10（2026-10-14T00:52+0800 人工批准，选项 A）—— 授权第 11 个文件 `config/OpenAlexRequestPolicy.kt`，为 `/works/{id}` 单实体形态补 `SINGLETON(0 credit)` 派生。

### 变更文件（11）

| 文件 | 摘要 |
|---|---|
| `discovery/service/DiscoveryIdentityRecoveryService.kt`（新增） | 服务端重新取证（PMCID→JATS / DOI·PMID→OpenAlex 精确单篇）、只读 preview、`snapshotHash`/`proposalHash`、apply 重算 + 各层 CAS、`PARTIAL` 不刷新准入、`STALE`/`DEFERRED`/`SOURCE_*` 结果类型与 DTO |
| `discovery/controller/DiscoveryIdentityRecoveryController.kt`（新增） | 登录保护的 `/api/expert-discovery/identity-recovery/preview` 与 `/apply`；401/400/409/503；actor 只取会话 |
| `discovery/service/DiscoveryIdentityRepairService.kt` | 新增受限方法 `repairEmptyNameProof`：只填空姓名段 + proof，已有非空姓名/已有 proof/冲突零写入；自动增量入口不启用 |
| `expert/service/ExpertIndexWriterService.kt` | 新增 `writeDiscoveryNameAndProofIfVersionMatches`：带 `if_seq_no/if_primary_term` 条件补姓名段与 proof，其他字段不动 |
| `discovery/service/OpenAlexDataSource.kt` | 抽出 `parseWork` 供列表复用；新增 `fetchWorkByDoi`/`fetchWorkByPmid`（声明 `DISCOVERY+SINGLETON`，来源 ID 不符抛错、404 返回 null、429 上抛） |
| `config/OpenAlexRequestPolicy.kt` | A10：`SINGLETON_WORK_PATH`（`W…`/`pmid:`/`doi:`）派生 `SINGLETON(0)`；LIST/SEARCH/CONTENT 与目标校验语义不变 |
| `discovery/service/DiscoveryIdentityRecoveryServiceTest.kt`（新增） | 真实 writer + HTTP 替身：跨路径恢复、条件写入、STALE、PARTIAL（不刷新准入）、旧学术保护、别名命中、作者 ID 冲突、来源/证据/额度 |
| `discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt`（新增） | `@WebMvcTest` + 真实 `AuthWebConfig`：401/400/409/503、会话 actor、忽略请求体 actor |
| `discovery/service/OpenAlexDataSourceTest.kt` | 追加精确单篇读取：声明 SINGLETON / 0 credit、来源 ID 不符拒绝、404→null、429 上抛 |
| `tools/discovery-identity-recovery/replay.py`（新增） | 默认 preview；JSONL 留痕、manifest 冻结、apply 前原子备份、按 ID 去重续跑、回滚提案；目录 0700/文件 0600、不记录认证头 |
| `tools/discovery-identity-recovery/test_replay.py`（新增） | unittest：默认预览、日志恢复去重、备份失败保护、权限/留痕边界 |

### 命令与结果（本次调用内全新执行）

| 命令 | 退出码 | 计数 |
|---|---|---|
| C1 `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn -o -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test` | 0 | 156 tests / 0 failures / 0 errors（RecoveryServiceTest 16、ControllerTest 7、OpenAlexDataSourceTest 89、OpenAlexRequestPolicyTest 44）；JS 门禁 pass 1657 / fail 0 |
| C2 `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn -o -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test` | 0 | 294 tests / 0 failures / 0 errors（ExpertDiscoveryServiceTest 184、DiscoveryPipelineServiceTest 48、DiscoveryIdentityRepairServiceTest 13、ExpertIndexWriterServiceTest 49）；JS 门禁 pass 1657 / fail 0 |
| C3（实际命令）`python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'` | 0 | Ran 9 tests — OK |

说明：`discover` 形态可用且可复现，故按 brief 的等价形式选用 `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'`（其余形式 `python3 tools/discovery-identity-recovery/test_replay.py` 亦受支持）。

### 合同核对

- I-1：来源标识只从服务端 `externalIds`（PMCID/DOI/PMID）读取；三层不一致即 `SOURCE_IDENTITY_CONFLICT`；无精确定位 `SOURCE_LOCATOR_MISSING`；不做姓名模糊检索；调用方只交 `docId`。
- I-2：已有非空姓名段必须与来源一致，否则零写入（`NAME_CONFLICT`）；已有 proof/学术字段/APPLICATION/联系人（docId·ORCID·邮箱·别名）/人工决定全部零写入；三层全名只缺 proof → `ROUTE_TO_REPAIR`（走 01）。
- I-3：preview 对 ES/准入/联系人/邮件零写；apply 服务端重新取证重算 hash，不一致 `STALE`（控制器 409）；逐层 CAS；部分写入 `PARTIAL` 且不刷新自动准入；`APPLIED` 时以既有 `recordAutomaticConclusion` 接缝按当前完整姓名重算身份哈希。
- I-4：OpenAlex 精确读取走既有 `getJson`/`requestPolicy`（`RequestKind.DISCOVERY`+`Operation.SINGLETON`）；429/额度延期记 `DEFERRED`；脚本每人一行 JSONL、目录 0700/文件 0600、不记认证头。

### 未决 / 边界

- 无前端；无新增 DB/ES 字段；无迁移；未运行生产 apply、未发送任何邮件；`docs/plans/fast/**` 未进入代码提交。
- apply 后准入重验失败单列为 `ADMISSION_REVERIFY_FAILED`（名字已落地、可重跑），非本 epoch 缺陷。
- 待人工验收 A-1..A-4 按计划书面清单在隔离环境执行。
