# Fast-P Child Brief — c1（后端：配置、快照、严格选号与绑定跳过）

- Child ID: `c1`
- 权威子计划：`docs/plans/2026-09-23/01-batch-sender-filter-backend.md`（计划身份 `commit:a58ce98`，即本 worktree 当前内容）
- 总计划：`docs/plans/2026-09-23/00-batch-sender-filter-main.md`（身份 `commit:a58ce98`；人工批准的改写见 ledger `## Amendments` A1–A6）
- **迁移号（A4–A6 人工批准）**：`V135__add_batch_sender_account_codes.sql`。V134 已被并行 run 的 `V134__shared_inbox_owner.sql` 占用（该分支已实施并轻量验证），Flyway 版本是共享命名空间，不得重号；Flyway「最新版本」断言目标 = `135`（本分支允许 V134 缺口），定点 target 断言不动。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-batch-sender-filter-main`
- Branch：`fast/2026-09-23-batch-sender-filter-main`
- `child_base_sha`：`377a38b`（= 计划播种 `c9babae` 之后的改写提交；产品代码等同 `main` @ `9237d6f`，两个前置提交只动 `docs/plans/`）
- 执行报告路径：`docs/plans/fast/2026-09-23-batch-sender-filter-main/children/c1/execution.md`
- 实现提交信息：`feat(fast-p): implement c1`

## 授权文件（只能是子计划 `## 变更文件清单` 的这 10 个，不得增删）

1. `src/main/resources/db/migration/V134__add_batch_sender_account_codes.sql`
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt`
7. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`

禁止：改计划文件、改 `docs/plans/fast/**`（fast-p 证据由控制器提交）、新建其他文件、改 CSS/前端、push/merge/rebase/amend。

## 必须保持的不变量（子计划 I-1～I-5，逐条以代码/测试证据交付）

- I-1：`sender_account_codes_json` 存逻辑 `account_code` 的 JSON 数组；`[]`=不限制；坏 JSON 必须拒绝启动/读取，不得降级为 `[]`；不按 `inbound_mailbox_code` 合并别名；`BatchExecutionSnapshot.senderAccountCodes` 是本次执行唯一范围快照。
- I-2：非空 code 列表 trim/去重/校验存在且非 `SIMULATOR_NOOP`；每轮 `listSendableAccounts`、自检、额度合计、`selectAccount` 候选都限制在快照集合内；选中账号禁用/暂停/满额时停发或跳过，绝不回退未选中账号；空列表保留旧选号逻辑（旧四参调用行为不变）。
- I-3：任何 `expert_contact.bound_sender_account_code != NULL` 的目标都不得发送/重选/改绑，与绑定值是否在选中集合无关；INTRODUCTION 覆盖 MySQL NEW 重试与 ES 新目标；MATERIAL_REMINDER 覆盖目标构造与发送前重读；同一 ORCID 在别的 campaign 有绑定也要排除。
- I-4：NEW 重试与 MATERIAL_REMINDER 的预估（`countBySnapshot`）与执行共用同一目标构造函数，绑定过滤同源；INTRODUCTION 的 ES `countExperts` 仍是候选估算，ES 已绑定目标在发送前以 `BOUND_SENDER_ALREADY_SET` 跳过并计入跳过数，不伪称精确。
- I-5：所有发件候选/绑定/日志只用逻辑 `accountCode`；共享同一物理 IMAP 的兄弟账号（如 LuKai_QF 与 LuKai）是两个独立 code，禁用者不得借 owner 的 enabled 状态外发。

## 下游接口（c2 前端依赖，必须逐字一致）

- 配置 API：`POST/PUT /api/mail/batch-send/configs`（及既有 create/update 路径）接受 `senderAccountCodes: string[]`（有序去重，`[]`=全部可发送账号）；`GET`/详情/列表回显同名字段与同值。
- 启动快照：`BatchExecutionSnapshot.senderAccountCodes` 同名字段；手动执行 POST `/api/mail/batch-send/manual-executions` 的快照携带它，未知 code 返回 422，不得被当作 `[]`。
- 预估：既有预估 endpoint 直接接收快照，快照里必须带 `senderAccountCodes`。
- 跳过原因：`BatchOutcomeReasonCodes.BOUND_SENDER_ALREADY_SET` 与其中文标签在跳过统计中可见。
- 旧 typed API：`/types/{sendType}/config` 的 `updateLegacyConfig` 必须保留既有 `senderAccountCodes`（不得重置为默认值）。

## 必需命令（实现后必须全部重跑并记录 exit code / 计数）

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
mvn -B -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,ManualInitialOutreachServiceTest test
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -B -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test
```

- JDK 必须是 zulu-11；OrbStack Docker 已确认可用（Server 29.4.0）。
- 基线结果（本 worktree `docs/plans/fast/2026-09-23-batch-sender-filter-main/baseline/`）：`js.txt`（`node --check` 0，`node --test src/test/js/*.test.js` 1121 pass / 0 fail）；`mvn.txt` 为基线的三个定向测试 + Flyway IT 记录（若基线中存在失败，实现报告必须列出「基线已失败」与「本次新增失败」的差异）。
- 未运行的命令必须在报告里写明「未运行 + 原因」，不得宣称通过。

## 操作约束

- 计划要求「测试先行」：先补测试再实现；测试只断言可观察契约（字段往返、白名单、跳过原因、外发账号集合），不写实现细节/行号断言。
- `FlywayMigrationIntegrationTest` 的普通「最新版本」断言当前钉 `131`，按工作树实际最高版本（V133→V134）重查后改到 `134`；定点 target 版本断言（如 `130`）保持原值。
- 不要触碰 `OperatorStatusWriteSeamGuardTest`：它钉死的是其他文件的噪音行号（`UnmatchedInboundMailController.kt`、`ExpertSearchService.kt`、`ExpertContactRepository.kt`、`MailRecordRepository.kt` 等），本 child 的授权文件不在其清单内；若该测试变红，先确认是否由本 child 的改动引起。
- 实现提交只包含这 10 个文件；`docs/plans/fast/**` 与 `docs/plans/2026-09-23/**` 一律排除。
