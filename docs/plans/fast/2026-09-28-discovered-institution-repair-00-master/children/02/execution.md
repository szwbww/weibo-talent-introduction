# Child 02 执行报告 — 机构来源证据落库

## Execution Result: PLAN_CONFLICT

（实现已按批准计划全部落盘并通过定向命令；唯一红点是**白名单外**的计数钉子测试。按 brief 规则 5 与 Main 的裁决返回 `PLAN_CONFLICT`，**未提交**、未再改动任何文件。）

- Plan（完整合同）: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master/docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md`
- Plan SHA-256: `8786cbe8553f3be00b7377a3bcf04be31be4477c0d6163b3381ae6be4a210c1c`（执行前/提交前两次复算一致，`size_bytes 9312`）
- Brief: `children/02/brief.md`（本 child 的派发合同）
- Execution ID: `docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md@8786cbe8553f3be00b7377a3bcf04be31be4477c0d6163b3381ae6be4a210c1c`
- Execution epoch: `NEW`
- Approval basis: 本次调用（child 02 派发消息 + brief）；冲突裁决由 Main 给出（见「阻塞点」）
- Executor: `Implement02`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Target branch: `fast/2026-09-28-discovered-institution-repair-00-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master@fast/2026-09-28-discovered-institution-repair-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Pre-execution code SHA (`child_base_sha`): `68ad011971ac4cbafdd439cfe2d981476ff1332a`；本地 HEAD = `f5bff239bd4c1c2ff939a29cff0d682d78f7a601`（两者之间只有 docs 提交：`git diff --stat 68ad011..HEAD -- src` **无输出**、`git log --oneline 68ad011..HEAD` = `f5bff23 docs(fast-p): record 01b light verification`，故代码基线与 child_base_sha 逐字相同）
- Post-execution code SHA: **N/A（未提交）** —— 按 Main 裁决「不要提交，把未提交改动原样留在工作区」，实现保留为未提交工作区状态
- Implementation boundary: 工作区（未提交），9 个已修改文件，`git diff --stat` = `9 files changed, 539 insertions(+), 5 deletions(-)`
- 身份工具：`~/.agents/skills/execute-p/scripts/plan_identity.py`、`worktree_identity.py`（含 `--expect-root/--expect-branch`）本次均可用，执行前与冻结前各取一次，输出一致

## 阻塞点（PLAN_CONFLICT 的唯一原因）

三层 mapping 新增 `institutionEvidence` keyword 是 I-1 的**强制**要求（验收标准：「三层仓库 mapping 有同类型 keyword」），但白名单外已存在一个把 RAW mapping **顶层属性个数**钉死为 36 的测试：

- 文件/行：`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt:170`
- 逐字断言：`org.junit.jupiter.api.Assertions.assertEquals(36, singleFieldPuts, "RAW batch failure must degrade to per-field PUTs for every declared field")`
- 其上一行注释（`:169`）：`// researchFieldIds is now declared alongside the existing RAW properties.`
- `singleFieldPuts` 统计的正是「批量 PUT 返回 400 后逐字段降级 PUT 的次数」= RAW mapping 顶层属性个数。

计数证据（本次 fresh 运行）：

```sh
$ jq '.mappings.properties | length' src/main/resources/es/orcid_info_raw.json          # 37（本次改动后）
$ git show HEAD:src/main/resources/es/orcid_info_raw.json | jq '.mappings.properties | length'   # 36（child base）
$ jq '.mappings.properties | length' src/main/resources/es/orcid_info_candidate.json    # 39 / HEAD 38
$ jq '.mappings.properties | length' src/main/resources/es/orcid_info_application.json  # 49 / HEAD 48
```

即：三层各恰好 +1（`institutionEvidence`），RAW 36 → 37 是 `mvn test` 唯一失败的**全部**原因，且该失败在 seed/01/01b 基线上不存在（基线 4242/0/0/13 全绿）。

