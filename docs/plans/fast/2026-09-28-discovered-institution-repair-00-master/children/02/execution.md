# Child 02 执行报告 — 机构来源证据落库

## Execution Result: READY_FOR_VERIFICATION（epoch 2：A1 修订后的最终状态）

- Plan（完整合同，epoch 2 = A1 修订版）: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master/docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md`
- Plan SHA-256（epoch 2）: `f1803069d02e9c06d78c09db688a9e2a85a31fe0000f74b76d6e5ee1932355d7`（`size_bytes 9552`）
- Execution ID（epoch 2）: `docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md@f1803069d02e9c06d78c09db688a9e2a85a31fe0000f74b76d6e5ee1932355d7`
- Execution epoch: `RESUME`（同一 child 的第二个执行纪元：epoch 1 因 `PLAN_CONFLICT` 暂停并留下未提交实现；人工批准 A1 后按新合同 hash 恢复 —— epoch-1 合同 SHA `8786cbe8553f3be00b7377a3bcf04be31be4477c0d6163b3381ae6be4a210c1c`，记录见文末附录）
- Approval basis: 人工批准 A1（Main 转达）＋ 计划修订提交 `a90f59d8d57dea33d83b571bbb62c5f389d387d7`（`docs(plans): authorize child 02 mapping-count expectation update (A1)`）
- Executor: `Implement02`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Target branch: `fast/2026-09-28-discovered-institution-repair-00-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master@fast/2026-09-28-discovered-institution-repair-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Pre-execution code SHA（`child_base_sha`）: `68ad011971ac4cbafdd439cfe2d981476ff1332a`；epoch-2 起点本地 HEAD = `39b884242ca19b8d1503f1efdae8f37e3ff6efd4`（`docs(fast-p): pause 02 and record A1 amendment`），其与 child_base_sha 之间的 `src/**` 差异为空（只有 docs 提交）
- Post-execution code SHA（= Evidence HEAD）: **`feat(fast-p): implement 02` → `c30c954761b199467c7d904a50b177808f54ddce`**（单个提交，父亲 `39b8842`，10 个文件，`docs/plans/fast/**` 全部排除；该提交即 `TARGET_WORKTREE` HEAD 与目标分支 HEAD 且为其祖先）
- Implementation boundary: `39b884242ca19b8d1503f1efdae8f37e3ff6efd4..c30c954761b199467c7d904a50b177808f54ddce`
- 身份工具：`~/.agents/skills/execute-p/scripts/plan_identity.py`、`worktree_identity.py`（含 `--expect-root/--expect-branch`）在 epoch 2 恢复时与提交前各取一次，输出一致

## A1 修订（本纪元授权的唯一范围扩张）

| 项 | 内容 |
|---|---|
| 修订提交 | `a90f59d8d57dea33d83b571bbb62c5f389d387d7`（`变更文件清单` 追加 1 行） |
| 逐字授权 | 「`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` | A1：mapping 新增字段致 RAW 顶层属性计数变化时，仅按语义更新计数期望（36→37）与紧邻注释，断言与语义不变」 |
| 实际应用 | `ExpertIndexServiceTest.kt:170`：`assertEquals(36, singleFieldPuts, "RAW batch failure must degrade to per-field PUTs for every declared field")` → `assertEquals(37, …)`；`:169` 注释同步为 `// researchFieldIds and institutionEvidence (02) are now declared alongside the existing RAW properties.` |
| 边界遵守 | 该文件仅此 2 行变更（`1 file changed, 2 insertions(+), 2 deletions(-)`），断言消息、"every declared field" 语义、`batchPuts` 断言均未放宽或删除 |

## 冲突与解决（epoch 1 → A1）

epoch 1 结束时全量 `mvn test` 唯一红点：`ExpertIndexServiceTest.kt:170` 把 RAW mapping **顶层属性个数**钉死为 36，而三层 mapping 各新增 1 个 `institutionEvidence`（I-1 强制）使 RAW 变为 37。计数证据（`jq`）：

```sh
$ jq '.mappings.properties | length' src/main/resources/es/orcid_info_raw.json                                    # 37（本次改动后）
$ git show HEAD:src/main/resources/es/orcid_info_raw.json | jq '.mappings.properties | length'                     # 36（child base）
$ jq '.mappings.properties | length' src/main/resources/es/orcid_info_candidate.json                              # 39 / base 38
$ jq '.mappings.properties | length' src/main/resources/es/orcid_info_application.json                            # 49 / base 48
```

