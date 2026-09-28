## Light Verification: LIGHT_PASS_WITH_NOTES

- Child: 01（新发现机构来源提取修复）
- Boundary: `2e9639df7947bc5f3057ca08e1445b155aba7cd7..b12c971be46a992b275a3e4fb3768047b0af8877`（单提交 `feat(fast-p): implement 01`；`git rev-parse HEAD` = `b12c971be46a992b275a3e4fb3768047b0af8877`，即目标分支 HEAD）
- Verifier: `Verify01-2`（light / four-gate；JDK 11 `zulu-11`；三条必需命令在本最终状态下 fresh 重跑，未采用实现者数字）

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --stat base..head` 恰为 brief 授权的 10 个文件（6 产品 + 4 测试），**无新增文件**；6 产品文件与计划「变更文件清单」逐条对应。`git status --porcelain` 仅 `?? docs/plans/fast/**`（报告未进提交）。`docs/plans/2026-09-28/**`（冻结计划）无任何改动 |
| Plan and invariants | PASS | I-1/I-2/I-3 均有直接代码 + 测试证据（见下）；未改邮箱—作者绑定、ORCID 路径、SBIR/非发现档案 |
| Required commands | PASS | 命令 1 exit 0 / 300·0·0·0；命令 2 exit 0 / 4234·0·0·13（Node 1202 pass·0 fail）；命令 3 exit 0（无输出） |
| Downstream interfaces | PASS | 与 brief「downstream interface note」逐项一致（见下） |

### Gate 2 — 要求与不变量证据

**I-1 作者—机构同源**
- JATS：作者关联 aff 只在 `contrib` 直接 `<aff>` + `ref-type=aff` 的 `xref rid`（`ids[rid].singleOrNull()` 唯一解析，且必须是 `<aff>`）中取（`JatsXmlEmailParser.kt:56-58`）；机构名取其唯一 `content-type ∈ {university, edu}` 的非空 `<institution>`，且排除 `<fn>` 内脚注标签（`:198-224`，`ORGANISATION_CONTENT_TYPES` `:21-25`）；多机构名 `distinct().singleOrNull()` 否则整体 null（`:203-206`）。
- OpenAlex：`soleInstitution` = 同一 `authorship.institutions` 中唯一「`display_name` 非空」对象，多余/空名/多机构 → `singleOrNull()` null（`:285`、`:314-318`）。
- 传播：`SourceAuthorEmailResolver` 仅在既有「唯一 owner」分支携带机构三字段，邮箱线索/同名多作者分支仍是 null（`:161-169`）。
- 测试：`JatsXmlEmailParserTest`「university typed institution…」「untyped department footnote email and ror…」「two equally untyped affiliations…（PMC13138287 形态）」」「edu content type is accepted while several different typed institutions are not」；`OpenAlexDataSourceTest`「works path never picks one of several authorship institutions (I-1)」（原 I5a-2 断言 `First University` 反转为 null，计划明确授权）「works path ignores blank institution entries…」；`SourceAuthorEmailResolverTest`「structured institution travels only with one uniquely owned mailbox」。

**I-2 字段语义**
- `affiliation` 原文线索逐字保留（JATS 仍 `relatedAffs…joinToString("; ")`，`JatsXmlEmailParser.kt:61`）；ROR/GRID/邮箱/邮编/脚注只在 affiliation（测试逐条断言）。
- `institutionSource` 仅结构解析成功时写 `JATS` / `OPENALEX`（`JatsXmlEmailParser.kt:65`、`OpenAlexDataSource.kt:299`），无 `dataSource` 推断路径。
- `country` 仅取承载该机构名的同一 `<aff>` 唯一且 `CountryContinentMapping.toRegion ≠ REGION_OTHER` 的 `<country>`（`JatsXmlEmailParser.kt:226-233`）／同一 OpenAlex 机构对象的 `country_code` 且地区表可识别（`OpenAlexDataSource.kt:289-290`）。
- 论文发现 `employment = null`、`institution = institutionName`（`ExpertDiscoveryService.kt:2259-2260`）；`inferCountryFromAffiliation` 已整段删除（git grep base = 2 处，HEAD = none）。

**I-3 类型绑定**
- OpenAlex works 路径 `type`/`name`/`country` 全部取自同一 `institution` 对象（`OpenAlexDataSource.kt:285-296`）；PMC→OpenAlex 补齐仅在 `email.institutionName == null` 且 `verifiedAuthorIdByOrcid` 唯一、且同 ORCID 作者给出**同一个** `OPENALEX` 来源机构时才整条取值（`:114-133`、`:218-235`）。
- `updateExpertAcademicFields` 写入体已不含 `institutionType`（`ExpertDiscoveryService.kt:3005-3009`），补全读到的 `last_known_institutions[0].type` 不再落 ES。
- 旧「覆盖」断言已按计划反转：`enrichExistingExperts never overwrites institutionType from another source object (I-3)` 断言 `assertFalse(doc.containsKey("institutionType"))`。
- 全仓核对：`src/main` 中 `institutionType` 的产生点只有 OpenAlex works 路径（另加两处字段声明），故「有类型必有同源机构名」；`buildProfile` 转发 `institutionType = authorEmail.institutionType` 不破坏配对（实现者已披露该判断，属计划文本内）。

**不得改变项**
- JATS 邮箱绑定逻辑（候选/claim/xref-corresp 归属）逐字未动，diff 只新增 AuthorEmail 三字段与提取辅助函数；`SourceAuthorEmailResolver` claim 逻辑未动。
- ORCID 路径独立：`buildOrcidProfile`（`ExpertDiscoveryService.kt:1513-1525`）仍 `institution = record.institutionName`；`buildProfile` 唯一调用方在论文分支（`:1573`），ORCID 分支走 `:1582`。

### Gate 4 — 下游接口

- `AuthorEmail` / `PaperAuthor` 新增内部可空 `institutionName` / `institutionCountry` / `institutionSource`；`institutionSource` 取值域只有 `"JATS"` / `"OPENALEX"`，且仅对应结构解析成功时非空；无结构证据时三者皆 null ✔
- 单机构才判定：JATS `distinct().singleOrNull()`、OpenAlex `soleInstitution().singleOrNull()`、resolver 唯一 owner ✔
- 论文发现 `employment = null`；`institution = institutionName` ✔
- `institutionType` 不跨来源对象复制：补全写入体无该键 ✔
- 未新增 ES 字段、未改 mapping/迁移/前端 ✔

### Required commands（fresh，本最终状态）

| # | Command | Result |
|---|---|---|
| 1 | `mvn test -Dtest=JatsXmlEmailParserTest,OpenAlexDataSourceTest,SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest` | exit 0，`BUILD SUCCESS`，`Tests run: 300, Failures: 0, Errors: 0, Skipped: 0`；分类：JatsXmlEmailParserTest 40、OpenAlexDataSourceTest 79、SourceAuthorEmailResolverTest 19、ExpertDiscoveryServiceTest 162；同批 Node 套件 `tests 1202 / pass 1202 / fail 0` |
| 2 | `mvn test` | exit 0，`BUILD SUCCESS`（04:48），`Tests run: 4234, Failures: 0, Errors: 0, Skipped: 13`；Node `tests 1202 / pass 1202 / fail 0` |
| 3 | `git diff --check` | exit 0，无输出 |

与 `children/01/baseline.md` 对比：命令 2 计数 4234/0/0/13 vs 基线 4220/0/0/13 —— 差额 +14 恰等于本 child 新增用例（JATS +5、OpenAlex +6、Resolver +1、ExpertDiscoveryService +2），**零新增失败**；Node 1202 不变。定向类计数与实现者报告一致（40/79/19/162）。
备注：实现已提交，故 `git diff --check`（工作树 vs 索引）检查面为空；补充执行 `git diff --check base..head`（覆盖实际 diff）亦 exit 0 无输出。

### AUTO_FIX

无。四门均无「已证实违规 + 计划唯一确定修正 + 全部文件已授权」的组合。

### RECORD_ONLY

- `ExpertDiscoveryService.kt:3008-3009` 遗留注释仍写「I1-4: 非 null 时无条件覆盖」，该语义已随 I-3 移除（现仅对紧邻的 `lastPublicationYear` 成立，易误读）；同因 `AuthorEnrichment.institutionType`（`OpenAlexDataSource.kt:592`）与 `getEnrichmentStats().institutionTypePending`（`:2318`/`:2363`）在本次后永久不会由补全写入，该待办计数对相关专家将长期非零 —— 二者均为 I-3 的直接结果、实现者已在 execution.md 披露，计划未授权改其口径，非计划冲突、不影响本 child 判定。

### Required Action
- COMPLETE_CHILD