- 计划/主计划的 02 变更文件清单只有 10 个文件，其中**没有** `ExpertIndexServiceTest.kt`；brief 只对 `OperatorStatusWriteSeamGuardTest` 给出「仅按语义改排除清单行号」的预授权，未覆盖本文件。
- 因此「完成（全量测试回绿）」需要白名单外文件 → 按 brief 规则 5 与 execute-p 的 `PLAN_CONFLICT`（需要未被授权的范围）处理。
- 已向 Main 请示；Main 答复（逐字要点）：冲突属实、需要**人工**批准计划修订、Main 无权自行授予；指示「不要提交、不要再改任何文件、把未提交状态原样留在工作区」「报告写清冲突证据与三命令结果后返回 PLAN_CONFLICT，commit SHA = none」。

**唯一剩余工作（一行，无行为内容）**：把 `ExpertIndexServiceTest.kt:170` 的 `36` 改为 `37`，并把 `:169` 的注释同步说明「本轮新增 `institutionEvidence`」；断言本身（"every declared field" 语义）不得放宽或删除。完成该行后，本 child 的三条必需命令即为全绿（见下表预期：`mvn test` 4253/0/0/13）。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 三层 mapping 新增且仅新增 `institutionEvidence` keyword | IMPLEMENTED | `orcid_info_raw.json`、`orcid_info_candidate.json`、`orcid_info_application.json` | `orcid_info_raw.json:29`、`orcid_info_candidate.json:30`、`orcid_info_application.json:41` 均为 `"institutionEvidence": { "type": "keyword" }`；根 `dynamic:false` 未变；测试 `ExpertSearchServiceTest` 逐层读取资源断言 keyword（三层各 +1 属性，`jq` 计数见上） |
| I-1 统一签发/验签函数（NUL 分隔固定顺序） | IMPLEMENTED | `DiscoveryIdentity.kt` | `DiscoveryIdentity.kt:72-193`：`EVIDENCE_SOURCE_JATS/OPENALEX/ORCID`（`:80-82`）、`institutionEvidence(profile, source)`（`:93-110`）、`validInstitutionEvidence(profile)`（`:116-121`）、固定输入顺序 `tokenDigestInput`（`:124-140`）；golden 向量测试逐字钉死输入顺序 |
| I-1 值域与拒签（空/多机构/身份未证实/来源不匹配 → 不写键） | IMPLEMENTED | `ExpertDiscoveryService.kt`、`DiscoveryIdentity.kt` | `ExpertDiscoveryService.kt:2268-2280`（`withInstitutionEvidence`：来源种类只认 01 的内部 `institutionSource` 或显式 `ORCID`，否则原样返回）、`:2302-2304`（`profile.institutionEvidence?.let { doc[...] }` → 无证据**不写键**，不写 null/false/UNVERIFIED） |
| I-2 签发前置：ID 冲突 / 各来源必需来源 ID | IMPLEMENTED | `DiscoveryIdentity.kt` | `:145-152`（`consistentIdentityIds`：凭证 ORCID/OpenAlex 作者 ID vs `externalIds`、真实 ORCID 主键（非 `EMAIL-*`）vs ORCID 值）、`:103-108`（JATS 必须 `pmcId`；OPENALEX 必须同作者 `openAlexAuthorId` 且有 `doi`/`pmcId`；ORCID 必须 `externalIds.orcid`）；线上 1,256 条冲突形状被函数级测试覆盖 |
| I-2 机构/国家/类型变更 → 旧 token 失效；签发点 = `buildProfile`/`buildOrcidProfile` | IMPLEMENTED | `ExpertDiscoveryService.kt`、`DiscoveryIdentity.kt` | 签发调用点各一处：`ExpertDiscoveryService.kt:2269`（论文：`authorEmail.institutionSource`）、`:1527`（ORCID：`DiscoveryIdentity.EVIDENCE_SOURCE_ORCID`，仅当 01b 唯一机构非空时才可能产出）；失效由 `validInstitutionEvidence` 重算保证（测试断言改机构/国家/类型后为 false） |
| I-2 晋升 `_source` 全量透传、学术补全不碰机构/证据 | IMPLEMENTED（未新增逻辑） | — | `promoteDiscoveredToCandidate`（`ExpertDiscoveryService.kt:2307+`）仍为 `rawDoc.toMutableMap()` 拷贝；`updateExpertAcademicFields` 零改动；测试断言 RAW→CANDIDATE token 逐字相同，L2→L1 复用既有 `ExpertIndexWriterServiceTest` 的 `promoteToCandidate deep copies complex source fields` / `promoteToApplication removes promoted document from candidate index` 透传覆盖 |
| I-3 `ExpertProfile.test` 可空 `institutionEvidence`/`filterResult` + 读取投影 | IMPLEMENTED | `ExpertProfile.kt`、`ExpertSearchService.kt` | `ExpertProfile.kt:43-53`（两个新可空字段，末尾追加、默认 null，全部既有构造点用命名参数，零破坏）；`ExpertSearchService.kt:509-511`（`toExpertProfile` 显式读取）、`:595`（`sourceFields()` 投影新增 `filterResult`/`institutionEvidence`）；旧文档无键 → null（测试断言） |
| I-3 `ExpertIndexWriterService.discoveryProfile` 不参与发送门禁 | IMPLEMENTED（未触碰） | — | 该文件不在白名单内，零改动；其机构值仍从原 `_source` 读取 |
| `OperatorStatusWriteSeamGuardTest` 行号漂移时仅改排除清单行号 | 不适用（无需改动） | `OperatorStatusWriteSeamGuardTest.kt`（未修改） | 新增读取行落在钉死点 `ExpertSearchService.kt:499`（`operatorStatus = source.nullableText`）**之后**（`:509-511`、`:595`），`:499` 未平移；该测试在红/绿两次运行均为 `Tests run: 1, Failures: 0` |
| TDD：先红后绿，不改旧期望变绿 | IMPLEMENTED | 两个测试文件 | 红证据 3 组（见下），既有断言零改写（新增用例全部为追加） |
| 范围外：发信拦截、历史回填、线上 `PUT _mapping`、前端/迁移 | 未引入 | — | 无迁移、无脚本、无前端改动；未执行任何 ES/网络操作（仅仓库配置） |