该文件不在 02 的 10 文件清单内 → 按 brief 规则 5 返回 `PLAN_CONFLICT`（未提交、未改该文件）；Main 确认冲突属实且需人工批准。**解决**：人工批准 A1 → 计划新增该文件与该一行语义更新 → 本纪元应用后全量测试回绿。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 三层 mapping 新增且仅新增 `institutionEvidence` keyword | IMPLEMENTED | `orcid_info_raw.json:29`、`orcid_info_candidate.json:30`、`orcid_info_application.json:41` | 三处均为 `"institutionEvidence": { "type": "keyword" }`；根 `dynamic:false` 未变；`ExpertSearchServiceTest` 逐层读资源断言 keyword；`jq` 计数三层各 +1 |
| A1：RAW 顶层属性计数期望随语义更新 | IMPLEMENTED | `ExpertIndexServiceTest.kt:169-170` | `36 → 37` + 紧邻注释同步；`ExpertIndexServiceTest` 10/0/0/0 转绿；无其它改动 |
| I-1 统一签发/验签函数（NUL 分隔固定顺序、值域、拒签） | IMPLEMENTED | `DiscoveryIdentity.kt:72-193` | `EVIDENCE_SOURCE_JATS/OPENALEX/ORCID`（`:80-82`）、`institutionEvidence(profile, source)`（`:93-110`）、`validInstitutionEvidence(profile)`（`:116-121`）、`tokenDigestInput`（`:124-140`）；golden 向量测试钉死输入顺序 |
| I-1 不写键语义（空机构/多机构/身份未证实/来源不匹配） | IMPLEMENTED | `ExpertDiscoveryService.kt:2268-2280, 2302-2304` | `withInstitutionEvidence`（来源只认 01 内部 `institutionSource` 或显式 `ORCID`，否则原样返回）；`profile.institutionEvidence?.let { … }` → 无证据**不写键**（不写 null/false/UNVERIFIED） |
| I-2 签发前置：ID 冲突、各来源必需来源 ID | IMPLEMENTED | `DiscoveryIdentity.kt:103-108, 145-152` | 凭证 ORCID/OpenAlex 作者 ID vs `externalIds`；真实 ORCID 主键（非 `EMAIL-*`）vs ORCID 值；JATS 必须 `pmcId`；OPENALEX 必须同作者 `openAlexAuthorId` 且有 `doi`/`pmcId`；ORCID 必须 `externalIds.orcid` |
| I-2 机构/国家/类型变更 → 旧 token 失效；签发点唯一 | IMPLEMENTED | `ExpertDiscoveryService.kt:1527, 2269`、`DiscoveryIdentity.kt` | 签发各一处（ORCID：`:1527`；论文：`:2269` 传 `authorEmail.institutionSource`）；失效由验签重算保证（测试断言改机构/国家/类型后为 false） |
| I-2 晋升 `_source` 全量透传、学术补全不碰机构/证据 | IMPLEMENTED（未新增逻辑） | — | `promoteDiscoveredToCandidate` 仍为 `rawDoc.toMutableMap()` 拷贝；`updateExpertAcademicFields` 零改动；测试断言 RAW→CANDIDATE token 逐字相同；L2→L1 复用既有 `ExpertIndexWriterServiceTest` 的 `promoteToCandidate deep copies complex source fields` / `promoteToApplication removes promoted document from candidate index` |
| I-3 `ExpertProfile` 可空 `institutionEvidence`/`filterResult` + 读取投影 | IMPLEMENTED | `ExpertProfile.kt:43-53`、`ExpertSearchService.kt:509-511, 595` | 两个新可空字段末尾追加（默认 null，既有构造点全为命名参数）；`toExpertProfile` 显式读取；`sourceFields()` 点名两字段；旧文档无键 → null |
| I-3 `ExpertIndexWriterService.discoveryProfile` 不参与发送门禁 | IMPLEMENTED（未触碰） | — | 该文件零改动，机构值仍从原 `_source` 读取 |
| `OperatorStatusWriteSeamGuardTest` 行号漂移时仅改行号 | 不适用（无需改动） | `OperatorStatusWriteSeamGuardTest.kt`（未修改） | 新增读取行落在钉死点 `ExpertSearchService.kt:499` 之后（`:509-511`、`:595`），未平移；该测试各次运行均 1/0/0/0 |
| TDD：先红后绿，不改旧期望变绿 | IMPLEMENTED | 两个测试文件 | 红证据 3 组（见下）；既有断言零改写，新增用例全部为追加 |
| 范围外：发信拦截、历史回填、线上 `PUT _mapping`、前端/迁移 | 未引入 | — | 无迁移、无脚本、无前端改动；未执行任何 ES/网络操作 |

