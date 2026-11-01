# 02 — 原重审入口补齐留存身份

状态：待执行；2026-11-01。仅计划，不表示已实施或发布。

## 需求描述

现有「重新审核全部待审核 / 所选专家」继续使用：确认执行后先从已关联原文补齐明确的姓名/凭证，再运行现有资格验证。仅对照模式只形成拟修复身份及结论。

保持不变：①固定名单/查看/关闭页面不写专家；②人工、已联系、APPLICATION 保护及非空冲突不覆盖；③原审核轮次、原名单 hash、已提交准入历史和 Apollo 规则保留；④没有原文时继续既有验证并明确提示。

不做：从专家按钮发现不在固定名单的新作者；新增准入规则；改写完成的旧审核项。两个子系统：留存身份补证与本轮核验。

## 关键不变量

### Invariant I-1: 提案与应用分离
- Rule: 共享服务 propose 只读：精确邮箱定位、原文校验、唯一作者证据；apply 只在 ENFORCE 确认运行后。AUDIT 使用内存有效 profile，不写三层 ES/准入/人物/学术任务。所有提案须可追溯完整原输入。
- Applies to: 新服务、Worker
- Violation consequence: 固定名单或比较操作直接改变业务。
- 来源: K-paper-facts-need-owner-time-and-readonly-compute

### Invariant I-2: 安全补齐
- Rule: 沿用 recovery 的 live-discovered、APPLICATION、联系人/别名、人工决定、作者ID及跨层唯一邮箱保护；检查失败不当不存在。只填空姓名/缺凭证；非空值不相容为 CONFLICT。所有已有层用现有 seq_no/primary_term CAS；部分成功记录 PARTIAL，不能宣布全成功或直接准入。
- Applies to: RetainedPaperIdentityService.apply → repairEmptyNameProof/repairDuplicateIdentity
- Violation consequence: 覆盖人工数据或把不同人合并。
- 来源: K-author-identity-needs-email-evidence

### Invariant I-3: 本轮有效身份与原基线
- Rule: 只允许 review_epoch>0 且当前RUNNING、lease/generation有效、父run接受提交的本轮item重绑定。snapshot_json 只新增一个 identitySupplement 对象，完整保存 beforeSnapshot、beforeIdentityHash/factFingerprint、beforeAdmissionRevision/identity、proposalHash、source refs、effectiveProfile/hash、mode、phase、repairReceipt。原 rosterHash/旧轮次不变；本轮root.profile/hash与item列同步采用有效身份。
- Applies to: repository新CAS、Worker、Commit、Query
- Violation consequence: 旧快照仍挡住修复，或历史证据被伪造。
- 来源: K-discovery-recheck-history-not-item-reset

### Invariant I-4: 提交不得泛化放宽
- Rule: AUDIT 的并发保护比较 beforeSnapshot 对应当前实际业务；资格计算用有效 profile，永不 recordAutomatic。ENFORCE 只在持久化receipt完整且当前ES等于有效身份时接受身份改变；准入行仍为原身份时，仅原revision且非人工/非历史保护可进入既有 recordAutomaticPersisted。任何其他变化 STALE_INPUT/PROTECTED。
- Applies to: Commit所有分支
- Violation consequence: 为了补名跳过现有防并发门槛。
- 来源: original

### Invariant I-5: 版本和状态
- Rule: identitySupplement.phase 仅 PROPOSED/APPLYING/APPLIED/PARTIAL/UNCHANGED/CONFLICT/PROTECTED/UNAVAILABLE；中断APPLYING从各层真实目标值核对，不重写非空值。身份变更后清除旧identity绑定的待用外部证据，不搬旧Apollo命中；原缓存保存在beforeSnapshot供对照。
- Applies to: Worker恢复、repository、查询
- Violation consequence: 重复请求、旧人证据用于新人或永久重试。
- 来源: original

## 现状审计

完整源码回执：[paper-reprocess-evidence/README.md](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/README.md)、[paper-reprocess-evidence/paths.json](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/paths.json)。共享存储全部生产读写路径及交互编号见主计划「现状审计」；本子计划只改变下述接缝，其他匹配路径为回归边界。当前本地基线 `b758eb0`，工作区存在先前改动；不得当作已核实的生产部署版本。
- `PaperVerificationEvidenceService.kt:138–145` 要求解析名等于 snapshot.fullName；读取证据本身不会修档案。
- `DiscoveryVerificationWorker.kt:345,448–454` 使用旧 snapshot；identityValid=false 时跳过4–6，说明只修 parser 不够（X-2）。
- `DiscoveryIdentityRepairService.kt:57,183` 已有三层 CAS 窄写接口；`DiscoveryIdentityRecoveryService.kt:179–265` 有联系人、人工、APPLICATION、作者ID保护，但原 apply 会进一步刷新准入，不能在 AUDIT 直接调用整方法（X-3）。新服务复用窄 repair 方法，明确移植并测试保护条件，不复用联网 extractSource。
- `DiscoveryVerificationCommitService.kt:79–107` 校验 identityHash/fact/revision，`126–135` 只有 ENFORCE 才记准入；简单替换 Worker 的名字会被 commit 判 STALE_INPUT（X-4）。
- V161 唯一键含 review_epoch；既有 root.snapshot 是 MEDIUMTEXT，不新增 DB 列，新增一个结构化 JSON 字段 identitySupplement。该字段所有生产读者见 verification.txt；不改变普通发现 review_epoch=0 路径。
- 执行 epoch 1 的有界审计回执见 `docs/plans/fast/paper-reprocess-master/children/02/execution.md:30–57`：`DiscoveryReviewService.kt:858–865` 的 `recordAutomaticPersisted` 无条件拒绝 `outcome.identityChanged`，而 `:900–917` 在原身份自动准入行 CAS 成功后仍返回该标记。当前 I-4 的受控补名路径需要显式窄接缝，不能靠绕开断言实现；此前事务回滚结论为源码推导，epoch 2 必须用隔离回归证明。

