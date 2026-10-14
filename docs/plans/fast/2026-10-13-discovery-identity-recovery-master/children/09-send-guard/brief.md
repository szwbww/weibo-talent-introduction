# Child Brief — 09-send-guard（人物级首发原子保护）

- Child ID: `09-send-guard`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-person-09-send-guard.md`（此 worktree 内已提交；含 **A14** 修订；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（I-7/I-8）。
- child_base_sha: `4ee251f7c90bea7cf556c5536f8bb522c5cebc09`（08-selection 的 Code head，已 LIGHT_PASS_WITH_NOTES 验证）。
- 起始工作树 HEAD（开始前必须核对且 `git status` 干净）: 以 dispatch 消息给出的 `docs(fast-p): prepare child 09-send-guard` 提交 SHA 为准（含前序 fast-p 记账提交 docs/plans/fast/**，属 controller 记账，不得回退、不得纳入你的提交）。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 前置已完成（可直接引用）
- 08-selection：`DiscoveryPersonRecipientService` 选择结果 `{personId, selectedDocId, selectedEmail, memberDocIds, exclusionReasons}`；`OutreachTargetIterator` 支持调用者人物去重键；预估/执行/重试同源。
- 04-registry：人物关系读查询（成员/revision/指纹）。
- 当前最大迁移 = V156（已核对），V157 空闲。

## Authorized files（精确 11 个；如需第 12 个文件先停止并返回 PLAN_CONFLICT）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V157__create_discovery_person_outreach.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 在 08 后修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 在 08 后修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 修改 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt` | 修改 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt` | 新增 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 在 08 后修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 在 08 后修改 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` | 修改 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | A14：31 处最新版本断言 156→157 + V157 表存在/唯一键断言 |

## Required commands（JDK 11 + Docker 前缀；记录退出码与精确计数）
- C1（含真库并发守卫测试）：`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test`
- C2（V157 真迁移，A14 后）：`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`
- 每次 `mvn test` 自动执行 JS 门禁；失败数必须为 0（基线 1677/1677/0）。Docker 前缀如需微调，采用可运行形式并记录实际命令。

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1 所有介绍邮件入口同一原子门禁：新增 `discovery_person_outreach` 唯一键 `(person_id, mail_type)`（mail_type 本期仅 INTRODUCTION）；不以 campaign/account/email 切开首发唯一性；旧定时、新批量含重试、详情手动模板发送在**实际 mailType=INTRODUCTION 且目标为 discovered** 时使用同一保护；人物映射未就绪/版本变化先阻断；范围外保持原路径。
- I-2 发送状态保守推进：`RESERVED`（未交付）、`SENDING`（已取得唯一交付权）、`SENT`、`UNKNOWN`、`SAFE_RETRY`；以事务插入和 expected revision/owner token CAS 获得权限；网络请求前持久化 SENDING；SMTP 成功但后续落库失败、异常且无法证明未交付、进程在 SENDING 中断均保持不可重发并归 UNKNOWN；只在明确未调用 SMTP 或明确未接受时允许 SAFE_RETRY；不得因锁过期、超时或换邮箱重新取得首发许可；SAFE_RETRY 仍绑定原 recipient。
- I-3 历史保护及邮箱资格不可绕过：占位前只读检查全部成员的原联系人、邮箱别名、`mail_record`/`mail_send_attempt`、人工准入和抑制；已有成功或不确定发送不再首发；人工拒绝/暂停/明确退出不能靠换邮箱绕过；选址来自 08，但发送前重新核验当前标签、人物 revision、具体邮箱资格和状态；原历史 ID/内容/地址不重写。
- 实现步骤（详见计划 1-4）：①V157 新表（字段/复合主键按计划）+ `DiscoveryPersonOutreachGuardService`（内聚 JDBC 条件更新与历史只读检查；依赖数据库锁/唯一键，不用 JVM synchronized；锁期间复核成员 revision；发送前重读 ES/准入）；②三入口接入：旧/新首发在建 contact 或改 attempt 前 reserve；完成所有原门禁后 CAS 到 SENDING 才 send；详情手动发送仅对实际 INTRODUCTION 检查，其余类型原样；未获许可返回可解释结果；③结果与中断：`ManualOutreachTxHelper` 在原成功事务联动 SENT；详情自己成功事务等效处理；无法同时完成不释放占位（UNKNOWN 待核）；占位后取消且证明未交付可标 SAFE_RETRY；SENDING 不许定时超时解锁；回滚也保留保护；④四份定向测试：真库并发两 worker/不同入口、假 SMTP 记调用数；成功后 DB 失败、异常、取消、明确未交付重试；非 INTRODUCTION、范围外与原配额行为。
- 边界：不改既有迁移；不改 contact/mail 表结构；无前端；无生产发送；`docs/plans/fast/**` 不得进入你的提交。

## 下游接口（10 依赖）
- `DiscoveryPersonOutreachGuardService`：10 通过同一服务导入历史禁止状态（已有成功/未知首发保护），不另建写路径。
- 三入口的 reserve→SENDING→SENT/UNKNOWN 语义与失败结果可解释性（10 的对账沿用）。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/09-send-guard/execution.md`（逐命令退出码/计数，实际 Docker 前缀，改动摘要，未决问题）。
- 本地提交仅含 11 个授权文件：`feat(fast-p): implement 09-send-guard`。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。
