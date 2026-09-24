# c1 执行报告：介绍邮件发送前验证与持久化明细

执行者：C1Impl（execute-p，child c1）
计划（批准版，字节冻结）：`docs/plans/2026-09-24/emailable-01-runtime-audit.md`（identity `commit:99aa2ed7e9c93cfc5552f47889670fae96816f01`）
主计划：`docs/plans/2026-09-24/emailable-pre-send-verification.md`
Child brief：`docs/plans/fast/2026-09-24-emailable-pre-send-verification/children/c1/brief.md`
Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-24-emailable-pre-send-verification`
Branch：`fast/2026-09-24-emailable-pre-send-verification`
child_base_sha（实测 HEAD，控制方更正值）：`f64e9f6e9a1e5220d9267d9cde97127ae456df74`
实施提交：见文末「提交」一节（`feat(fast-p): implement c1`，不含本报告）
结果：**READY_FOR_VERIFICATION**

## 1. 变更文件（与计划「变更文件清单」1:1）

| # | 计划清单文件 | 实际改动 | 状态 |
|---|---|---|---|
| 1 | `campaign/domain/BatchExecutionModels.kt` | `BatchExecutionSnapshot.emailVerificationEnabled: Boolean = false`（末位默认参数）；`BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED` + 标签「邮箱验证未通过」 | 已改 |
| 2 | `campaign/service/ManualInitialOutreachService.kt` | `run()` 入口开关/类型/密钥校验；共享介绍邮件引擎接入验证、`mail.to` 断言、SENDING 预占、逐分支审计收尾、专用审计异常边界 | 已改 |
| 3 | `campaign/service/BatchEmailVerificationService.kt` | 新增：HTTP 验证（JDK11 HttpClient）、逐次执行上下文（复用/间隔/取消）、标签、审计协调、受控错误码 | 新增 |
| 4 | `campaign/repository/BatchEmailVerificationRepository.kt` | 新增：JdbcTemplate 仓储 + 同文件 DTO/取值集合/错误码常量 | 新增 |
| 5 | `db/migration/V138__create_batch_email_verification.sql` | 新增明细表、唯一键、游标索引、级联外键 | 新增 |
| 6 | `test/.../BatchEmailVerificationServiceTest.kt` | 新增：HTTP/标签/异常分类/复用/密钥测试（25 例） | 新增 |
| 7 | `test/.../BatchEmailVerificationRepositoryIT.kt` | 新增：真实 MySQL 约束、分页、聚合、级联清理（8 例） | 新增 |
| 8 | `test/.../ManualInitialOutreachServiceTest.kt` | 发送边界与计数（11 例新增）+ 必需依赖（`BatchEmailVerificationService` mock） | 已改 |
| 9 | `test/.../BatchSendTaskRuntimeIntegrationTest.kt` | 共享引擎回归：补必需构造参数 | 已改 |
| 10 | `test/.../FlywayMigrationIntegrationTest.kt` | 19 处「迁到最新」断言 136 → 138；新增 V138 迁移契约测试 | 已改 |

白名单外无任何文件改动（`git status --porcelain` 恰好为上述 10 个文件：5 改 + 5 新增）。

## 2. 关键不变量证据（file:line）

| 不变量 | 实现位置 | 说明 |
|---|---|---|
| I-1 开关范围与缺省 | `BatchExecutionModels.kt:42`；`ManualInitialOutreachService.kt:154-161` | 快照字段默认 false（旧 JSON 缺字段 = false）；`run()` 在任何业务写入前 `require(INTRODUCTION)` 并 `requireConfiguredApiKey()`；关闭时不建上下文（`:535-543`），零 HTTP/仓储调用 |
| I-2 验证对象=最终收件地址 | `BatchEmailVerificationService.kt:364`（规范化）、`:234-262`（200+state+email 匹配才 PASS）；`ManualInitialOutreachService.kt:843-852`（SMTP 前 `normalizeVerificationEmail(mail.to)` 断言） | `+tag` 保留、点号不合并；编码见 `EmailableRequestFactory.verifyUri`（`+`→`%2B`）；地址变化 → 停止本次执行、明细 NOT_SENT/`EMAIL_CHANGED` |
| I-3 副作用晚于验证 | `ManualInitialOutreachService.kt:683-748`（类型/抑制/已绑定之后、`selectSendAccount` 之前）；`:755,764`（选号失败收尾） | 非通过/取消目标不建 contact、不写 PREPARED、不调 SMTP、不计账号发送量 |
| I-4 服务异常≠邮箱异常 | `BatchEmailVerificationService.kt:200-232`（249 重试/分类）、`:352-361`（错误码）；`ManualInitialOutreachService.kt:727-736`（ERROR → 停止，`PARTIAL_SUCCESS`/`FAILED`） | 249 最多 2 次；402/401/403/429/超时/5xx/非法 JSON 或 state/邮箱不匹配 → ERROR；ERROR 不追加「邮箱异常」；`remaining` 不被改写成跳过（`annotateTerminalRemaining` 不含新码） |
| I-5 标签追加与分层一致 | `BatchEmailVerificationService.kt:269-299` | `_mget` 真实 `_id`，只写 ID/ORCID/当前邮箱三者匹配的已存在副本；缺失真实 ID → `FAILED/MISSING_DOC_ID`（绝不用 ORCID 冒充）；无匹配/读取异常/任一写失败 → FAILED；deliverable 不写标签 |
| I-6 审计先落库、可追溯 | 仓储 `insertPending/recordDecision/recordTag/recordSend/markSending`（`BatchEmailVerificationRepository.kt:29,57,79,83,91`）；引擎逐分支收尾（`ManualInitialOutreachService.kt:804,822,835,850,861,891,918,936,951,960,979,993,1001`） | 先插 PENDING → 结论 → SMTP 前条件预占 SENDING（必须 1 行）→ SENT/FAILED；审计失败 `EmailVerificationAuditException`（`:544`）先于广义 catch（`:998`），不进入 pauseAccount/SMTP 统计；已发后审计失败保留成功计数、行留 SENDING |
| I-7 成本与统计边界 | `BatchEmailVerificationService.kt:443-453`（≥100ms 间隔 / 500ms 重试）、`:186-192`（同执行同邮箱复用、requestCount=0）；引擎 `:712-726`（跳过计 skipped+processed+roundProcessed/roundRejected 并占 `roundSent` 槽） | 只验证实际进入门禁的目标；预估人数不验证；服务异常不缓存 |
| I-8 密钥与日志安全 | `BatchEmailVerificationService.kt:63-70`（空/test_ 拒绝）、`:479-507`（固定 `https://api.emailable.com/v1/verify`、Bearer 头、百分号编码）、`:511-538`（连接 3s/请求 12s、禁止重定向） | 密钥只进 Authorization，不进 URL/快照/日志/DB；不保存完整请求/响应；原始 reason 只作数据（截断入库） |
| I-9 审计存储与保留 | `V138__create_batch_email_verification.sql:37-63`；`BatchEmailVerificationRepository.kt:95-121`（游标分页 + 单 readOnly 事务内组合汇总，默认 50/最大 100/hasMore=limit+1） | 不改 `task_execution`/`task_progress_log` schema；FK `ON DELETE CASCADE` 跟随现有保留期；查询一律带 executionId |