## 实现方案

1. **提案（I-1/I-2）**：`RetainedPaperIdentityService.kt` 使用 archive.analysisLease + PaperBodyAnalyzer 原始bytes；按精确邮箱收集多个来源，一致的唯一作者生成 AuthorEmail/proof；冲突、无源、重复 docId 返回诊断。论文来源优先固定 paperSourceRefs，fallback 只用持久化 DOI/PMCID/PMID 的现有 locate，不按同名论文猜测。仅选择确认归属的 source refs；缺源不阻断既有 Apollo/ORCID 路径。
2. **确认后运行（I-1/I-3）**：`DiscoveryVerificationWorker.kt` 在第2步保留原文读取，在第3步增加 identitySupplement；正式模式提交前做保护复读，调用窄 repair 后再读完整 RAW，登记成功才 APPLIED。纯对照用 proposal 中的虚拟 profile。不在 prepare/start API 调用耗时解析；原按钮仍很快返回。
3. **可恢复本轮重绑定（I-2/I-3/I-5）**：repository 新 CAS 分两阶段：①租约下保存完整 beforeSnapshot+proposal，状态 APPLYING（AUDIT为PROPOSED），不先改item身份；②外部ES操作结束后，短事务锁父run→item，验证原lease/generation/current baseline，保存receipt、有效root.profile/root.identityHash/root.factFingerprint并同步item列。有效身份更新与receipt同事务。若ES成功、SQL未提交，下次先按proposal核对三层，ALREADY_VERIFIED续接，禁止重新解析成不同提案。CAS成功必须返回新VerificationItem；Worker在内存中替换activeLease及有效snapshot，后续collect/evaluate/finish均使用返回版本，不能继续传旧val。原快照始终可由 identitySupplement.beforeSnapshot 重建，字段只保存一份，防止重试嵌套膨胀。
4. **缓存与资格（I-4/I-5）**：有效身份变更后所有后续查询/资格调用用有效 snapshot；相同身份可复用原证据，变更身份的缓存标记 IDENTITY_CHANGED_CACHE_NOT_REUSED，REUSE 模式不因此外呼，REFRESH 按冻结配置重取。已落库的历史 evidence 不删；身份改变时同时丢弃旧snapshot下的paper owner判定，使用同一冻结来源重新collect并绑定有效snapshot（允许复用纯解析缓存，不复用owner结论）；新第3步记录原/新哈希、原句/页码、保护与每层写入结果。
5. **提交（I-3/I-4）**：`DiscoveryVerificationCommitService.kt` 只识别服务端本轮生成且proposal/hash/lease链完整的identitySupplement。分别检查 before admission revision 和当前ES有效身份；其他人为更名/手工结论/联系发生必须阻断。AUDIT staged policy 使用虚拟 profile但记录applied=false；ENFORCE继续现有准入提交、人物登记、候选投影，不另造自动批准。
   - 同步在 `DiscoveryReviewService.kt` 的既有 persisted-admission seam 增加仅供本轮受控修复使用的严格校验：原 identity/revision 与非人工/非历史保护仍匹配、服务端完整修复 receipt 对应当前有效身份且父 run/item/lease 允许提交时，才允许该次 expected identity change；普通调用继续原有身份保护。不提供通用 ignoreHash/allowIdentityChanged 布尔后门，不预写准入、不双调写入、不捕获断言当成功。回归放入既已授权的 CommitServiceTest / IntegrationTest，覆盖原身份自动准入→有效身份成功及 stale/manual/history/取消拒绝与回滚。
6. **读回（I-3）**：`DiscoveryVerificationQueryService.kt` 原名单详情展示冻结身份（从beforeSnapshot），当前处理结果展示有效身份；rosterHash不随补名变化。第3步已有通用 JSON trace 显示完整提案/原因，无须前端先改才能用。
7. **并发边界（I-2/I-4）**：沿用 SQL 决策revision + ES CAS；不宣称跨存储原子事务。每次写前复读保护，提交前再检查；测试人工审核/新联系人插入时至少不得准入覆盖。若现有保护写路径无法保证“任何身份补丁都绝不会与同期联系重叠”，记录竞态限制，不把它宣称为已解决的跨库强一致性。
8. **测试（I-1–I-5）**：文件清单内五个测试覆盖：空名→补名、完整名缺proof、已有完整、冲突、人工/联系人、REFRESH/REUSE、AUDIT零业务写、ES部分成功后崩溃、CAS冲突、当前项取消、旧token失败、旧轮次完全不变。

