# c5 执行报告 — 保留可信作者身份

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/05-author-identity.md
Plan SHA-256: a8b284677711183edca265d034194ddbdcd3feee0ed9563077fa5e8bcc0bd4ee
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/05-author-identity.md@a8b284677711183edca265d034194ddbdcd3feee0ed9563077fa5e8bcc0bd4ee
Execution epoch: NEW
Approval basis: fast-p 运行 `docs/plans/2026-09-21/00-discovery-enrichment-master.md`（计划身份 `commit:831e6604cf97e7acba005d8f00827659b49ce010`）授权的本地提交；本子计划契约 = 上述 plan 文件 + `children/c5/brief.md`。执行技能 `skill://execute-p`。
Executor: C5Implementer（fast-p child c5，单一写入者）
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Target branch: fast/2026-09-21-discovery-enrichment-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master@fast/2026-09-21-discovery-enrichment-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Pre-execution code SHA: 5ed8de19c84b68d42f42b1e621a705dbd08f35c1（c4 证据提交；c4 code head = 985f1ddf5891bdf534fbfeb2e5140ce6fd3f254b = 本子计划 child_base_sha）
Post-execution code SHA: 1ba685217a166628968a798450ff203191ead79c
Evidence HEAD: N/A（本子计划只要求一个实现提交；本报告位于 `docs/plans/fast/**`，按 brief 不纳入提交）
Implementation boundary: 5ed8de19c84b68d42f42b1e621a705dbd08f35c1..1ba685217a166628968a798450ff203191ead79c

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Task 1 / I-1 两个 DTO 增可空默认字段 `openAlexAuthorId`，规范为 `A`+数字，最终存入既有 `externalIds.openAlexAuthorId`，ES `_id`/`orcidId` 语义不变 | IMPLEMENTED | PaperAuthor.kt, AuthorEmail.kt, OpenAlexDataSource.kt, ExpertDiscoveryService.kt, OpenAlexDataSourceTest.kt, ExpertDiscoveryServiceTest.kt | `PaperAuthor`/`AuthorEmail` 末尾新增 `openAlexAuthorId: String? = null`（`@JvmOverloads` 保持 Java 构造点兼容）；`normalizeOpenAlexAuthorId()` 只接受 `A\d+`（去 `https://openalex.org/` 前缀）；`parseResponse` 写入 `authorships[].author.id`；`buildExternalIds` 合并写入子键。断言：OpenAlexDataSourceTest「keeps the OpenAlex author id of every authorship (I-1)」（`A5023888391`/`A5086928770` 保留，`W9` 被拒）；ExpertDiscoveryServiceTest「merges the OpenAlex author id into externalIds without dropping the import ids (I-1, I-3)」（主键仍 `0000-0001`，externalIds = pmcId+doi+pmid+orcid+openAlexAuthorId 五项全在）与「never stores a non-canonical author id in externalIds (I-1)」 |
| Task 1 / V-1 有作者ID无ORCID仍存 EMAIL-* 主键，子键随三层晋升存活 | IMPLEMENTED | ExpertDiscoveryService.kt, ExpertDiscoveryServiceTest.kt | 主键生成 `ExpertIdGenerator.generate(authorEmail.orcidId, …)` 未改；断言 ExpertDiscoveryServiceTest「keeps the EMAIL-* primary key and promotes the author id to CANDIDATE (V-1)」：RAW 写入 id 以 `EMAIL-` 开头、`promoted=1`、CANDIDATE PUT 文档 `externalIds.openAlexAuthorId=A5086928770`（晋升复制整份文档，未改晋级路径） |
| Task 2 / I-2 PDF/CORE 首字母宽松匹配不再绑定作者ID/ORCID | IMPLEMENTED | PdfEmailExtractor.kt, CoreDataSource.kt, PdfEmailExtractorTest.kt, CoreDataSourceTest.kt | 删除 `localPart.contains(family.take(1))`/`contains(given.take(1))` 兜底；改用统一接缝 `hasStrongEmailNameEvidence`（本地部分须同时含完整姓与名，token ≥2 字符）+ `verifiedAuthorFor`（`matches.singleOrNull() ?: authors.singleOrNull()?.takeIf { emailCount == 1 }`），歧义时保留邮箱、身份全为 null。断言：两测试类各 3 例（共享首字母+姓、同名、姓氏子串 `li ⊂ lian` 均不绑定；唯一完整姓名组合与唯一作者+唯一邮箱可绑定并携带 orcid+作者ID） |
| Task 2 / I-2 PMC 提取结果只在 ORCID 精确等值时补接 OpenAlex ID | IMPLEMENTED | OpenAlexDataSource.kt, OpenAlexDataSourceTest.kt | `extractAuthorEmails` 的 PMC 分支对 europePmc 结果做 ORCID 精确对齐：`verifiedAuthorIdByOrcid` 要求邮箱 ORCID 与作者 ORCID 精确等值且对应唯一作者 ID（同一 ORCID 多个不同 ID → null）；EuropePMC 自身行为未改（未授权文件）。断言：OpenAlexDataSourceTest 三条（精确对齐 → `A5023888391`；两个作者共享同一 ORCID（A1/A2）→ null；邮箱无 ORCID 或 ORCID 不匹配 → null） |
| Task 2 / I-3 externalIds 只作子键合并写入、绝不整体替换，不新增 mapping/迁移 | IMPLEMENTED | OpenAlexDataSource.kt, ExpertDiscoveryService.kt | `buildExternalIds` 在既有 `mutableMapOf` 上追加 `openAlexAuthorId`（doi/pmid/pmcId/orcid 全部保留）；未新增 ES mapping 字段（子键位于 `enabled:false` 的 `externalIds` 对象内）；全仓 grep `externalIds` 确认无 `exists`/`term` 子字段查询、无 Flyway 迁移改动 |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,CoreDataSourceTest,ExpertDiscoveryServiceTest`（提交 1ba6852 之后的最终状态，本子计划 Required command） | PASS | 退出码 0；`Tests run: 167, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`（ExpertDiscoveryServiceTest 88 / OpenAlexDataSourceTest 41 / PdfEmailExtractorTest 22 / CoreDataSourceTest 16） |
| 同一命令（先加数据契约与全部验收场景、未改行为时运行，仅作过程记录） | FAIL | 退出码 1；`Tests run: 167, Failures: 11, Errors: 9`。行为红：`PdfEmailExtractorTest … two authors share the email initial and surname:281 expected: <null> but was: <John>`、`… surname-substring collision:318 expected: <null> but was: <Li>`、`CoreDataSourceTest … first-initial match:237 expected: <null> but was: <John>`、`OpenAlexDataSourceTest … author id of every authorship:755 expected: <A5023888391> but was: <null>`、`ExpertDiscoveryServiceTest … merges the OpenAlex author id:499 expected: <{…, openAlexAuthorId=A5023888391}> but was: <{pmcId, doi, pmid, orcid}>`。非交付状态。 |
| 同一命令（实现后、修掉测试自身的 Mockito 取参错误前，仅作过程记录） | FAIL | 退出码 1；`Tests run: 167, Errors: 1` — `discover keeps the EMAIL-* primary key…:549 NoSuchElement`（测试用 `getArgument(1)` 取到 HttpMethod 而非 HttpEntity，异常被 `promoteDiscoveredToCandidate` 的 catch 吞掉）。仅测试代码问题，已改为 `getArgument(2)` 并补 `promoted=1` 断言。非交付状态。 |

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperAuthor.kt` — 新增可空默认字段 `openAlexAuthorId`（`@JvmOverloads`，保留既有构造点）。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/AuthorEmail.kt` — 同上；该字段是邮箱线索与可信学术身份之间的唯一携带位。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` — `parseResponse` 保留每个署名的 `author.id`；`normalizeOpenAlexAuthorId()`（`A`+数字）与 `normalizeOrcid()`；PMC 分支按 ORCID 精确等值补接作者 ID。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` — 删除首字母兜底；新增共享接缝 `hasStrongEmailNameEvidence` / `verifiedAuthorFor`（PDF 与 CORE 唯一一份规则）。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` — `associateEmails` 改用同一接缝，歧义不携带身份。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — `buildExternalIds` 合并写入 `openAlexAuthorId` 子键（I-1/I-3）。
- `src/test/kotlin/.../PdfEmailExtractorTest.kt`、`CoreDataSourceTest.kt`、`OpenAlexDataSourceTest.kt`、`ExpertDiscoveryServiceTest.kt` — 新增 I-1/I-2/I-3/V-1/V-3 的请求与持久化边界断言（共 12 个新用例）。