## TDD 证据（红 → 绿）

### 红 #1（行为红：写路径 + mapping，改产品代码前 fresh 运行）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test -Dtest=ExpertDiscoveryServiceTest,ExpertSearchServiceTest,OperatorStatusWriteSeamGuardTest
# exit 1；聚合 Tests run: 240, Failures: 3, Errors: 0, Skipped: 0
```

```
[ERROR] Tests run: 168, Failures: 2, Errors: 0, Skipped: 0 … in …ExpertDiscoveryServiceTest
[ERROR] ORCID discovery signs evidence from the unique institution and carries it to CANDIDATE (I-1 I-2 I-3)
  AssertionFailedError: 01b 的唯一机构必须签发规范 token（ORCID:<64位小写十六进制>），实际: null ==> expected: <true> but was: <false>
[ERROR] discover signs JATS institution evidence and carries it to CANDIDATE (I-1 I-2 I-3)
  AssertionFailedError: 结构机构 + 单一来源必须签发规范 token（JATS:<64位小写十六进制>），实际: null ==> expected: <true> but was: <false>
[ERROR] Tests run: 71, Failures: 1, Errors: 0, Skipped: 0 … in …ExpertSearchServiceTest
[ERROR] all three ES layers declare institutionEvidence as a keyword (I-1)
  AssertionFailedError: orcid_info_raw 必须声明 institutionEvidence keyword ==> expected: <keyword> but was: <>
