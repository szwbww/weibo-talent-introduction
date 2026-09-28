## Light Verification: LIGHT_PASS_WITH_NOTES

- Child: 02 — 机构来源证据落库；plan `docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md`，identity `commit:a90f59d8d57dea33d83b571bbb62c5f389d387d7`（实测 sha256 `f1803069d02e9c06d78c09db688a9e2a85a31fe0000f74b76d6e5ee1932355d7` / 9552 bytes；HEAD 版本与 a90f59d 版本零差异）
- Boundary: `68ad011971ac4cbafdd439cfe2d981476ff1332a..c30c954761b199467c7d904a50b177808f54ddce`
  - 实现提交 `c30c954`（`feat(fast-p): implement 02`，10 文件，不含 `docs/plans/**`）；A1 计划修订 `a90f59d`（docs-only，+1 行）；边界内另有 `39b8842`（pause/A1 记录）与 `f5bff23`（01b 验证记录），均为 docs-only，非本 child 产品改动
- Verifier: Verify02（light / 四门）；工作区 HEAD = `c30c954761b199467c7d904a50b177808f54ddce`，分支 `fast/2026-09-28-discovered-institution-repair-00-master`；所有命令均为本次 fresh 运行（未复用实现者数字）

| Gate | 结果 | 结论要点 |
|---|---|---|
| 1 Authorized scope | PASS | 变更产品/测试文件 10 个，全部落在 brief 11 行授权清单内；A1 文件仅授权 2 行；无白名单外文件 |
| 2 Plan and invariants | PASS | I-1 / I-2 / I-3 均有直接代码 + 测试证据；golden token 由本验证器独立重算复现 |
| 3 Required commands | PASS | 三条命令 fresh 全绿，计数与基线/01b 后态自洽 |
| 4 Downstream interfaces | PASS | token 格式、唯一签发/验签函数对、可空字段 + `_source` 投影、三层 `_source` 透传与 brief 逐字一致 |

### Gate 1 — Authorized scope

- `git diff --name-status 68ad011..c30c954` 的产品/测试文件恰为 10 个，逐一命中 brief 授权表第 1–11 行：
  `orcid_info_raw.json`、`orcid_info_candidate.json`、`orcid_info_application.json`、`ExpertProfile.kt`、`DiscoveryIdentity.kt`、`ExpertDiscoveryService.kt`、`ExpertSearchService.kt`、`ExpertDiscoveryServiceTest.kt`、`ExpertSearchServiceTest.kt`、`ExpertIndexServiceTest.kt`（A1）。无新建文件。
- 第 10 行 `OperatorStatusWriteSeamGuardTest.kt`「若移行仅改行号」为条件授权且未触发：新增读取行落于钉死点之后（`ExpertSearchService.kt:499` 为排除点 → 新增 `:509-511`、`:595`），fresh 运行 1/0/0/0，排除清单无过期项（该测试自带 stale-exclusion 自检）。
- A1 变更逐字核对：`ExpertIndexServiceTest.kt:169-170` 仅注释同步 + `assertEquals(36, singleFieldPuts, "RAW batch failure must degrade to per-field PUTs for every declared field")` → `37`；断言消息、`batchPuts=3` 断言、"every declared field" 语义均未放宽；该文件 diff 为 `2 insertions(+), 2 deletions(-)`。计数期望语义正确：本验证器独立 `jq` 得 RAW/CANDIDATE/APPLICATION = **37 / 39 / 49**（base 36/38/48），三层 `mappings.properties.institutionEvidence.type = keyword`、根 `dynamic = false` 不变。
- 边界内 docs 改动（child plan +1 行 = A1；01b/02 报告、ledger）均非产品代码，未混入实现提交。

### Gate 2 — Plan and invariants

