## Light Verification: LIGHT_PASS_WITH_NOTES

- Child: 01b（ORCID 机构唯一性与任职语义）
- Boundary: `b12c971be46a992b275a3e4fb3768047b0af8877..68ad011971ac4cbafdd439cfe2d981476ff1332a`（`git rev-parse HEAD` = `68ad011971ac4cbafdd439cfe2d981476ff1332a`，即目标分支 HEAD；边界内除实现提交 `68ad011`「4 文件」外还含 docs-only 提交 `c4b2c6a`「record 01 light verification」，无产品代码）
- Verifier: `Verify01b`（light / four-gate；JDK 11 `zulu-11`；三条必需命令在本最终状态下 fresh 重跑，未采用实现者数字；只读产品代码/测试/索引/提交/计划，仅写入本报告）

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git show --stat 68ad011` 恰 4 文件、全为 brief 授权清单（2 产品 + 2 测试），**无新增文件**、`docs/plans/**`（冻结计划）零改动；`git status --porcelain` 仅 `?? docs/plans/fast/**`（报告未进实现提交，符合 brief 第 4 条）；改动行数 185 insertions / 6 deletions，其中 `-3` 行是 `orcidPageBody` 签名扩展与注释，无删除既有断言 |
| Plan and invariants | PASS | I-1、I-2 均有直接代码 + 行为测试证据（见下）；ORCID 邮箱/姓名/主键绑定与 `identityVerification` 生成路径逐字未动；论文、SBIR 路径零改动（实现提交仅 2 个产品文件） |
| Required commands | PASS | 命令 1 exit 0 / 180·0·0·0；命令 2 exit 0 / `BUILD SUCCESS` / 4242·0·0·13（Node 1202 pass·0 fail·0 skipped）；命令 3 exit 0（无输出） |
| Downstream interfaces | PASS | 与 brief「downstream interface note」逐项一致（见下） |

### Gate 2 — 要求与不变量证据

**I-1 多机构不猜**
- 提取（唯一产生点）：`OrcidDataSource.kt:166-178` — `node.path("institution-name").mapNotNull { it.asText(null)?.trim()?.takeIf { name -> name.isNotEmpty() } }.distinct()` → `institutionName = institutions.singleOrNull()`。trim / 去空 / 去重 / 恰一项四要素齐备。
- `firstOrNull()` 已零调用（`grep firstOrNull OrcidDataSource.kt` 唯一命中为 `:167` 注释中对旧做法的说明），不再按数组顺序任选第一家。
- 唯一性核对：`institution-name` 在本仓的持有 `src/main` 解析点只有 `OrcidDataSource.kt:168`；`ExpertDiscoveryService.readOrcidRecord`（`:2177-2178`）只对**已解析**的 `OrcidRecord` 做 Jackson 反序列化（队列 payload），不二次解析数组，故不存在绕过 I-1 的第二条解析路径。
- `OrcidDataSource.kt:177` 同处保持 `country = null`。

**I-2 任职/国家/身份保守**
- `ExpertDiscoveryService.kt:1513-1526`：`country = null`、`keyword = null, employment = null`、`institution = record.institutionName`（仅唯一机构时非空）。机构名不再回填任职。
- 唯一 ORCID 建档入口：`buildOrcidProfile` 仅被 `:1405`（内联发现）与 `:1583`（`orcidIdentityFactory`，队列消费，`:2050` 取用）调用，两条路径共用同一实现，无复制实现绕过。
- 身份路径未触碰：`proofFor(...)`、`externalIds`（`{"orcid": ...}`）、`orcidRecordToAuthorEmails`（含 `identityEvidence = "ORCID_RECORD_SHA256:" + hash(orcidId, givenNames, familyNames, email, institutionName)`）在 diff 中零改动。
- RAW/候选同值：本 child 未改晋升（`:2291-2308` 仍 `rawDoc.toMutableMap()` 复制、无二次推导），故两层必然同值。

**测试证据（行为层，非实现层）**
- `OrcidDataSourceTest.kt:250-283` 6 条 I-1 用例（单机构 trim 原名 / 两个不同机构 → null / 空数组 → null / 重复同名只算一家 / 空白名不算一家 / 全空白 → null），断言落在 `OrcidRecord.institutionName` 这一解析契约上。
- `ExpertDiscoveryServiceTest.kt:5450-5501` 2 条用例用**真实** `OrcidDataSource`（`:488-531` `discoverOrcidAndCapture` 装配 mock RestTemplate）跑完整 ORCID 发现，断言落在 ES 文档边界（`indexToRaw` 捕获的 RAW 与候选 `_doc` 写入体）：单机构用例断言 `institution` 原名、`employment`/`country` 为 null、`dataSource`/`emailSource`、主键 = `ExpertIdGenerator.generate(null, email)`、`externalIds.orcid`、givenNames/familyNames/email、`identityVerification` 四项（status/source/orcid/email），并断言候选层与 RAW 同值；多机构用例断言两层 `institution`/`employment`/`country` 全 null 且身份字段照旧。均属消费者可观察行为，未断言源码文本/内部调用。
- 未改旧期望变绿：diff 对测试只有追加（`+63` / `+116`），唯一 `-3` 是 `orcidPageBody` 的注释 + 2 行签名扩展（新增默认参数 `institutionNames = listOf("Test University")`，4 个既有调用点语义不变）；`grep employment ExpertDiscoveryServiceTest.kt` 显示既有 ORCID 相关用例无「任职 = 机构」断言可被反转。
- 改前红证据（由冻结基线 blob 复核，非重跑）：`git show b12c971:...OrcidDataSource.kt` 旧行为 = `institutionName = node.path("institution-name").firstOrNull()?.asText(null)`（按序取首家、不 trim、空白计一家），`git show b12c971:...ExpertDiscoveryService.kt:1520` 旧行为 = `employment = record.institutionName`。二者与实现者记录的 6 条失败（`expected: <null> but was: <Test University>`、`<Seoul National University>`、`""`→`null`、trim 差异）逐条吻合，故 2 条端到端用例 + 4 条解析用例确为行为红；另 2 条（重复同名、空数组）在旧代码下恰好也为 null，属回归护栏而非红，实现者已如实披露。[本次受「只读，不得 checkout/改动工作树」约束未重跑红态，为基线 blob 复核 + 代码推断]

### Gate 4 — 下游接口（child 02 依赖项）

- 「ORCID 发现档案仅在 `expanded-result.institution-name` **去空去重后恰一家**时携带该 trimmed 原名机构，否则 null」→ `OrcidDataSource.kt:168-176` + `ExpertDiscoveryService.kt:1522` ✔
- 「`employment` 与 `country` 保持 null」→ `ExpertDiscoveryService.kt:1520-1521`（`country = null`、`employment = null`）；测试断言两层均 null ✔
- 「child 01 的内部 `institutionSource` 语义不变」→ 本 child 零改动 `AuthorEmail.institutionSource` / `PaperAuthor.institutionSource`；`src/main` 中其赋值点仍只有 `JatsXmlEmailParser.kt:65`（`JATS`）与 `OpenAlexDataSource.kt:299`（`OPENALEX`），ORCID 路径构造的 `AuthorEmail` 与 `ExpertProfile` 均不设置该字段 ✔
- 「02 的 `institutionEvidence` `ORCID:` 签发只能基于唯一机构」→ 唯一机构判定收敛在解析层单一表达式，无第二判定点 ✔
- 未新增/未改 ES 字段、mapping、迁移、前端；未引入历史回填、当前职位判定、ORCID 国籍推断（实现提交仅 2 产品文件 + 2 测试文件）✔

### Required commands（fresh，本最终状态，逐条重跑）

| # | Command | Result |
|---|---|---|
| 1 | `mvn test -Dtest=OrcidDataSourceTest,ExpertDiscoveryServiceTest` | exit 0，`BUILD SUCCESS`，`Tests run: 180, Failures: 0, Errors: 0, Skipped: 0`；分类：`ExpertDiscoveryServiceTest` 164、`OrcidDataSourceTest` 16 |
| 2 | `mvn test` | exit 0，`BUILD SUCCESS`（483.80 s），surefire 聚合 `Tests run: 4242, Failures: 0, Errors: 0, Skipped: 13`；`<<< FAILURE/ERROR` 0 处；Node 套件 `tests 1202 / suites 236 / pass 1202 / fail 0 / cancelled 0 / skipped 0` |
| 3 | `git diff --check` | exit 0，无输出 |

与基线/child 01 对比（`children/01/baseline.md` seed `4220/0/0/13` + Node 1202 pass；child 01 post-state `4234/0/0/13`，`OrcidDataSourceTest` 10、`ExpertDiscoveryServiceTest` 162）：

| 指标 | 基线 seed | child 01 post-state | 本次 01b（fresh） | 判定 |
|---|---|---|---|---|
| surefire 聚合 | 4220 / 0 / 0 / 13 | 4234 / 0 / 0 / 13 | 4242 / 0 / 0 / 13 | 零新增失败；+8 恰为本 child 新增用例数 |
| OrcidDataSourceTest | 10 / 0 / 0 | 10 / 0 / 0 | 16 / 0 / 0 | +6，对应 6 条 I-1 用例 |
| ExpertDiscoveryServiceTest | 160 / 0 / 0 | 162 / 0 / 0 | 164 / 0 / 0 | +2，对应 2 条 I-1/I-2 端到端用例 |
| 01 引入的对照类（JATS/OpenAlex/Resolver） | 35 / 73 / 18 | 40 / 79 / 19 | 40 / 79 / 19 | 不变，无回归 |
| Node 套件 | 1202 pass | 1202 pass | 1202 pass / 0 fail | 不变 |
| skipped | 13 | 13 | 13 | 不变 |

计数与实现者报告（180/164/16、4242、Node 1202）逐项一致，但由本次独立 fresh 重跑取得。

### AUTO_FIX

无。四门均无「已证实违规 + 计划唯一确定修正 + 全部文件已授权」的组合：I-1/I-2 已由代码与行为测试证实成立；唯一残留（见 RECORD_ONLY）的修正需触碰白名单外文件，不满足 AUTO_FIX 第三条件。

### RECORD_ONLY

- `OrcidDataSource.OrcidRecord.country`（`OrcidDataSource.kt:36`）在本 child 后成为**死字段**：唯一生产点 `OrcidDataSource.kt:177` 恒写 `null`，且本 child 移除了它最后的读取方（`ExpertDiscoveryService.buildOrcidProfile` 原 `country = record.country`），`src/main` 内已无读取点（`grep` 复核）。计划文本只授权「country 继续 null」，未授权删除字段；且删除会改变构造签名、波及白名单外的 `DiscoveryPipelineServiceTest.kt:1416`（构造时传 `country = "GB"`）等调用点，故**不在本 child 可修正范围内**，实现者已在 execution.md 披露。行为影响为零（该字段在网络→解析→队列全链恒 null），仅属清洁度残留。留待后续 child 或人工决定是否收敛。
- 补充检查（非必需命令）：`git diff --check` 无参数检查面为工作树 vs 索引，实现已提交故为空，**必需命令 3 判定为 PASS**；为覆盖实际 diff，额外执行 `git diff --check b12c971..68ad011` → exit 2，唯一命中 `docs/plans/fast/.../children/01/execution.md:39: trailing whitespace.`。该文件由 **child 01 的证据提交 `c4b2c6a`** 引入（child 01 的验证只覆盖了 `2e9639d..b12c971`，故该行从未被检查），属计划报告产物、非 01b 授权文件、非产品代码，不影响 01b 任何门判定，仅记录待后续证据提交顺带清理。

### Required Action
- COMPLETE_CHILD