```
另两条新用例（无结构来源不写键、ORCID 多机构不写键）在改动前即为绿 —— 它们是 I-1 的护栏（旧代码不写这个字段，故天然成立），用于防未来回退。

### 红 #2（行为红：发送投影 `_source`，改产品代码前）

```sh
JAVA_HOME=… mvn -q test -Dtest=ExpertSearchServiceTest   # exit 1；Tests run: 72, Failures: 2
```
```
AssertionFailedError: RAW _source 必须投影 institutionEvidence: [orcidId, orcid, id, email, givenNames, familyNames, country, keyword, employment, age, degree, nationality, hIndex, citationCount, lastPublicationYear, researchFields, researchFieldIds, disciplineCategory, institution, institutionType, emailSource, emailVerifiedLevel, dataSource, externalIds, worksCount, identityVerification, tags, updatedAt, operatorStatus, recentWorkTitles, patentTitles, enrichedAt, enrichmentSource, expertClassification] ==> expected: <true> but was: <false>
```

### 红 #3（新 API 缺失 → 编译红：函数级/golden/冲突/失效/三层读回用例先于实现落盘）

```sh
JAVA_HOME=… mvn -q test-compile   # exit 1
[ERROR] …/ExpertDiscoveryServiceTest.kt: (6745, 39) Unresolved reference: institutionEvidence
[ERROR] …/ExpertDiscoveryServiceTest.kt: (6745, 86) Unresolved reference: EVIDENCE_SOURCE_JATS
[ERROR] …/ExpertDiscoveryServiceTest.kt: (6749, 31) Unresolved reference: validInstitutionEvidence
[ERROR] …/ExpertDiscoveryServiceTest.kt: (6749, 69) Cannot find a parameter with this name: institutionEvidence
… （同类错误覆盖 EVIDENCE_SOURCE_OPENALEX / EVIDENCE_SOURCE_ORCID 与 ExpertSearchServiceTest 的读回断言）
```

### 绿（本次最终状态）

- 命令 1：`exit 0`，聚合 `Tests run: 246, Failures: 0, Errors: 0, Skipped: 0`（`ExpertDiscoveryServiceTest` 171、`ExpertSearchServiceTest` 74、`OperatorStatusWriteSeamGuardTest` 1）—— 11 条新用例全部转绿，`ExpertDiscoveryServiceTest` 里钉死输入顺序的 golden 向量（`JATS:38473ba167e1e5703c47eed9263eda9ad2e7a473854a8422c0a7be320780721c`）与实现一致。
- 未削弱、未改写任何既有断言；两个测试文件均为**追加**（`+227` / `+153` 行）。

## Commands（fresh，本次调用内运行）

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertSearchServiceTest,OperatorStatusWriteSeamGuardTest` | PASS | exit 0；聚合 `Tests run: 246, Failures: 0, Errors: 0, Skipped: 0`（171 / 74 / 1；log `/tmp/cmd1-green.log`） |
| `JAVA_HOME=… mvn test` | **FAIL（1 条，白名单外计数钉子）** | exit 1；`Tests run: 4253, Failures: 1, Errors: 0, Skipped: 13` → `BUILD FAILURE`；唯一失败 `ExpertIndexServiceTest.PUT returns 400 degrades to per-field PUT and does not block remaining indices:170 — AssertionFailedError: RAW batch failure must degrade to per-field PUTs for every declared field ==> expected: <36> but was: <37>`；相关类：`ExpertDiscoveryServiceTest 171/0`、`ExpertSearchServiceTest 74/0`、`OperatorStatusWriteSeamGuardTest 1/0`、`ExpertIndexServiceTest 10/1`（log `/tmp/cmd2-full.log`） |
| `git diff --check` | PASS | exit 0，无输出 |

