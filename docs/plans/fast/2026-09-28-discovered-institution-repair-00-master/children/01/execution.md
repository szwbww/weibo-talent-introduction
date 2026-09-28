# Child 01 执行报告 — 新发现机构来源提取修复

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master/docs/plans/2026-09-28/discovered-institution-repair-01-source.md`
- Plan SHA-256: `c7300da110fb6064ea3a6630349de070f3520e62027638cc9531c0812efc02c4`
- Execution ID: `docs/plans/2026-09-28/discovered-institution-repair-01-source.md@c7300da110fb6064ea3a6630349de070f3520e62027638cc9531c0812efc02c4`
- Execution epoch: `NEW`
- Approval basis: 本次调用（brief `children/01/brief.md` sha256 `9b3c86787a02abe25185160843878caf33fd13ba2fa24cba76a57697ea1c6e79`；master `discovered-institution-repair-00-master.md` sha256 `2f7fdb23cdfa016795827416d9d43f73d19798457a9c32c20605ea3a42d404c6`）
- Executor: `Implement01-2`（fast-p child 01 worker）
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Target branch: `fast/2026-09-28-discovered-institution-repair-00-master`
- Worktree ID: `…-repair-00-master@fast/2026-09-28-discovered-institution-repair-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Pre-execution code SHA (child_base_sha): `2e9639df7947bc5f3057ca08e1445b155aba7cd7`
- Post-execution code SHA (= Evidence HEAD): `b12c971be46a992b275a3e4fb3768047b0af8877`
- Implementation boundary: `2e9639df7947bc5f3057ca08e1445b155aba7cd7..b12c971be46a992b275a3e4fb3768047b0af8877`（单提交，`feat(fast-p): implement 01`，仅 10 个 Authorized Files）
- 备注：execute-p 的 `scripts/plan_identity.py` / `scripts/worktree_identity.py` 在本机不存在（`~/.omp/skills` 为空，全盘 find 无结果），已用等价的 `shasum -a 256` + `git rev-parse --git-dir/--git-common-dir/--abbrev-ref HEAD` 记录身份；两次（执行前、提交前）取值一致。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 计划步骤 1：JATS 精确提取（`content-type=university\|edu` 唯一机构 + 同一 aff 唯一可识别国家 + `institutionSource=JATS`） | IMPLEMENTED | `JatsXmlEmailParser.kt`、`AuthorEmail.kt` | `JatsXmlEmailParser.kt:56-65`（作者关联 aff → `structuredInstitution`）、`:194-227`（白名单/唯一性/国家）、`:21-25`（`ORGANISATION_CONTENT_TYPES = {university, edu}`）；测试 5 个新增用例（见下） |
| 计划步骤 2：OpenAlex 同一 authorship 唯一机构 + PDF/HTML 分支唯一作者传播 + PMC/JATS→OpenAlex 仅唯一 ORCID 绑定且 JATS 无机构时补机构 | IMPLEMENTED | `OpenAlexDataSource.kt`、`SourceAuthorEmailResolver.kt`、`PaperAuthor.kt` | `OpenAlexDataSource.kt:284-299`（`soleInstitution` 唯一对象，name/type/country_code/来源同源）、`:317-320`（唯一非空机构；多机构 → null）、`:110-133`（XML 分支仅 ORCID 唯一绑定且 JATS 无机构时补）、`:218-236`（`boundOpenAlexInstitution` 要求来源标记且唯一）、`SourceAuthorEmailResolver.kt:165-169` |
| 计划步骤 3：`buildProfile` 改用新字段、论文 `employment=null`、删用 `inferCountryFromAffiliation`；`updateExpertAcademicFields` 不再写异源 `institutionType` | IMPLEMENTED | `ExpertDiscoveryService.kt` | `:2255-2266`（`country=institutionCountry`、`employment=null`、`institution=institutionName`）、`inferCountryFromAffiliation` 已整段删除（全文件 0 命中）、`:3005-3009`（补全写入体不再含 `institutionType` 键） |
| I-1 作者—机构同源 | IMPLEMENTED | 上述 4 个产品文件 | 多机构一律 null（`OpenAlexDataSourceTest.works path never picks one of several authorship institutions`、`JatsXmlEmailParserTest.two equally untyped affiliations…`）；无类型/院系/脚注标签不产生机构（`…untyped department footnote email and ror…`） |
| I-2 字段语义 | IMPLEMENTED | 上述 4 + `AuthorEmail.kt` | 论文 `employment=null`、国家只来自同一结构机构且经地区表识别（`unknown or ambiguous country is dropped…`、`works path drops a country the region table does not recognise`）；`institutionSource` 仅结构解析成功时写 `JATS`/`OPENALEX` |
| I-3 机构类型绑定 | IMPLEMENTED | `OpenAlexDataSource.kt`、`ExpertDiscoveryService.kt` | works 路径类型与同一机构对象同源（`:284-299`）；authors 补全不再写 `institutionType`（`:3005-3009`） |
| 不得改变邮箱—作者绑定、ORCID/SBIR 来源、非发现档案 | IMPLEMENTED（未触碰） | — | 未改 `DiscoveryIdentity`、`OrcidDataSource`、`buildOrcidProfile`（`ExpertDiscoveryService.kt:1513-1525` 原样）、SBIR 脚本；`SourceAuthorEmailResolver` 仅在既有唯一 owner 分支追加字段 |