## TDD 证据

### 红 #1（行为红：写路径 + mapping，实现前 fresh 运行）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test -Dtest=ExpertDiscoveryServiceTest,ExpertSearchServiceTest,OperatorStatusWriteSeamGuardTest
# exit 1；聚合 Tests run: 240, Failures: 3, Errors: 0, Skipped: 0
```
```
[ERROR] Tests run: 168, Failures: 2 … in …ExpertDiscoveryServiceTest
  discover signs JATS institution evidence and carries it to CANDIDATE (I-1 I-2 I-3):
    结构机构 + 单一来源必须签发规范 token（JATS:<64位小写十六进制>），实际: null ==> expected: <true> but was: <false>
  ORCID discovery signs evidence from the unique institution and carries it to CANDIDATE (I-1 I-2 I-3):
    01b 的唯一机构必须签发规范 token（ORCID:<64位小写十六进制>），实际: null ==> expected: <true> but was: <false>
[ERROR] Tests run: 71, Failures: 1 … in …ExpertSearchServiceTest
  all three ES layers declare institutionEvidence as a keyword (I-1):
    orcid_info_raw 必须声明 institutionEvidence keyword ==> expected: <keyword> but was: <>
```
两条新护栏用例（无结构来源不写键、ORCID 多机构不写键）在改动前即为绿（旧代码本就不写该字段），用于防未来回退。

### 红 #2（行为红：发送投影 `_source`，实现前）

```sh
JAVA_HOME=… mvn -q test -Dtest=ExpertSearchServiceTest   # exit 1；Tests run: 72, Failures: 2
```
```
RAW _source 必须投影 institutionEvidence: [orcidId, orcid, id, email, givenNames, familyNames, country, keyword,
employment, age, degree, nationality, hIndex, citationCount, lastPublicationYear, researchFields, researchFieldIds,
disciplineCategory, institution, institutionType, emailSource, emailVerifiedLevel, dataSource, externalIds,
worksCount, identityVerification, tags, updatedAt, operatorStatus, recentWorkTitles, patentTitles, enrichedAt,
enrichmentSource, expertClassification] ==> expected: <true> but was: <false>
```

### 红 #3（新 API 缺失 → 编译红，实现前）

```sh
JAVA_HOME=… mvn -q test-compile   # exit 1
[ERROR] ExpertDiscoveryServiceTest.kt: (6745, 39) Unresolved reference: institutionEvidence
[ERROR] ExpertDiscoveryServiceTest.kt: (6745, 86) Unresolved reference: EVIDENCE_SOURCE_JATS
[ERROR] ExpertDiscoveryServiceTest.kt: (6749, 31) Unresolved reference: validInstitutionEvidence
[ERROR] ExpertDiscoveryServiceTest.kt: (6749, 69) Cannot find a parameter with this name: institutionEvidence
…（同类错误覆盖 EVIDENCE_SOURCE_OPENALEX / EVIDENCE_SOURCE_ORCID 与 ExpertSearchServiceTest 的读回断言）
```

### 绿（epoch 2 最终状态）

- 命令 1：exit 0，`Tests run: 246, Failures: 0, Errors: 0, Skipped: 0`（171 / 74 / 1）。
- 命令 2：exit 0，`BUILD SUCCESS`，surefire `Tests run: 4253, Failures: 0, Errors: 0, Skipped: 13`，构建内 JS 门禁 `tests 1202 / pass 1202 / fail 0`。
- golden 向量测试通过：`JATS:38473ba167e1e5703c47eed9263eda9ad2e7a473854a8422c0a7be320780721c`（钉死 NUL 分隔输入顺序）。
- 未削弱、未改写任何既有断言；两个测试文件均为追加（`+227` / `+153` 行）。

## Commands（epoch 2 fresh，本次调用内最终状态上运行）

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertSearchServiceTest,OperatorStatusWriteSeamGuardTest` | PASS | exit 0；聚合 `Tests run: 246, Failures: 0, Errors: 0, Skipped: 0`（`ExpertDiscoveryServiceTest` 171、`ExpertSearchServiceTest` 74、`OperatorStatusWriteSeamGuardTest` 1；log `/tmp/cmd1-epoch2.log`） |
| `JAVA_HOME=… mvn test` | PASS | exit 0；`BUILD SUCCESS`；surefire 聚合 `Tests run: 4253, Failures: 0, Errors: 0, Skipped: 13`；`ExpertIndexServiceTest` 10/0（A1 后转绿）；JS 套件 `tests 1202 / pass 1202 / fail 0`（log `/tmp/cmd2-epoch2.log`） |
| `git diff --check` | PASS | exit 0，无输出 |