补充证据（本次调用内另跑）：

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/*.test.js` | PASS | exit 0；`tests 1202 / suites 236 / pass 1202 / fail 0`，与基线 1202 完全一致（唯一红点是 Java 计数钉子，前端零影响）。注：全量 `mvn test` 在 surefire 失败后中止，`exec-maven-plugin` 的 JS 套件未被执行，故单独补跑 |

### 与基线对比

| 指标 | seed 基线 | child 01b post-state | 本次（工作区） | 判定 |
|---|---|---|---|---|
| surefire 聚合 | 4220 / 0 / 0 / 13 | 4242 / 0 / 0 / 13 | 4253 / 1 / 0 / 13 | +11 = 本 child 新增用例数（`ExpertDiscoveryServiceTest` 164→171、`ExpertSearchServiceTest` 70→74）；唯一失败为白名单外计数钉子 |
| `ExpertDiscoveryServiceTest` | 164 / 0 | 164 / 0 | 171 / 0 | +7（JATS 签发+透传、无结构来源不写键、ORCID 签发+透传、ORCID 多机构不写键、golden 向量、ID 冲突拒签、机构变更失效） |
| `ExpertSearchServiceTest` | 70 / 0 | 70 / 0 | 74 / 0 | +4（三层 mapping keyword、三层投影字段、三层读回+旧文档 null、篡改机构失效） |
| `OperatorStatusWriteSeamGuardTest` | 1 / 0 | 1 / 0 | 1 / 0 | 不变（排除清单行号未平移，无需改动） |
| `ExpertIndexServiceTest` | 10 / 0 | 10 / 0 | 10 / **1** | 计划外红点（计数 36→37） |
| Node 套件 | 1202 pass | 1202 pass | 1202 pass / 0 fail | 不变 |

## Changed Files（9 / 白名单 10，全部在 Authorized Files 内；`ExpertIndexServiceGuardTest` 无需改动）

```
src/main/resources/es/orcid_info_raw.json                                    | 1 +
src/main/resources/es/orcid_info_candidate.json                              | 1 +
src/main/resources/es/orcid_info_application.json                            | 1 +
src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt   | 13 +-
src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt | 121 +
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | 21 +-
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt | 6 +-
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | 227 +
src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt | 153 +
9 files changed, 539 insertions(+), 5 deletions(-)
```

- `orcid_info_{raw,candidate,application}.json` — 单一 `institutionEvidence: keyword`（`:29` / `:30` / `:41`）。
- `ExpertProfile.kt:43-53` — 可空 `institutionEvidence`、`filterResult`（末尾追加、默认 null）。
- `DiscoveryIdentity.kt:72-193` — token 词表常量、唯一签发/验签函数、固定输入顺序、ID 冲突前置。
- `ExpertDiscoveryService.kt:1526-1527`（ORCID 签发）、`:2268-2280`（论文签发 + `withInstitutionEvidence` helper）、`:2302-2304`（写入：无证据不写键）。
- `ExpertSearchService.kt:509-511`（读取）、`:595`（投影）。
- `ExpertDiscoveryServiceTest.kt:6615-6841`（7 条新用例 + 3 个 fixture helper）。
- `ExpertSearchServiceTest.kt:2396-2543`（4 条新用例 + 2 个 helper）。

## 下游接口（供 child 03，逐字冻结）

- token 格式：`<来源种类>:<64位小写SHA256>`，来源种类三类：`DiscoveryIdentity.EVIDENCE_SOURCE_JATS` = `"JATS"`、`EVIDENCE_SOURCE_OPENALEX` = `"OPENALEX"`、`EVIDENCE_SOURCE_ORCID` = `"ORCID"`。
- 唯一签发/验签函数对（同一份实现，签发与验签同口径）：
  - `DiscoveryIdentity.institutionEvidence(profile: ExpertProfile, source: String): String?`（null = 不写键）
  - `DiscoveryIdentity.validInstitutionEvidence(profile: ExpertProfile): Boolean`（只读已存字段重算，含 ID 一致性；`institutionEvidence == null` → false）
- 固定输入顺序（NUL 分隔，逐字）：来源种类 → `identityVerification.source` → `identityVerification.evidenceHash` → 规范邮箱 → `"givenNames familyNames"` → 来源作者 ID（OPENALEX = `externalIds.openAlexAuthorId`；ORCID = `externalIds.orcid`；JATS = `externalIds.orcid` 或空）→ 论文 ID 或 ORCID ID（论文 = `pmcId→doi→pmid`；ORCID = `externalIds.orcid`）→ 机构 → 国家（空则空串）→ 机构类型（空则空串）。
- 读取投影：`ExpertProfile.institutionEvidence` / `ExpertProfile.filterResult` 由 `ExpertSearchService.toExpertProfile` 从 ES `_source` 显式读取（`sourceFields()` 已点名两字段）；旧文档 → 两者均为 `null`。
- **签发/读取的 ID 字段语义差异（03 必须注意）**：写入时 `profile.orcidId` 是作者的 ORCID 值（`buildProfile`/`buildOrcidProfile`），读取时 `profile.orcidId` 是 ES 主键（`EMAIL-*`）。函数**不把 `orcidId` 放进哈希**，且「真实 ORCID 主键（非 EMAIL-*）与 ORCID 值不得冲突」只在主键确为真实 ORCID 时才生效，因此写入侧与读取侧的 token 逐字相同（已由三层读回用例证明）。03 只应调用 `validInstitutionEvidence(profile)`，不得自行重算或改写任一字段。
- `ExpertIndexWriterService.discoveryProfile` **不**填充这两个字段（未授权改动），发送门禁必须使用 `ExpertSearchService` 的投影。

## Deviations

- 未新建白名单外文件、未改 `docs/plans/**`（除本报告）、未 push/merge/rebase/squash/amend/reset、未跑格式化/lint、未触碰主工作区、未执行任何 ES/网络操作。
- **唯一偏差**：全量 `mvn test` 有 1 条红，来源是白名单外 `ExpertIndexServiceTest.kt:170` 的计数钉子。按 brief 规则 5 与 Main 裁决：**不擅自修改该文件、不提交**，返回 `PLAN_CONFLICT` 等待人工批准计划修订（一行期望值 36→37 + 注释同步）。除此之外，02 的实现与三条必需命令中的两条（命令 1、命令 3）均为全绿。
- 计划未指定 token 的具体输入编码（只规定「NUL 分隔固定顺序」与字段清单）。本次在计划文本的字面顺序内实现，并用 golden 向量测试把该顺序钉死（`DiscoveryIdentity.kt:124-140`、`ExpertDiscoveryServiceTest.kt:6743-6752`），以便下游 03 与未来改动都能被同一测试发现。
- `sourceAuthorId`/论文 ID 的具体取值来源（`externalIds` 子键）在计划里只写「已存 externalIds 中论文 ID 或 ORCID ID」，本次实现为 `pmcId→doi→pmid`（论文来源）与 `externalIds.orcid`（ORCID 来源），并在函数注释中固定。

## Freshness

- Plan identity rechecked: YES（执行前与冻结前 `plan_identity.py` 均为 `8786cbe8…0c1c`）
- Worktree identity rechecked: YES（`worktree_identity.py --expect-root/--expect-branch` 通过；HEAD `f5bff23`，与 child_base_sha 的代码差异为空）
- Reported commits reachable from target branch: N/A（**未提交**，按 Main 裁决）
- Required commands run this invocation: YES（命令 1/2/3 + 3 组红证据 + Node 套件均在本次调用内 fresh 运行）
- Historical evidence used only as baseline: YES（`children/01/baseline.md`、child 01b post-state 计数仅用于对比）

## Remaining Blocker

- 需要**人工**批准的计划修订（Main 已明确自身无权授予）：把 `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` 加入 child 02 的 Authorized Files，授权**仅**做一处机械期望值更新 —— `:170` 的 `36` → `37`（断言与其消息不变），并同步 `:169` 注释（说明本轮新增 `institutionEvidence`）。
- 完成该行后：命令 2 预期 `4253 / 0 / 0 / 13`（exit 0），届时可提交 `feat(fast-p): implement 02`（单个提交，排除 `docs/plans/fast/**`）并转 `READY_FOR_VERIFICATION`。

## Next Action

- PLAN_CONFLICT → 人工决定：批准上述一行修订（并授权该文件）后重新派发本 child 收尾（应用该行 → 重跑命令 2 → 单提交 → `READY_FOR_VERIFICATION`）；或改为在 03 中一并修订。当前工作区未提交改动须原样保留。