## TDD 证据

### 红证据 A（行为红，改产品代码前）

命令：`JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn -q test -Dtest=OpenAlexDataSourceTest,ExpertDiscoveryServiceTest`（此时只加了不依赖新 API 的断言）→ **exit 1，Tests run: 234, Failures: 3, Errors: 0**：

```
[ERROR] Tests run: 161, Failures: 2, Errors: 0, Skipped: 0 … in …ExpertDiscoveryServiceTest
[ERROR] discover leaves institution country and employment empty without structured evidence {I-1 I-2} … 
  AssertionFailedError: 无结构机构时不得把原文署名写进 institution ==> expected: <null> but was: <Oxford, UK>
[ERROR] enrichExistingExperts never overwrites institutionType from another source object {I-3} …
  AssertionFailedError: 补全不得写异源 institutionType：{… institutionType=company …} ==> expected: <false> but was: <true>
[ERROR] Tests run: 73, Failures: 1, Errors: 0, Skipped: 0 … in …OpenAlexDataSourceTest
[ERROR] works path never picks one of several authorship institutions {I-1} … expected: <null> but was: <First University>
```

三条红都不是编译问题，而是旧行为（原文回填 `institution`、逗号末段猜国家、署名当 `employment`、异源类型覆盖、多机构取第一家）直接违反新断言。

### 红证据 B（新 API 红，改产品代码前）

新增字段断言写入后、产品代码未改时：`mvn -q test-compile` → **exit 1**，编译错误逐字（节选）：

```
ExpertDiscoveryServiceTest.kt: (105, 46) Too many arguments for public constructor AuthorEmail(…9 params…)
JatsXmlEmailParserTest.kt: (189, 58) Unresolved reference: institutionName
OpenAlexDataSourceTest.kt: (797, 49) Unresolved reference: institutionName
OpenAlexDataSourceTest.kt: (861, 55) Cannot find a parameter with this name: institutionName
```

即：新断言所依赖的内部 `institutionName/institutionCountry/institutionSource` 在改动前不存在（Kotlin 单模块编译，故以编译红呈现）。

### 绿证据

实现后定向类全绿（见命令 1），全量套件绿（见命令 2）。新增 14 个用例，未削弱任何既有断言（唯一反转是计划 I-3 明确要求的「覆盖 → 不覆盖」：`ExpertDiscoveryServiceTest:3343-3389`）。