下游接口（c2/c3 依赖）逐项落地：快照布尔字段；表 18 列列名/语义（V138 注释逐列写明）；`listAfter/aggregate/readPage`；`EMAIL_VERIFICATION_REJECTED` 与 7 个 `EMAIL_VERIFY_*` 码字面一致；`decision/send_status/tag_status` 取值集合与计划表一致（`BatchEmailVerificationRepository.kt:324-359`）。

## 3. 命令结果（本 child 内 fresh 运行）

| # | 命令 | 结果 | 计数 |
|---|---|---|---|
| 1 | `JAVA_HOME=<zulu-11> mvn -o test -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest` | **PASS**（exit 0，BUILD SUCCESS） | 178 tests / 0 failures / 0 errors（25 + 131 + 22）；另 JS 用例 1152 全绿（exec-maven-plugin 绑定） |
| 2 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=<zulu-11> mvn -o test -Dtest=BatchEmailVerificationRepositoryIT,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -Dapi.version=1.40` | **PASS**（exit 0，BUILD SUCCESS） | 37 tests / 0 failures / 0 errors（`FlywayMigrationIntegrationTest` 29 + `BatchEmailVerificationRepositoryIT` 8）；日志含 `Successfully applied 137 migrations ... now at version v138` |

命令 1 期间为让 Kotlin 非空参数与 Mockito 4 语义一致所做的测试侧修正（`anyValue`/`eqValue`/`captureValue`、`intThat` 改为显式 id 断言）不影响生产代码。

### 命令 2 最终重跑（本 child 最终状态）

- `FlywayMigrationIntegrationTest`：29 tests / 0 failures / 0 errors —— brief 记录的基线红（19 failures，`expected: <136> but was: <137>`）已按计划消除；显式历史 target 断言（135/133/130/131/116/23/24/36/121）逐字保留。
- `BatchEmailVerificationRepositoryIT`：8 tests / 0 failures / 0 errors —— 缺省值/唯一键/严格比较/游标分页/汇总/级联清理/超长拒绝全部通过。
- 合计 37 tests / 0 failures / 0 errors，exit 0，BUILD SUCCESS。

### 命令 2 首次运行（同一命令、同一生产代码）

- `FlywayMigrationIntegrationTest`：**19 处 latest 断言（136→138）全部转绿**；新增 V138 契约测试通过。
- `BatchEmailVerificationRepositoryIT`：8 例中 1 例失败 —— `read page ...:267 expected: <100> but was: <60>`（我的断言写错：该执行只有 60 行，不可能返回 100 行）。已改为「补足 120 行后再断言上限 100 + hasMore」。
- 其余 7 例（缺省值/唯一键/严格比较/游标分页/汇总/级联清理/超长拒绝）通过；日志可见 `Successfully applied 137 migrations ... now at version v138`。

## 4. 与计划的偏差

1. **新增受控 send_reason / stopReason `EMAIL_CHANGED`**（`ManualInitialOutreachService.kt:69,850-851`）：计划要求「SMTP 前地址变化则终止该次执行」但未给出该分支的码。按其「既有跳过/失败码或 CANCELLED/ACCOUNT_UNAVAILABLE/RESULT_UNCONFIRMED」的开放列表新增一个受控码，语义单一、可追溯。
2. **停止原因码 `EMAIL_VERIFY_AUDIT_FAILED` / `EMAIL_VERIFY_SEND_STATE_CONFLICT`**（`:63,66`）：分别对应 I-6 的「审计不可用则停止发送」与「其它结果停止并报告重复/状态冲突」。二者不是供应商错误码，故不复用 7 个 `EMAIL_VERIFY_*` 错误码，而是独立的停止原因码（明细 `error_code` 列仍只写计划规定的 7 个码）。
3. **`FlywayMigrationIntegrationTest` 测试方法名**：`fresh database migrates through V136` → `fresh database migrates through the latest version`（断言 136→138 后原名字失真）。其余 18 处只改断言值，显式历史 target 断言（135/133/130/131/116/23/24/36/121 等）逐字保留。
4. **取消检查的开关门控**：引擎新增的两处中途取消检查（验证前、SMTP 前）都以 `emailVerificationEnabled` 为前置，保证关闭时发送循环的取消语义与原来逐字一致（原来只在轮次开头检查）。
5. **宽泛 catch 的 SENDING 语义**：`mailDeliveryService.send` 抛异常（结果不明）时保持 SENDING、维持原有失败计数与继续循环（不额外停止）；「停止本次执行」只用于审计不可用/状态冲突/服务故障分支。理由是计划未授权改写既有 SMTP 异常路径的循环语义。
6. **未加 CHECK 约束**：V138 严格按计划表的列/缺省/键清单实现，未额外加 `decision/send_status/tag_status` 的 CHECK（计划 DDL 未列出；加约束会让后续阶段新增取值需要迁移）。取值集合由 Kotlin 常量与测试守住。

## 5. 剩余风险 / 未覆盖

- **真实 Emailable 契约未联调**：HTTP 语义按计划矩阵实现（249/402/401/403/429/5xx/超时/非法 JSON/邮箱不匹配），但未调用真实收费接口；真实响应字段若与假设（`state`/`reason`/`email`）不同，会 fail-closed 记 `EMAIL_VERIFY_BAD_RESPONSE` 而不放行。
- **`EMAILABLE_API_KEY` 读取方式**：经 Spring `@Value("${EMAILABLE_API_KEY:}")` 从后端环境读取（JVM 系统属性同样可见）。密钥不出现在任何快照/接口/日志/DB。
- **标签写入无事务隔离**：ES `_mget` 与 `_update` 之间文档可能变化，计划已明确不声称跨读事务隔离；层不匹配只记 `tag_status=FAILED`，邮件仍跳过。
- **`manual-executions` API 直接放行新字段**：c1 未改控制器（不在白名单）；`BatchSendControlService.validateSnapshotFields` 不白名单字段，故请求体带 `emailVerificationEnabled` 即生效（计划「通过现有手动快照 API 显式开启」）。配置贯通属 c2。
- **`FlywayMigrationIntegrationTest` 其他断言未动**：命令 2 结果用于确认无遗留红。

## 6. 提交

- 实现提交：`0965a037d94e198f6ce8b13900149a09d35683b4` — `feat(fast-p): implement c1`（父提交 `f64e9f6e9a1e5220d9267d9cde97127ae456df74`，恰 1 个提交，位于 `fast/2026-09-24-emailable-pre-send-verification`；仅上述 10 个业务/测试/迁移文件，不含 `docs/plans/fast/**` 证据）。
- 本报告由控制方单独提交（当前为未跟踪文件）。

## 7. 追加记录

- 命令 2 最终重跑：`FlywayMigrationIntegrationTest` 29/0/0 + `BatchEmailVerificationRepositoryIT` 8/0/0 = 37 tests / 0 failures / 0 errors，BUILD SUCCESS。两条必需命令均为本 child 最终状态下的 fresh 运行，全部通过；无已知在范围内的失败。