## 变更文件清单

| 文件（仓库根目录相对路径） | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/RetainedPaperIdentityService.kt` | 新增纯提案/受控应用共享服务 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | 第2/3步接入补绑；AUDIT虚拟身份 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | 本轮有租约的身份重绑定 CAS；保留冻结基线 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt` | 区分原基线保护与有效身份；仅受控修复例外 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | 展示原身份→有效身份与修复结果 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` | 既有 persisted-admission 窄接缝识别服务端受控身份修复；普通调用保护不变 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/RetainedPaperIdentityServiceTest.kt` | 保护、唯一性、部分写续跑 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | 两种模式、缓存失效、重试 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt` | 身份变化与人工并发保护 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` | 原始名单hash及有效身份读回 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` | SQL CAS、重启、保护与候选投影 |

共 11 个文件。该上限为用户批准的 02 单独例外；不删除原有测试来规避计数。未列文件禁止扩改；新增必要接缝先记录证据及明确授权来源，再修订范围。

## 验收标准

- I-1：点击固定名单以及 AUDIT 全流程，专家/准入/person写计数均0；仅核验审计表允许写。
- I-2：明确姓名填入指定docId；同邮箱多docId/非空冲突/保护读异常均零身份写；PARTIAL不能晋升，续跑只补余层。
- I-3：集成测试验证新有效hash与item列同事务，beforeSnapshot可重建；原名单hash和已结束旧item SHA不变。普通发现epoch0禁止调用rebase。
- I-4：人为更名/新人工决定/新增联系/取消与commit竞态均不得准入；AUDIT使用虚拟身份仍可出建议但applied=false。自动身份修复不能通过通用“忽略hash”开关实现。
- I-4/X-4：隔离回归实际执行原 identity/revision 的自动准入行在受控补名后经 recordAutomaticPersisted 成功；同样变化在缺 receipt、非本轮、manual/history、revision 变化或取消时拒绝，普通调用不能借此绕过身份 guard。
- I-5：重启、旧token、同提案重试、重放旧缓存hash均覆盖；不无限嵌套原快照。
- X-2–X-4：真实已建空名样本通过原按钮执行，证据→补丁→新核验→准入/待核验→查询追溯贯通；准入数按规则实际输出，不预设325。

## 人工验收清单

### A-1: 原按钮仍能补绑
- 前置条件：隔离环境放入一条00真值中的已建空名RAW及关联原文，无人工/联系保护。
- 操作步骤：1. 专家审核选此人；2. 固定名单，查看名字；3. 确认ENFORCE执行；4. 查看第3步。
- 预期结果：固定名单阶段名字仍空；执行后填入真值姓名；第3步有before/after和页码；最终显示现行规则实际准入或待核验，不固定为通过。
- 覆盖：I-1/I-2/I-3，X-2/X-3/X-4

### A-2: 纯对照与旧名单
- 前置条件：同A-1但选择AUDIT，并保存旧完成轮次详情/名单hash。
- 操作步骤：1. 执行仅对照；2. 查看档案和任务；3. 刷新旧任务。
- 预期结果：任务有拟补齐姓名；业务名字仍空；applied=false；旧轮次与原名单hash不变。
- 覆盖：I-1/I-3/I-4，保持不变①③

### A-3: 冲突、保护及无原文
- 前置条件：分别准备非空不同名、人工通过、已联系、APPLICATION、原文删除五名专家。
- 操作步骤：1. 选择五人重审；2. 查看每人第3步。
- 预期结果：前四个不覆盖姓名/准入，显示CONFLICT或PROTECTED；第五个UNAVAILABLE并继续已有可用验证；原有Apollo开关不变。
- 覆盖：I-2/I-4/I-5，保持不变②③④

### A-4: 部分完成续跑
- 前置条件：故障注入环境：RAW补丁成功后使CANDIDATE写入失败一次。
- 操作步骤：1. 执行；2. 检查PARTIAL；3. 移除故障重试。
- 预期结果：第一次不准入；第二次仅补剩余层，完成后同一docId仍一条人物关系，历史证据可查看。
- 覆盖：I-2/I-3/I-5，X-3/X-4


人工验收开始时才从此处导出同前缀 `-acceptance.md`；本次不生成衍生勾选文件。

## 修正记录
- 2026-11-01：用户“继续 后续授权都继续 直到所有子计划都完成且可以运行review-fast-p的阶段 不要执行review-fast-p，我会交给其他agent执行”批准此前暂停建议：增加 DiscoveryReviewService.kt 窄接缝与文件数 11 的例外，维持 I-4 原 revision/人工/历史保护与所有验收；权威 lineage 见 fast-p ledger Amendments A2（主计划对应 A1）。未授权 review-fast-p 或发布。
