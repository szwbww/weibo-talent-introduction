# Fast-P Child Brief — 01（新发现机构来源提取修复）

## 身份与边界

- Master plan（批准版，字节冻结，只读）：`docs/plans/2026-09-28/discovered-institution-repair-00-master.md`，identity `commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7`。
- 本 child 批准计划（**完整合同，必须先通读**）：`docs/plans/2026-09-28/discovered-institution-repair-01-source.md`，identity `commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7`。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`；Branch：`fast/2026-09-28-discovered-institution-repair-00-master`。
- `child_base_sha`：`2e9639df7947bc5f3057ca08e1445b155aba7cd7`（seed 提交；产品代码与 master base `d90084841d400e75eb0f2b6c4c6726e54307260a` 相同，只多 `docs/plans/**`）。
- 依赖：none。下游 child（01b/02/03）依赖本 child 的产物：内部 `institutionSource` 语义、`AffiliationDecision`/唯一机构判定、`employment=null`、类型不跨源覆盖。

## 全局约束

1. JDK 11 固定：所有 Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改下面「Authorized Files」表内 10 个文件；不得新建白名单外文件（测试文件全部已存在）；不得修改 `docs/plans/**`（计划与 fast-p 证据由控制方提交）。
3. 不得修改主工作区 `/Users/lukai/IdeaProjects/weibo-talent-introduction`；不得 push、merge、rebase、squash、amend、reset。
4. 产品代码提交格式：`feat(fast-p): implement 01`，单个提交；把 `docs/plans/fast/**` 报告/日志排除在该提交之外（报告留在工作树由控制方提交）。
5. 计划与代码冲突、需要白名单外文件、需要新行为、需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不要自行扩范围或改计划。
6. 只读参考（不得复制、不得提交）：主工作区下 `docs/audits/2026-09-28-discovered-institution-comprehensive/README.md`。

## 必须保持不变（计划「需求描述」「关键不变量」「验收标准」）

- 不得改变邮箱—作者绑定规则（`DiscoveryIdentity` 身份门禁、唯一邮箱作者判定）、ORCID/SBIR 来源记录、非新发现专家档案。
- 范围外：历史 ES 修正、发送策略、多机构「主机构」猜测。
- I-1 作者—机构同源：JATS 只接受该 `<contrib>` 直接 `<aff>` 或唯一 `xref rid` 指向的 `<aff>` 中，`content-type=university|edu` 的唯一非空 `<institution>` 作为组织名；其他 `content-type` 和无类型标签只留在原始 `affiliation`。OpenAlex 只接受同一 `authorship` 中唯一非空 `institutions` 对象。多个不同机构、无结构节点、无法唯一绑定作者 → 一律 null，不选第一家。
- I-2 字段语义：`affiliation` 保留原文线索；内部 `institutionSource` 仅由实际成功的 JATS/OpenAlex 结构解析写 `JATS`/`OPENALEX`，不得由 `dataSource` 猜测；`institution` 只放该结构机构名；`country` 只放同一机构明确且 `CountryContinentMapping` 可识别的值，否则 null；论文发现 `employment=null`；删除 `inferCountryFromAffiliation` 的使用，不得按逗号末段、邮箱域名、地址猜国籍/职位。
- I-3 类型绑定：`institutionType` 只能与所显示 `institution` 的同一个来源机构对象配对；`last_known_institutions[0].type` 不得覆盖论文 `authorship.institutions` 的类型；类型未知则 null。旧测试里原先断言「覆盖」的用例同步改为「不覆盖」。
- PMC/JATS→OpenAlex 补机构只允许：已存在的唯一 ORCID 作者绑定且 JATS 未取得机构时取同一作者 OpenAlex 单机构；无绑定或两源矛盾保持 null。
- 计划要求 TDD：先写失败断言（记录改前红证据），再实现到通过；不得为了变绿改旧业务期望（仅 I-3 明确要求的「覆盖→不覆盖」断言反转除外）。

## Authorized Files（10）

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/AuthorEmail.kt` | 内部来源字段 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperAuthor.kt` | 内部来源字段 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt` | JATS 精确提取 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 同一 authorship 唯一机构 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` | 强绑定字段传播 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 建档与异源覆盖修正 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt` | JATS 反例（含真实 `PMC13138287` 的两个 `aff`） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 多机构/类型/国家 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 作者绑定传播 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | ES 写值/补全不覆盖 |

## 已知基线事实

- 基线命令与逐类计数见 `docs/plans/fast/2026-09-28-discovered-institution-repair-00-master/children/01/baseline.md`（控制方在 seed 提交上 fresh 运行，记录 exit code 与计数）。测试若在基线上已红，按基线对比归类，不得把预置红算成新引入失败。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=JatsXmlEmailParserTest,OpenAlexDataSourceTest,SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

## 交付

- 按计划实现 1→2→3；`ExpertDiscoveryService.buildProfile` 改用新字段，`updateExpertAcademicFields` 不再写异源 `institutionType`。
- 执行报告写入 `docs/plans/fast/2026-09-28-discovered-institution-repair-00-master/children/01/execution.md`（报告本身不进实现提交）：逐子任务的文件:行证据、改前红证据、命令与 exit code/计数、与基线对比、计划要求但未做到的显式偏差。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复白名单外问题、不得重构相邻代码、不得为计划之外的目标做改动。