## Commands（fresh，本次调用内运行）

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=JatsXmlEmailParserTest,OpenAlexDataSourceTest,SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest` | PASS | exit 0，`BUILD SUCCESS`，`Tests run: 300, Failures: 0, Errors: 0, Skipped: 0`；分类计数：JatsXmlEmailParserTest 40、OpenAlexDataSourceTest 79、SourceAuthorEmailResolverTest 19、ExpertDiscoveryServiceTest 162；Node 套件同时跑完 1202 tests / 1202 pass / 0 fail（logs: `/tmp/child01-cmd1.log`） |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0，`BUILD SUCCESS`，`Total time: 05:03 min`；surefire 聚合 `Tests run: 4234, Failures: 0, Errors: 0, Skipped: 13`；Node 套件 `tests 1202 / pass 1202 / fail 0`（logs: `/tmp/child01-cmd2.log`） |
| `git diff --check` | PASS | exit 0，无输出（无空白/冲突标记问题） |

### 与基线对比（`children/01/baseline.md`）

| 指标 | 基线 | 本次 | 判定 |
|---|---|---|---|
| `mvn clean package` | exit 0，surefire 4220 / 0 / 0 / 13 skipped，Node 1202 pass | 未跑 `clean package`（brief 必需命令为 `mvn test`）；`mvn test` exit 0，surefire 4234 / 0 / 0 / 13 skipped | 无新增失败；+14 用例 = 本 child 新增 |
| JatsXmlEmailParserTest | 35 / 0 / 0 | 40 / 0 / 0 | +5 |
| OpenAlexDataSourceTest | 73 / 0 / 0 | 79 / 0 / 0 | +6 |
| SourceAuthorEmailResolverTest | 18 / 0 / 0 | 19 / 0 / 0 | +1 |
| ExpertDiscoveryServiceTest | 160 / 0 / 0 | 162 / 0 / 0 | +2 |
| Node 套件 | 1202 pass | 1202 pass | 不变 |

## Changed Files（全部在 Authorized Files 白名单内，10/10）

- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/AuthorEmail.kt` — 追加内部可空 `institutionName/institutionCountry/institutionSource`（`@JvmOverloads` 追加在末尾，全部既有位置参数调用点不受影响）+ 来源常量 `INSTITUTION_SOURCE_JATS/OPENALEX`（`:3-41`）
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperAuthor.kt` — 同三字段（`:17-25`）
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt` — 唯一 `university|edu` `<institution>` 提取、同 aff 唯一可识别 `<country>`、脚注排除、来源标记（`:21-25,56-65,194-227`）
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` — `soleInstitution` 唯一机构对象、name/type/country 同源、来源标记（`:275-299,317-320`）；PMC/JATS 分支仅在唯一 ORCID 绑定且 JATS 无机构时补齐整条机构（`:110-133,218-236`）
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` — 唯一 owner 分支传播机构三字段；无 owner 线索分支仍为 null（`:165-169`）
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — `buildProfile` 用新字段 + `employment=null`（`:2255-2266`）、删除 `inferCountryFromAffiliation`、补全不再写异源 `institutionType`（`:3005-3009`）
- `src/test/kotlin/.../JatsXmlEmailParserTest.kt` — 新增 5 用例（university 结构化机构 + 脚注/ROR/邮箱/邮编噪声、无类型/院系/脚注不入选、PMC13138287 形态双子 aff 反例、edu 接受与多机构歧义、未知/多国家丢弃）
- `src/test/kotlin/.../OpenAlexDataSourceTest.kt` — 新增 6 用例（唯一机构 name/country/type/来源同源、未知国家丢弃、空 display_name 不构成第二机构、XML 分支唯一 ORCID 补齐、JATS 机构不被覆盖、ORCID 绑定机构矛盾不补）+ 反转多机构用例为「不取第一家」
- `src/test/kotlin/.../SourceAuthorEmailResolverTest.kt` — 新增 1 用例（机构仅随唯一作者证据传播）
- `src/test/kotlin/.../ExpertDiscoveryServiceTest.kt` — 新增 2 用例（无结构证据 → institution/country/employment 全空；结构化机构 → institution/country 同源且 employment=null）+ I-3 覆盖断言反转为不覆盖 + 测试 helper 追加机构参数

`git show --stat b12c971` 与 `git status --porcelain` 复核：仅上述 10 文件；`docs/plans/fast/**` 保持未跟踪（未进提交）。

## 下游接口（供 01b/02/03）