### Deviations

- **两个 DTO 使用 `@JvmOverloads`（Kotlin 默认参数对 Java 不可见）**：唯一 Java 构造点 `src/test/java/.../DiscoveryMockHelper.java:190` 以 7 个实参构造 `AuthorEmail`，该文件不在授权清单内，且 brief 要求「Keep the existing `PaperAuthor` / `AuthorEmail` constructor call sites compiling」。`@JvmOverloads` 生成 6/7/8 参重载，旧调用点原样编译，未新增字段以外语义；`PaperAuthor` 无 Java 构造点，同一注解仅为对称（不改变 Kotlin 调用点）。
- **V-2「唯一完整姓名组合」的判定口径**：实现为「邮箱本地部分（小写字母数字）同时包含完整姓与完整名，且两侧 token ≥2 字符，且命中作者唯一」。计划片段只给出 `hasStrongEmailNameEvidence` 名称与 `singleOrNull()` 结构，未逐字规定强度函数；本实现取计划内最严解释（首字母/单名不算证据），避免把弱匹配升级为学术身份。
- **CORE 强匹配时同时携带 `institutionType`**：改动前 `CoreDataSource.associateEmails` 用 6 参构造、丢弃了 `institutionType`（PDF 路径已携带）。改为与 PDF 共用同一构造后，强匹配作者机构类型也会随身份一起写入；这是同一「作者身份」传播规则的一致性修正，未放宽匹配证据。
- **未授权文件保持原样**：`DiscoveryMockHelper.java`（Java 构造点）、`EuropePmcDataSource.kt`、`JatsXmlEmailParser.kt`、`OrcidDataSource.kt`、Crossref/arXiv/PMC OA 均未改动；`docs/plans/fast/**`（含被主控修改的 `ledger.md`）未 `git add`，保持工作区未提交状态；未触碰已应用迁移与 ES mapping。

### Freshness

- Plan identity rechecked: YES（执行结束时重算，仍为 `a8b284677711183edca265d034194ddbdcd3feee0ed9563077fa5e8bcc0bd4ee`，与开始时一致）
- Worktree identity rechecked: YES（提交前以 `--expect-root/--expect-branch/--expect-git-dir` 校验通过；提交后 HEAD=1ba6852 且 `merge-base --is-ancestor` 对 `fast/2026-09-21-discovery-enrichment-master` 成立）
- Reported commits reachable from target branch: YES
- Required commands run this invocation: YES（最终提交状态后重跑，退出码 0；此后无源码改动）
- Historical evidence used only as baseline: YES（两次过程 FAIL 均标明非交付状态）

### Remaining Blocker

- None

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`
