# Child Brief — 05-ingestion（后续发现、重复发现与补证统一归人）

- Child ID: `05-ingestion`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-person-05-ingestion.md`（此 worktree 内已提交于 36075ec；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（I-4/I-5/I-8；范围仅限 discovered）。
- child_base_sha: `de4c1a042a48ec139c95f659858e78746b6208a6`（04-registry 的 Code head，已 LIGHT_PASS_WITH_NOTES 验证）。
- 起始工作树 HEAD（开始前必须核对且 `git status` 干净）: 以 dispatch 消息给出的 `docs(fast-p): prepare child 05-ingestion` 提交 SHA 为准（含前序 fast-p 记账提交 docs/plans/fast/**，属 controller 记账，不得回退、不得纳入你的提交）。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 前置已完成（可直接引用，勿重复实现）
- 04-registry（已验证）：`DiscoveryPersonService.register(DiscoveryPersonSnapshot): DiscoveryPersonRegistrationResult`（结果 `CREATED/ATTACHED/UNCHANGED/NEEDS_REVIEW/SCOPE_CONFLICT/STALE`；后三者不写关系）；读查询 `findPersonIdsByDocIds`、`findMembersByPerson`、`findAuthorKeysByPerson`、`findPerson`（revision）、`findMember`（fingerprint）；V156 三表为唯一人物存储。
- 01-duplicate（已验证）：`DiscoveryIdentityRepairService.repairDuplicateIdentity(docId, incoming)` + `ExpertIndexWriterService.findDiscoveryHitsByEmail` / `writeIdentityProofIfVersionMatches`。
- 02-replay（已验证）：`DiscoveryIdentityRecoveryService.preview/apply` 与服务端取证链路。
- 本计划是在这些产物之上接线：所有成功出口在事实持久化且范围有效后统一调用 04 登记。

## Authorized files（精确 8 个；如需第 9 个文件先停止并返回 PLAN_CONFLICT）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 修改 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt` | 修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt` | 在原 01 产物上修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt` | 在原 02 产物上修改 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 修改 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt` | 修改 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt` | 在原 01 产物上修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt` | 在原 02 产物上修改 |

## Required commands（JDK 11 前缀不可省略；记录退出码与精确计数）
- C1：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test`
- C2（04 回归，真库 Testcontainers）：`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test`
- 每次 `mvn test` 自动执行 JS 门禁；失败数必须为 0（基线 1657/1657/0）。C2 的 Docker 前缀如需微调，采用可运行形式并记录实际命令。

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1 每条成功出口都登记：同步 ORCID、论文消费、持久队列、相同邮箱重复、01 补证及 02 恢复——在事实成功持久化且范围有效后调用 04；**不得在重复分支提前返回而跳过归人**；登记参数从当前持久化事实构造，不从无凭证 externalIds 猜身份；ORCID 与论文队列共用登记函数；原始邮箱事实可保留多条 ES 技术记录，用户专家数由人物主档决定。
- I-2 双存储中断可恢复：先保留邮箱 ES 事实→再登记 SQL 关系→最后完成该发现结果；登记失败可幂等重试（从既有 ES 事实继续，不重置邮箱证明）；映射未就绪的 discovered 不进入首发；事实/指纹变化返回 `STALE` 而非借旧关系通过；不能靠清空队列解决；"ES 成功、SQL 失败"的恢复由 10 的维护重登记扫描承担（本计划禁止在预估时补写）。
- I-3 单位清楚、预算不变：分别统计新增人物、追加邮箱、重复事实、需复核、登记失败；原邮件事实条数不改叫人数；`maxAuthors`/队列批大小维持原有工作量限制；禁止姓名猜测、凭证伪造与其他标签写入。
- 实现步骤（详见计划实现方案 1-4）：①新建/重复统一出口接入 04（`consumeOutcomeInternal` 新建与 duplicate 两出口、`consumeQueuedItem`、`recordIdentityDuplicate`、同步 ORCID 循环）；②补证后刷新（修改两个 recovery service：仅对实时 discovered 成员在各层事实补齐成功后刷新人物登记；跨层部分失败先留失败，不抢先合并；身份指纹变化须先复验完整组，冲突保持独立并记录原因）；③传播与统计（DiscoveryPipelineService 把可重试登记异常纳入未完成结果；语义冲突记需复核、不无限重试）；④回归（四份定向测试覆盖原多邮箱 HTML 夹具与 same-ORCID 两邮箱场景：期待原邮箱证据 2 份、人物 1 位，而不是删掉第二邮箱；增加注册前/后进程中断、重复任务、来源冲突和暂停预算断言）。
- 边界：无迁移、无新 DB/ES 字段；其他 ES 标签/运营/学术写入不在本计划改造；非发现 `backfillRawEmailsAndPromote` 不纳入；无生产动作；`docs/plans/fast/**` 不得进入你的提交。

## 下游接口（06/08/09/10 依赖，保持稳定）
- 统一登记函数与调用点位置（ExpertDiscoveryService 内、repair/recovery 复用）；`DiscoveryPipelineService` 对可重试登记失败与语义冲突的传播/重试语义（10 的维护重登记扫描沿用同一 service，不另建路径）。
- 计数口径（`ConsumeOutcomeResult`/`QueuedItemConsumption` 等）单位语义保持清晰（06/08 读人物关系不重算人数）。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/05-ingestion/execution.md`（逐命令退出码/计数，改动摘要，未决问题）。
- 本地提交仅含 8 个授权文件：`feat(fast-p): implement 05-ingestion`。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。