### 与基线对比

| 指标 | seed 基线 | child 01b post-state | 本次（epoch 2） | 判定 |
|---|---|---|---|---|
| surefire 聚合 | 4220 / 0 / 0 / 13 | 4242 / 0 / 0 / 13 | 4253 / 0 / 0 / 13 | +11 = 本 child 新增用例（`ExpertDiscoveryServiceTest` 164→171、`ExpertSearchServiceTest` 70→74）；无新增失败 |
| `ExpertDiscoveryServiceTest` | 164 / 0 | 164 / 0 | 171 / 0 | +7 |
| `ExpertSearchServiceTest` | 70 / 0 | 70 / 0 | 74 / 0 | +4 |
| `OperatorStatusWriteSeamGuardTest` | 1 / 0 | 1 / 0 | 1 / 0 | 不变（排除清单行号未平移） |
| `ExpertIndexServiceTest` | 10 / 0 | 10 / 0 | 10 / 0 | A1 更新后恢复全绿 |
| Node 套件 | 1202 pass | 1202 pass | 1202 pass / 0 fail | 不变 |

## Changed Files（10 / 10 Authorized Files）

```
src/main/resources/es/orcid_info_raw.json                                        | 1 +
src/main/resources/es/orcid_info_candidate.json                                  | 1 +
src/main/resources/es/orcid_info_application.json                                | 1 +
src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt       | 13 +-
src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt   | 121 +
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | 21 +-
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt | 6 +-
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | 227 +
src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt | 153 +
src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt | 2 +-（A1）
10 files changed, 541 insertions(+), 7 deletions(-)
```

- `orcid_info_{raw,candidate,application}.json` — 单一 `institutionEvidence: keyword`。
- `ExpertProfile.kt:43-53` — 可空 `institutionEvidence`、`filterResult`。
- `DiscoveryIdentity.kt:72-193` — token 词表、唯一签发/验签、固定输入顺序、ID 冲突前置。
- `ExpertDiscoveryService.kt:1526-1527`（ORCID 签发）、`:2268-2280`（论文签发 + helper）、`:2302-2304`（写入：无证据不写键）。
- `ExpertSearchService.kt:509-511`（读取）、`:595`（投影）。
- `ExpertDiscoveryServiceTest.kt:6615-6841`（7 条新用例 + 3 个 fixture helper）。
- `ExpertSearchServiceTest.kt:2396-2543`（4 条新用例 + 2 个 helper）。
- `ExpertIndexServiceTest.kt:169-170` — A1 一行计数期望 + 注释。

## 下游接口（供 child 03，逐字冻结）

- token 格式：`<来源种类>:<64位小写SHA256>`；来源种类 = `DiscoveryIdentity.EVIDENCE_SOURCE_JATS`(`"JATS"`) / `EVIDENCE_SOURCE_OPENALEX`(`"OPENALEX"`) / `EVIDENCE_SOURCE_ORCID`(`"ORCID"`)。
- 唯一函数对：`DiscoveryIdentity.institutionEvidence(profile, source): String?`（null = 不写键）与 `DiscoveryIdentity.validInstitutionEvidence(profile): Boolean`（只读已存字段重算，含 ID 一致性；`institutionEvidence == null` → false）。
- 固定输入顺序（NUL 分隔，逐字）：来源种类 → `identityVerification.source` → `identityVerification.evidenceHash` → 规范邮箱 → `"givenNames familyNames"` → 来源作者 ID（OPENALEX = `externalIds.openAlexAuthorId`；ORCID/JATS = `externalIds.orcid`，JATS 缺则空）→ 论文 ID 或 ORCID ID（论文 = `pmcId→doi→pmid`；ORCID = `externalIds.orcid`）→ 机构 → 国家（空则空串）→ 机构类型（空则空串）。
- 读取投影：`ExpertProfile.institutionEvidence` / `filterResult` 由 `ExpertSearchService.toExpertProfile` 从 ES `_source` 读出（`sourceFields()` 已点名）；旧文档 → 均为 null（不默认合格）。
- **ID 字段语义差异**：写入时 `profile.orcidId` 是作者 ORCID 值，读取时是 ES 主键（`EMAIL-*`）。函数不把 `orcidId` 放进哈希，「真实 ORCID 主键 vs ORCID 值冲突」仅当主键确为真实 ORCID 时生效，因此写入侧与读取侧 token 逐字相同（已由三层读回用例证明）。03 只应调用 `validInstitutionEvidence(profile)`，不得自行重算或改写任一字段。
- `ExpertIndexWriterService.discoveryProfile` **不**填充这两个字段（未授权改动），发送门禁必须使用 `ExpertSearchService` 的投影。

