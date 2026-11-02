# 06 — 固定真值与完整链路复验

状态：待执行；2026-11-01。仅计划，不表示已实施或发布。

## 需求描述

用同一份原文、同一批目标、同一策略配置核对离线解析与测试/生产重处理结果，给出实际修复、补录、准入及未修原因。发布和线上补跑是后续独立操作，本计划不自动授权执行。

保持不变：①历史首次解析、旧run结果、人工/联系保护；②邮件/采集游标/Apollo配置不被验证脚本改写；③无法取得原文的项不伪造预期。

不做：未经验证的成功率/完成时间承诺。两个子系统：自动测试、对照报告。

## 关键不变量

### Invariant I-1: 同输入对照
- Rule: 解析比较固定body/metadata/parser版本；资格比较还须固定事实、评价时间、策略与外部响应。实时外部变化必须单列EXTERNAL_DATA_CHANGED，不掩盖程序差异。
- Applies to: test/对照脚本/报告
- Violation consequence: 离线预计与线上无法公平比较。
- 来源: original

### Invariant I-2: 真实计数
- Rule: 逐条标识核对，不能只比总数；原333/325属于保守历史审计口径，本次另报唯一邮箱候选、唯一docId、personId、实际新增/补齐/准入。未补齐每项具体原因；没有结果标未执行。
- Applies to: 报告
- Violation consequence: 同数不同人被误报一致。
- 来源: original

### Invariant I-3: 隔离与发布门
- Rule: 先隔离环境通过、再由用户批准发布/生产任务；生产只读核对遵循既有授权。没有Docker/原文等必要条件时标NOT_RUN，不记PASS；迁移、保护与误绑任何失败阻断上线。
- Applies to: 所有验证
- Violation consequence: 测试扰动生产或把未测当已测。
- 来源: K-flyway-gate-must-pass-on-fresh-db

## 现状审计

完整源码回执：[paper-reprocess-evidence/README.md](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/README.md)、[paper-reprocess-evidence/paths.json](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/paths.json)。共享存储全部生产读写路径及交互编号见主计划「现状审计」；本子计划只改变下述接缝，其他匹配路径为回归边界。当前本地基线 `b758eb0`，工作区存在先前改动；不得当作已核实的生产部署版本。
- `docs/audits/2026-11-01-discovery-30711-pdf/report.md` 是漏绑证据，不是修复后资格预期。
- `FlywayMigrationIntegrationTest` 由 `-DmigrationIt=true` 才启用；普通 mvn test 可能跳过，不能当作迁移验证成功。
- 00→01→02→03→04→05 分别覆盖 X-0..X-9，本章核验这些路径实际衔接，尤其 identity哈希变更与admission guard、普通新注册和旧档案child重审不重复。

## 实现方案

1. **构建（I-3）**：JDK11执行计划涉及的全部Kotlin测试；迁移明确使用 `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`，空库与V171升级两条路径均需真实MySQL结果。最后 `mvn package -DskipTests` 只验证WAR构建，不替代测试。
2. **解析对照（I-1/I-2）**：同00manifest跑01；逐行输出EXPECTED_MATCH/EXPECTED_MISSING/UNEXPECTED_MATCH/REGRESSION/SOURCE_MISSING，人工已证实真值中任何新错绑为阻断。未覆盖中间页与无法取得元数据分开计数。
3. **业务fixture（I-1/I-3）**：新增端到端fixture用以下固定12目标：新建2、现有空名2、现有完整2、身份冲突2、人工/联系保护2、缺源1、写入故障1。固定provider响应让其中1新建+1补名合格、另外2资格不足。预期业务新增2、补齐2、实际准入2；冲突/保护/缺源/错误均不可被计准入。用SQL+ES测试实例，不对生产写数据。
4. **状态与复跑（I-2/I-3）**：同一任务重试成功项0重做；新任务再跑上述fixture新增0、补齐0、已准入2独立展示；中途取消保留已提交结果；准入成功但投影失败只同步重试。对所有明确写路径做故障注入。
5. **效果报告（I-1/I-2）**：报告列旧漏绑定组合总数、可读原文数、生产旧重现数、新规则新增正确绑定数、保留未知数、回归数；再列当前业务新增/补齐/保护/冲突/未创建原因；最后准入/待核验/系统错/同步错。必须附逐条差异，不写笼统“325全部修复”。
6. **上线后核对（I-1/I-3）**：后续用户发布并启动任务后，仅按runId读取结果与同manifest比较；若换了批次、版本、时间/实时外部数据，只能比较对应稳定维度。任何线上补跑/变更需另有明确授权，不由此验证脚本暗中触发。

