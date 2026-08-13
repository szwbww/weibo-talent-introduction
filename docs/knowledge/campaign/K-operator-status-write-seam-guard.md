---
id: K-operator-status-write-seam-guard
domain: campaign
created: 2026-08-13
last_used: 2026-08-13
hit_count: 0
source: execute-p:02-single-writer-guard-test
---
经验：`expert_contact.operator_status` 的**唯一 DB 写入口**由守卫测试机器强制，不再是口头约定——
`OperatorStatusWriteSeamGuardTest`（`src/test/kotlin/com/weibo/talentintroduction/campaign/`）递归扫描
`src/main/kotlin` 全部 `.kt`，断言对 `operatorStatus` 的赋值位置集合**恰好等于**白名单
`{ExpertOperatorStatusService.kt, ManualInitialOutreachService.kt}`（T-2 `ALLOWED_WRITE_SITES`），
多一个少一个都失败（I-1 白名单闭包）。任何人在白名单外新增 `copy(operatorStatus = ...)` /
构造命名参数 / SQL `operator_status` 写入，`mvn test` 即失败并指出违规 `file:line` 与整改指引（I-2 显式变更）。

规则：
- 新增合法写入点：**必须**同时把文件登记进 `ALLOWED_WRITE_SITES` 并在一行中文注释中写明理由；
  或复用白名单内唯一写入口（`ExpertOperatorStatusService.changeStatus` 人工 / `updateAutomatically` 自动；
  建行初始化 NOT_CONTACTED 与退信 EMAIL_INVALID 两个例外在 `ManualInitialOutreachService`）。
- 误报处理：非写入命中按 `EXCLUDED_NOISE_SITES` 显式 path:line:context 排除，不用模糊启发式；
  排除项失效（行移位/上下文变化）会让该行重新被判违规（宁可误报、不放过），须同步更新排除名单。
- 已知非写入噪声：7 处 DTO 构造命名参数（UnmatchedInboundMailController:203/1097、
  MailboxService:165、ExpertContactManagementController:549、ExpertIndexController:85/410、
  ExpertSearchService:332）、ES 侧脚本 `ExpertIndexWriterService:84`（`ctx._source.operatorStatus = ...`，
  ES 写入口由 P-B 守卫，本守卫 out of scope）、SQL 只读上下文（ExpertContactRepository:47 WHERE 比较、
  MailRecordRepository:537/585 SELECT 投影与 GROUP BY）、注释行（ManualOutreachTxHelper:49 等）。
- 守卫只扫源码文本（不用 ArchUnit——对 Kotlin data class `.copy()` 合成方法的参数名捕获不精确）；
  相对路径读取工程文件，机制与 QaRuleManagementServiceTest 读 migration 一致。运行：`mvn test` 全量即含守卫。

关联：K-operator-status-single-writer（自动写出口收敛与 I-1 单调性）、K-expert-contact-two-write-sites（建行构造点）。