## Deviations

- 无范围偏差（epoch 2）：10/10 Authorized Files（A1 后），未新建白名单外文件，未改 `docs/plans/**`（计划文件由控制方提交），未 push/merge/rebase/squash/amend/reset，未跑格式化/lint，未触碰主工作区，未执行任何 ES/网络操作。
- 计划未指定 token 的具体输入编码（只规定「NUL 分隔固定顺序」与字段清单）。本次在计划字面顺序内实现，并用 golden 向量测试把顺序钉死（`DiscoveryIdentity.kt:124-140`、`ExpertDiscoveryServiceTest.kt` golden 用例），使下游 03 与未来改动都能被同一测试发现。
- `sourceAuthorId` 与论文 ID 的具体取值（`externalIds` 子键选择）在计划里只写「已存 externalIds 中论文 ID 或 ORCID ID」，本次实现为 `pmcId→doi→pmid`（论文来源）与 `externalIds.orcid`（ORCID 来源），并在函数注释中固定。
- epoch 1 的 `PLAN_CONFLICT` 属协议内流程（需要白名单外文件），非实现缺陷；本条记录保留在文末附录。

## Freshness

- Plan identity rechecked: YES（epoch-2 恢复时与提交前 `plan_identity.py` 均为 `f1803069…55d7`；epoch-1 合同 SHA `8786cbe8…0c1c` 仅存于附录）
- Worktree identity rechecked: YES（`worktree_identity.py --expect-root/--expect-branch` 通过；提交前 HEAD `39b8842`，分支 `fast/2026-09-28-discovered-institution-repair-00-master`）
- Reported commits reachable from target branch: YES（`c30c954761b199467c7d904a50b177808f54ddce` = `TARGET_WORKTREE` HEAD = 目标分支 HEAD，`git merge-base --is-ancestor` 通过）
- Required commands run this invocation: YES（命令 1/2/3 在 epoch-2 最终状态上 fresh 运行；红证据 3 组在 epoch 1 同一工作区同一实现前运行）
- Historical evidence used only as baseline: YES（`children/01/baseline.md`、child 01b post-state 计数仅用于对比）

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`。
- 人工验收（master plan A-1/A-2/A-3、child 02 A-1/A-2/A-3）需真实 ES 三层 `GET _mapping`、发现/晋升/补全运行与目视，属 whole-system 人工门禁，不在本 child 执行范围。

---

## 附录：epoch-1 记录（原 `PLAN_CONFLICT`，保留备查）

- epoch-1 Plan SHA-256：`8786cbe8553f3be00b7377a3bcf04be31be4477c0d6163b3381ae6be4a210c1c`（`size_bytes 9312`，无 A1 行）。
- epoch-1 结论：实现完成、命令 1（246/0/0/0）与命令 3（`git diff --check` exit 0）通过；命令 2 `mvn test` = `Tests run: 4253, Failures: 1, Errors: 0, Skipped: 13`（exit 1，`BUILD FAILURE`），唯一失败逐字为：
  `ExpertIndexServiceTest.PUT returns 400 degrades to per-field PUT and does not block remaining indices:170 — AssertionFailedError: RAW batch failure must degrade to per-field PUTs for every declared field ==> expected: <36> but was: <37>`
- epoch-1 该次构建中 surefire 失败后中止，`exec-maven-plugin` 的 JS 套件未执行，故另行补跑 `node --test src/test/js/*.test.js` → exit 0，`tests 1202 / suites 236 / pass 1202 / fail 0`。
- epoch-1 处置：按 brief 规则 5 返回 `PLAN_CONFLICT`，**未提交**、未改 `ExpertIndexServiceTest.kt`（Main 裁决：冲突属实、需人工批准、工作区保持未提交原样）；随后人工批准 A1（epoch-2 计划 hash `f1803069…55d7`），本纪元仅应用授权的 2 行并完成收尾。
- epoch-1 各处行号/计数证据（`jq` 三层属性数 36→37 / 38→39 / 48→49、`ExpertIndexServiceTest.kt:169-170` 原文）与红 #1/#2/#3 逐字输出见上文对应小节。