- **I-1（单字段）**：三层 mapping 各 `+1` 行且仅此一个 keyword；token 由唯一函数产出（`DiscoveryIdentity.kt:93-110`，词表 `:80-82`），NUL 分隔固定顺序 `来源种类 → proof.source → proof.evidenceHash → 规范邮箱 → 姓名 → 来源作者 ID → 论文 ID 或 ORCID ID → 机构 → 国家 → 机构类型`（`:124-140`），格式 `"$source:${hash(...)}"`，`hash` 为既有小写 SHA-256（`:33`）。签发来源只认 01 内部 `institutionSource`（`ExpertDiscoveryService.kt:2269`）或 ORCID 路径显式常量（`:1527`），不按 `dataSource`/旧 `institution` 补签；无证据时 `toIndexMap` **不写键**（`:2303`，`profile.institutionEvidence?.let {…}`），绝不写 `null`/`false`/`UNVERIFIED`；仓库内无任何按字段外观扫描历史文档补签的逻辑。
  - 独立复算（不依赖测试常量）：本验证器用 python3 按上述顺序以 `\u0000` 连接并 SHA-256，得 `JATS:38473ba167e1e5703c47eed9263eda9ad2e7a473854a8422c0a7be320780721c`，与 `ExpertDiscoveryServiceTest` golden 断言逐字相同 → 输入顺序/编码被钉死且可被未来改动暴露。
- **I-2（原子同步）**：`consistentIdentityIds`（`DiscoveryIdentity.kt:154-171`）核 proof.orcid↔`externalIds.orcid`、proof.openAlexAuthorId↔`externalIds.openAlexAuthorId`、真实 ORCID 主键（非 `EMAIL-*`）↔`externalIds.orcid` 及 ↔proof.orcid；来源必需 ID：JATS 必须 `pmcId`、OPENALEX 必须同作者 `openAlexAuthorId` 且有 `doi`/`pmcId`、ORCID 必须 `externalIds.orcid`。签发点：`buildProfile` 在原始 `AuthorEmail` 在场时签发、`toIndexMap` 仅写入；ORCID 由 01b 唯一机构驱动。机构/国家/类型任一变更 → 重算失配（`validInstitutionEvidence` 只读已存字段重算）。测试覆盖：线上 1,256 条反例形状（主键 vs `externalIds.orcid` 冲突）拒签且旧 token 验签失败、三类 ID 冲突、各来源缺 ID、来源种类不在词表（SBIR）、`identityVerification=null`、机构空、机构/国家/类型改值失效。
  - 晋升 `_source` 全量透传沿用现状，未新增第二套复制逻辑（`ExpertIndexWriterService.kt:552-559`、`:443-449`）；测试断言 RAW→CANDIDATE token 逐字相同。
  - 学术补全不触碰机构/证据：`updateExpertAcademicFields`（`ExpertDiscoveryService.kt:3004-3052`）在本边界内零改动（该文件仅 3 处 hunk：`:1526-1527`、`:2264-2280`、`:2299-2304`），写入体不含机构/证据键，`profile.copy` 保留新字段。
- **I-3（读取全路径）**：`ExpertProfile.institutionEvidence`/`filterResult` 为末尾可空字段；`ExpertSearchService.kt:509-511` 显式读取、`:595` 投影点名；`validInstitutionEvidence(null)=false`。测试按三层真实 `_source` 形状驱动读取：旧文档（无键）→ 双字段 `null` 且不默认合格，签档 → token/`PASSED` 读出且能被唯一验签函数重算通过；`ExpertIndexWriterService.discoveryProfile` 零改动（仍从原 `_source` 取机构，不填这两个字段），与 I-3「不参与发送门禁」一致。
- 未改变已有主键、身份校验结构、旧非发现档案、历史邮件；无迁移、无前端、无 ES 网络操作（diff 内无 `src/main/resources/db`、无脚本）。

### Gate 3 — Required commands（本次 fresh）