- `AuthorEmail` / `PaperAuthor` 新增：`institutionName: String?`、`institutionCountry: String?`、`institutionSource: String?`（`institutionSource` 只可能是 `"JATS"` / `"OPENALEX"`，且仅当对应结构解析真的成功；无结构证据时三者皆 null）。
- 论文建档（`buildProfile`）：`institution = institutionName`、`country = institutionCountry`、`employment = null`、`institutionType = authorEmail.institutionType`（该类型在提取侧已与同一机构对象配对：JATS 不产类型、OpenAlex 与机构对象同源）。
- 类型不再跨源覆盖：`updateExpertAcademicFields` 不写 `institutionType`；文档里 `institutionType` 只来自发现时（论文 authorship / ORCID `institution-name` 路径原样）。
- 未新增 ES 字段（`institutionSource` 仅内部模型字段，02 才落 `institutionEvidence`）；未改 ES mapping、未改迁移、未改前端。

## Deviations

- 无。10 个 Authorized Files 之外未改动任何文件；未改 `docs/plans/**`；未 push/merge/rebase/amend/reset；未跑格式化/lint。
- 两个需要显式记录的判断（都在计划文本之内，不是范围扩张）：
  1. `buildProfile` 保留 `institutionType = authorEmail.institutionType` 的转发（未加「必须同时有 institutionName」的额外守卫）。理由：I-3 要求的是「类型与所显示机构的**同一来源机构对象**配对」，而配对只能在提取侧判定；提取侧已保证「有类型必有同源机构名」（JATS 不产类型、OpenAlex 同一对象产出），故 ES 侧只是转发提取结果。既有 `I5a-3/I5a-4` 键集与类型断言因此保持逐字不变。
  2. JATS 不从 `content-type` 反推 `institutionType`（保持 null）。计划只要求把它当作「是否组织级」的准入标签，未授权把 JATS 词表（`university`/`edu`）当作我们的机构类型枚举；按「类型未知则 null」保守处理。
- 计划提到的真实样本 `PMC13138287` XML 本工作树无副本（已确认测试资源 zip 中不含任何 `<institution>` 节点、`docs/audits/2026-09-28-discovered-institution-comprehensive/README.md` 也无该片段）。反例测试按计划审计描述逐字构造该样本的两个 aff（无类型 `Mayo Clinic Alix School of Medicine` / `Department of Neurologic Surgery`），断言「两者标签相同 → 机构为空、原文两条都留在 affiliation」。

## Freshness

- Plan identity rechecked: YES（执行前 `/` 提交前两次 sha256 一致：`c7300da1…c02c4`；master `2f7fdb23…404c6`）
- Worktree identity rechecked: YES（`git rev-parse --show-toplevel/--git-dir/--git-common-dir/--abbrev-ref HEAD` 全程指向本 worktree 与 `fast/2026-09-28-discovered-institution-repair-00-master`）
- Reported commits reachable from target branch: YES（`b12c971` 即该分支 HEAD；`git rev-parse HEAD` = `b12c971be46a992b275a3e4fb3768047b0af8877`）
- Required commands run this invocation: YES（命令 1/2/3 均在本实现最终状态下 fresh 运行）
- Historical evidence used only as baseline: YES（`children/01/baseline.md` 仅用于计数对比）

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`。
- 人工验收（master plan A-1/A-2/A-3）需要运行真实发现任务并查看详情页，属于 whole-system 人工门禁，不在本 child 执行范围。

## 已知行为变化（供 verify/human 预期对齐）

- 新发现只写结构机构：非结构化来源（Crossref/ArXiv/Core 等的署名字符串、Europe PMC 搜索字段）现在产出 `institution=null`、`country=null`、`employment=null`。因此 `ExpertClassificationService`（读 `institution`/`employment`）对这类新档案的分类会变（多为 UNKNOWN → `filterResult=REJECTED`），这正是 master plan「缺证据者不进入首发名单」的方向；`mvn test` 全绿说明现有测试未依赖旧口径。
- `getEnrichmentStats().institutionTypePending` 的补采过滤器未改动（计划未授权改动其口径）；由于补全不再写 `institutionType`，该待办计数对这些专家将保持非零。这是计划 I-3 的直接结果，非新增缺陷。
