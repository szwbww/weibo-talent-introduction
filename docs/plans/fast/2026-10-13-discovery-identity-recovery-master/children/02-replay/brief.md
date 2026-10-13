# Child Brief — 02-replay（旧名单回源预览、受控恢复与留痕）

- Child ID: `02-replay`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-identity-recovery-02-replay.md`（此 worktree 内已提交于 b06a718；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（含 A1—A9 修订）。注意 A1 范围收窄：10,686 旧名单仅为参考背景，**本次不启动其存量执行**；回源能力只能服务本次 discovered 范围；不运行生产 apply。
- child_base_sha: `bc294d2e991e58346ce456f4e5eeb7525a2b5771`（前序子计划 01-duplicate 的 Code head，已 LIGHT_PASS_WITH_NOTES 验证）。
- 起始工作树 HEAD（开始前必须核对且 `git status` 干净）: `0aba5e5e9e1834ab6eb3c5268e6c208810c1b657`（含前序 fast-p 记账提交 docs/plans/fast/**，属 controller 记账，不得回退、不得纳入你的提交）。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 前置已完成（可直接引用，勿重复实现）
- admission-relaxation：准入政策软化/版本 2026-10-13.1、重验软硬分离、CAS 复读。
- 01-duplicate：`DiscoveryIdentityRepairService`（`repairDuplicateIdentity`，结果枚举 REPAIRED/ALREADY_VERIFIED/MISSING_NAME_NEEDS_RECOVERY/CONFLICT/PARTIAL/FAILED）、`ExpertIndexWriterService.findDiscoveryHitsByEmail` / `writeIdentityProofIfVersionMatches`、`IDENTITY_REPAIR_FAILED` 传播。
- 本计划在其基础上：RecoveryService 走「服务端重新取证 → 预览 → apply CAS」；扩展 repair service/writer 支持受限「只填空姓名 + proof」模式（自动增量入口不得启用此模式）。

## Authorized files（精确 10 个；如需第 11 个文件先停止并返回 PLAN_CONFLICT）

| # | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt` | 新增：来源复查/预览/apply 及 DTO |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryController.kt` | 新增：登录及参数保护 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt` | 补空身份的受限模式 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | 条件更新空姓名与 proof |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 精确来源读取及复用解析 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt` | 新增：跨路径恢复/冲突/旧学术保护/条件写入 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt` | 新增：认证/校验/hash 变化 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 精确查询/额度/来源校验 |
| 9 | `tools/discovery-identity-recovery/replay.py` | 新增：名单与本地留痕、应用调度、回滚提案 |
| 10 | `tools/discovery-identity-recovery/test_replay.py` | 新增：默认预览/日志恢复/备份失败保护 |

## Required commands（JDK 11 前缀不可省略；记录退出码与精确计数）
- C1: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest test`
- C2（01 四项既有，仅执行、不得修改）: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test`
- C3（Python）：从仓库根运行 `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'`；若发现机制不适用，改用可运行且可复现的等价形式（如 `python3 tools/discovery-identity-recovery/test_replay.py`），并把**实际使用的精确命令**记录进报告。
- 每次 `mvn test` 自动执行 JS 门禁；失败数必须为 0（基线 1657/1657/0）。

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1 重新取证、旧摘要不当证明：仅接受真实 docId；来源 ID 从服务端存量 externalIds 读取；PMCID 走公开 JATS、DOI/PMID 走精确元数据；再用现有提取器取邮箱归属；无精确来源标识报 `SOURCE_LOCATOR_MISSING`，不按姓名搜索拼接；调用方不能提交 proof/作者名/任意下载 URL/模拟原文；旧 `extraction_json` 不改版本重用。
- I-2 有限应用范围：同姓名缺证走 01；空姓名仅在「原来源唯一确认 + 已有非空姓名部分相符 + 三层无身份/作者 ID 冲突 + 无 existing proof + 无人工/历史准入决定 + 无 APPLICATION + 联系人按 docId/orcidId/email 均未命中 + 无旧学术字段」时才补空字段及 proof；已填姓名从不覆盖；否则仅建议人工处理。学术字段检查包括 `hIndex/citationCount/worksCount/researchFields/researchFieldIds/recentWorkTitles/patentTitles/disciplineCategory/enrichedAt`。姓名补齐会改变 admission identity_hash —— 必须按既有规则重算，不能沿用旧人工决定。
- I-3 预览不写专家、应用复核来源及并发：每次 API 只处理 1 个 docId；preview 对 ES/准入/联系人/邮件零写（可产生既有请求额度台账）；返回 `snapshotHash`、`proposalHash`、规则版本及逐层摘要；apply 只接受 docId + 两项 hash，服务端重新取证重算，不同则 409；再以各层版本 CAS 应用；部分写入回报 PARTIAL、不刷新自动准入、不算完整成功；重试按「前值或本次目标值」确认，遇第三种值停止；不跨层宣称事务；补证后准入重验失败单列、可重跑。
- I-4 来源、预算与报告守恒：OpenAlex 必须走既有 `getJson/requestPolicy`，声明既有 `RequestKind.DISCOVERY + Operation.SINGLETON`；共用既有每篇时限、并发许可与地址数；429/预算延期记 `DEFERRED`，不算不合格；仅公开来源、不解验证码。脚本每人一行 JSONL：docId、观察时间、来源 ID/URL、元数据摘要、提取版本、proof source/hash、前后差异、各层结果、准入结果、失败原因；输出目录 0700、文件 0600、文件名不含姓名邮箱、不记录认证头；报告失败则该条停止进入 apply。
- 队列与写入边界：V133 job 的 payload/extraction 由既有链路写入，本份不改队列；不直接 REST 覆盖整文档；唯一事实写入口仍是 repair service + writer。
- 边界：无前端；无新增 DB/ES 字段；无迁移；禁止生产 apply/SMTP/发信；`docs/plans/fast/**` 不得进入你的提交。
- 特别要求：`DiscoveryIdentityRecoveryServiceTest` 必须通过**真实 writer + HTTP 替身**覆盖新增条件写入，不能 mock writer 后宣称 ES 补丁已验收；样本原始证据引用既有审计资源，需新增大夹具时先拆计划，不临时加第 11 个文件。

## 下游接口（05/10 依赖，保持稳定）
- `DiscoveryIdentityRecoveryService`：预览/apply 服务端取证的入口与结果类型（供 10 的 discovered 子名单复用；05 将在其上做统一登记）。
- `DiscoveryIdentityRepairService` 的受限「空姓名 + proof」模式：自动增量入口不启用（05 只调用原补证路径）。
- `OpenAlexDataSource`：精确 DOI/PMID 读取方法 + 复用 `parseWork`（不得复制作者解析规则）。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/02-replay/execution.md`（逐命令退出码/计数，含 C3 实际命令，改动摘要，未决问题）。
- 本地提交仅含 10 个授权文件：`feat(fast-p): implement 02-replay`。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。