| # | Command | 结果 | 本次实测计数 |
|---|---|---|---|
| 1 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertSearchServiceTest,OperatorStatusWriteSeamGuardTest` | exit 0 / `BUILD SUCCESS` | 聚合 **246 / 0 / 0 / 0**（`ExpertDiscoveryServiceTest` 171、`ExpertSearchServiceTest` 74、`OperatorStatusWriteSeamGuardTest` 1）；构建内 JS 门禁 1202 pass / 0 fail |
| 2 | `JAVA_HOME=… mvn test` | exit 0 / `BUILD SUCCESS` | surefire 269 个类 **4253 / 0 / 0 / 13**；JS 套件 `tests 1202 / pass 1202 / fail 0`；`ExpertIndexServiceTest` 10 / 0（A1 后转绿） |
| 3 | `git diff --check` | exit 0 | 无输出 |

- 与基线对比：seed `children/01/baseline.md` = 4220/0/0/13；child 01b 后态 = 4242/0/0/13；本次 = **4253/0/0/13 = 01b + 11**（`ExpertDiscoveryServiceTest` 164→171 = +7；`ExpertSearchServiceTest` 70→74 = +4），与新增用例数完全一致，无新增失败、无 skipped 漂移；Node 1202 不变。`OperatorStatusWriteSeamGuardTest` 1 与基线相同（行号未平移）。基线上无预置红，故本次亦无「基线已红」需归类项。

### Gate 4 — Downstream interfaces（child 03）

- token 格式 `JATS:<64hex>` / `OPENALEX:<64hex>` / `ORCID:<64hex>`：常量 `DiscoveryIdentity.EVIDENCE_SOURCE_JATS/OPENALEX/ORCID`，词表与 01 的 `INSTITUTION_SOURCE_JATS="JATS"`、`INSTITUTION_SOURCE_OPENALEX="OPENALEX"`（`AuthorEmail.kt:7-8`）逐字一致。
- 唯一签发 + 验签函数对：`DiscoveryIdentity.institutionEvidence(profile, source): String?`（null = 不写键）与 `validInstitutionEvidence(profile): Boolean`（只读已存字段、内部委托同一签发函数重算，含 ID 一致性）。签发的生产调用方唯一（`ExpertDiscoveryService.kt:2278`）；`validInstitutionEvidence` 目前只有测试调用方 —— 与计划「03 才做发送验签」一致，非缺件。
- 读取投影：`ExpertProfile.institutionEvidence` / `filterResult` 可空，经 `ExpertSearchService.toExpertProfile` + `sourceFields()` 从 ES `_source` 读出；旧文档 → `null`。`ExpertIndexWriterService.discoveryProfile` 刻意不填这两个字段，故 03 门禁必须走 `ExpertSearchService` 投影（与本 brief 下游说明一致）。
- 晋升多层透传：`promoteDiscoveredToCandidate`（raw `toMutableMap()`）与 `ExpertIndexWriterService.promoteToCandidate`/`promoteToApplication`（全 `_source` 字段 deepCopy）原样透传任意新键，RAW→CANDIDATE 有本次新测试直接断言 token 逐字相同；CANDIDATE→APPLICATION 走同一复制模式，由既有 `ExpertIndexWriterServiceTest`（`promoteToCandidate deep copies complex source fields` / `promoteToApplication removes promoted document from candidate index`）覆盖透传语义。

### AUTO_FIX

- none（四门均无被证实违规项）

### RECORD_ONLY

- **OPENALEX 来源无正向签发断言**（覆盖观察，非缺陷）：仓库内 `source=OPENALEX` 且 `externalIds.openAlexAuthorId` + `doi`/`pmcId` 齐备 → `OPENALEX:<64hex>` 只有代码证据与拒签负例，e2e 只覆盖 JATS/ORCID。该分支生产可达（`OpenAlexDataSource.kt:296/299` 置 `openAlexAuthorId`/`institutionSource=OPENALEX`，`ExpertDiscoveryService.kt:3284` 写入 `externalIds.openAlexAuthorId`，`SourceAuthorEmailResolver.kt:166` 提供 `SOURCE_SHA256:` 凭证通过 `allowed`）。child 03 消费 OPENALEX token 前建议补一条正例（新增/修改测试文件超出本 child 授权，本 child 不处理）。
- `children/02/fix-log.md`（未跟踪、docs）首段称「The first light verification returned `COMPLETE_CHILD`」，与 ledger 不符：02 在 epoch 1 以 `PLAN_CONFLICT` 暂停、从未被验证（ledger 02 行 `Evidence commit = —`，本次为首轮 light verification）。仅文档准确性问题，无代码影响；后续 fix-round 记账勿以此句为依据。

### Required Action
- COMPLETE_CHILD