## 变更文件清单

| 文件（仓库根目录相对路径） | 改动 |
|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessEndToEndTest.kt` | 新建/补齐/复验/取消/重启的端到端fixture |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 新增迁移空库和升级验证 |
| `scripts/discovery_admission_probe/reconcile_paper_identity_replay.py` | 增加实际重处理run与离线预期逐项比较 |
| `docs/audits/2026-11-01-discovery-30711-pdf/reprocess-acceptance-report.md` | 执行后生成：可修数量、真实落地与偏差；不可预填 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | 仅将Flyway fixture目标从V161提升到V173，使child04新增的`discovery_paper_reprocess_run`依赖可见；不改断言与业务行为 |

共 5 个文件。未列文件禁止扩改；新增问题先记录证据并修订范围。

## 验收标准

- I-1：相同输入解析集合严格一致；provider变化独立列出而非混作代码差异。
- I-2：12目标fixture每人都有可核对结果；相同总数但不同email/docId必须FAIL；原325中缺源不算修复。
- I-3：空库/升级/边界测试全通过且新错绑0才具备发布条件；报告不得把NOT_RUN转换PASS。
- X-0..X-9：主计划人工A-1..A-6、子计划所有A项能逐一覆盖，机器通过与人工验收结论分开。

## 人工验收清单

### A-1: 预期与实际逐条核对
- 前置条件：测试环境已准备实现方案第3项12目标及固定provider响应。
- 操作步骤：1. 运行对照；2. 执行ENFORCE；3. 打开报告和日志。
- 预期结果：实际新增2、补齐2、准入2；12目标逐条可核对；无来源的1项显示SOURCE_MISSING；错误1项不是待核验。
- 覆盖：I-1/I-2，X-0..X-9

### A-2: 历史和重复
- 前置条件：保留测试前首次outcome/旧任务/配置与游标快照。
- 操作步骤：1. 再跑同批；2. 查看变化；3. 对比快照。
- 预期结果：第二轮新增0、补齐0；原已准入2单列；原快照、配置、采集游标不变；保护2项始终未覆盖。
- 覆盖：I-2/I-3，保持不变①②

### A-3: 缺源和未测诚实呈现
- 前置条件：从00清单移走1个原文副本；或本机无Docker。
- 操作步骤：1. 执行报告流程；2. 查看对应状态。
- 预期结果：缺原文行SOURCE_MISSING；迁移未执行为NOT_RUN；发布门未通过，不输出“全部验证成功”。
- 覆盖：I-1/I-3，保持不变③


人工验收开始时才从此处导出同前缀 `-acceptance.md`；本次不生成衍生勾选文件。

## 修正记录
- 2026-11-02：06全量命名测试发现 `DiscoveryVerificationWorkerTest` 的7处Flyway fixture仍target V161，而child04的 `DiscoveryVerificationRepository.claimDue` 已引用V173新增表，导致7个BadSqlGrammar错误。经用户后续必要授权加入第5个文件，仅把fixture目标提升到V173；断言、业务行为、12目标fixture、迁移与报告验收均不减。证据见06执行日志与ledger A12/A13。
