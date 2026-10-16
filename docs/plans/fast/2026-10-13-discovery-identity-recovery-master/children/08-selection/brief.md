# Child Brief — 08-selection（按人选择首发目标与预估）

- Child ID: `08-selection`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-person-08-selection.md`（此 worktree 内已提交于 36075ec；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（I-7/I-8）。
- child_base_sha: `2fae99a551ea656604c46a19f1f046eb3ad6593e`（07-ui 的 Code head，已 LIGHT_PASS 验证）。
- 起始工作树 HEAD（开始前必须核对且 `git status` 干净）: 以 dispatch 消息给出的 `docs(fast-p): prepare child 08-selection` 提交 SHA 为准（含前序 fast-p 记账提交 docs/plans/fast/**，属 controller 记账，不得回退、不得纳入你的提交）。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 前置已完成（可直接引用）
- 04-registry：人物关系与读查询（`findPersonIdsByDocIds`/`findMembersByPerson`/`findPerson` revision/`findMember` fingerprint）。
- 05-ingestion：发现/补证/恢复出口已登记；06-query：ES 列表与 DB 分支人物口径 + `memberCount`。
- 现有发送事实：`BatchRecipientSelectionService`（按 docId 判定 + admission）、V148 准入、邮箱验证/抑制、`ManualInitialOutreachService`/`InitialOutreachService` 两条首发链、`OutreachTargetIterator`。
- 本计划只做"按人选择"层；**人物级原子保护由 09 负责，本计划不得实现/替代它**。

## Authorized files（精确 10 个；如需第 11 个文件先停止并返回 PLAN_CONFLICT）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt` | 修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt` | 修改 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt` | 新增 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt` | 修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 修改 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt` | 修改 |

## Required commands（JDK 11 前缀不可省略；记录退出码与精确计数）
- C1：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test`
- 每次 `mvn test` 自动执行 JS 门禁；失败数必须为 0（基线 1677/1677/0）。

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1 先邮箱合格，再归人选址：逐成员先执行既有身份/准入/显式筛选/邮箱抑制与验证，再为一个 personId 选择一个地址；不能因为默认主邮箱不合格漏掉无其他限制的合格邮箱，也不能因一个邮箱合格放行其余地址；人工 HOLD/REJECTED、身份冲突、已联系/发送等人物保护按**整个有效组**检查，不能换成员绕过。
- I-2 跨页和重试同一人物集合：首次遇到人物时批量加载该人物当前成员，应用相同 scope 后确定唯一地址；仅完整判定后标记人物已处理；重试及新目标使用同一人集合，不在每页清空；安全重试沿用原尝试的邮箱，不自动换址；选址确定性：默认邮箱仅在合格时优先，否则按现有验证可信级别降序、docId 升序。
- I-3 同源只读预估：预估和执行基于同一快照/scope/人物选择函数；只读预估不建 campaign/contact、不建人物成员、不建首发占位；未就绪/失效关系单列原因且不计为可发；预估是该时点目标数（发送前变化仍可减少实际量），不能承诺发信数恒等。
- 实现步骤（详见计划 1-3）：①新增 `DiscoveryPersonRecipientService`（共享纯计算：批量获取成员/当前事实，调用原逐邮箱准入能力，输出 `{personId, selectedDocId, selectedEmail, memberDocIds, exclusionReasons}`；返回入选 docId 兼容下游，不重写原档案 orcidId；依赖方向：原邮箱 selector ← 人物 selector 调用，避免两服务互相注入）；②两条首发 service 的预估、目标、重试使用同一人物选择层；`OutreachTargetIterator` 支持调用者提供人物去重键（普通记录仍原 docId）；每轮缓存只存本次 scope 与人物 revision 下的判断，发送前由 09 重验，不跨任务缓存"允许发送"；`BatchRecipientSelectionService` 继续输出每邮箱判定并保留精确拒绝原因；③五份定向测试覆盖：同人三邮箱分散页、首地址不合格但第二合格、人工 HOLD 与已发保护、scope 不同、重试重复、新旧路径一致；断言预估 1 人、选中具体 1 地址、零副作用；材料提醒和范围外样本保持原结果。
- 边界：不改 ES/DB 字段、无迁移；不实现人物级原子保护（09）；不改变材料提醒与会话回复；范围外（非 discovered）行为保持；无生产发送；`docs/plans/fast/**` 不得进入你的提交。

## 下游接口（09 依赖，保持稳定）
- `DiscoveryPersonRecipientService` 的选择结果结构（`{personId, selectedDocId, selectedEmail, memberDocIds, exclusionReasons}`）与"预估/执行/重试同源"语义；09 在发送前重验该选择（人物 revision + 具体邮箱资格）。
- `OutreachTargetIterator` 的人物去重键扩展点（09 的占位前检查复用同一目标集合）。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/08-selection/execution.md`（逐命令退出码/计数，改动摘要，未决问题）。
- 本地提交仅含 10 个授权文件：`feat(fast-p): implement 08-selection`。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。
