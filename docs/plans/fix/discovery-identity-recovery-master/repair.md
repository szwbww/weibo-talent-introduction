# Repair Plan: discovery-identity-recovery-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-13/discovery-identity-recovery-master.md
Baseline SHA256: f69d8135ce0019ad36cc8939cf8101a6f584aac9983dec3dfc78eb2ae37ce238
Baseline recorded commit: 36075ec06c5e4382f13606c995e24a13ec4470ce
Verification report: docs/plans/review/2026-10-13-discovery-identity-recovery-master/machine-verification.md — Epoch 1，独立 aggregate/master，FAIL / INITIAL
Implementation boundary: 9d353934b82ed3e36a193c70748e5efccb3d9e82..cd6a38d662da881ec453839921922a30602eacb3
Evidence head at review: 1408138b429297ad7b5a949d0b8e7839bdd4ee63
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master
Branch: fast/2026-10-13-discovery-identity-recovery-master

## Objective

恢复已批准的 discovered 人物链合同：仅有效范围和可靠同人事实归组；列表与选择单位一致；首发保护在失败、重试和事务回滚后仍可靠；维护预览、受限应用与审计可复核、可续跑。只修复独立审查证实的 V-1—V-20、V-25，不执行历史生产应用或真实发送。

## Authority and Handoff Validation

- governing master 与 invoked identity 一致，无 governing diff。批准修订 A1—A14 的逐项规则、原因、人工批准和 before/after commit 见 fast-p ledger 的 Amendments；该文件 SHA256 为 2bbeec0ca9bbaf2008aed29b4265862a089a84cdbc25f20160a3e6ef5fe361e2。
- fast handoff: docs/plans/fast/2026-10-13-discovery-identity-recovery-master/human-review-handoff.md，SHA256 ff9fa89083d24f9a3f6b37f798a8f201273df9c7b543d8a572316e7c4a390a64。
- 原 aggregate/repair lineage: N/A；收敛 INITIAL。V-1—V-20、V-25 为本边界证实的20项 P1 必需行为违反及1项 P2 必需构建违反，逐项 disposition=REPAIRABLE；无计划语义修订或新产品目标。
- Authorized Files 共 47 个现有文件：46 个在本次已审 68 个产品/测试文件差异内，另将既有 OperatorStatusWriteSeamGuardTest.kt 精确列入本待批准修复范围，以修复本实现导致的必需构建回归 V-25。该测试尚未被本审查修改；只有后续人工批准本 repair 才授权修改，未增加产品目标或更改原写入口规则。不得因同名、相邻功能或新测试便利而扩大文件集；需扩大时停止并报告具体权限缺口。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause / Evidence | Disposition |
|---|---|---|---|---|
| V-1 总开关缺失 | P1 | master I-8、发布段；04 I-3 | 05–09 直接调用人物能力，04 所称默认关闭仅指旧版本无调用者。默认配置部署即改既有读写。 | REPAIRABLE |
| V-2 范围判定把历史发现来源当实时标签 | P1 | master I-4；04 I-3；05；10 I-1 | PersonService:155、Recovery:190、Registration:facts、Maintenance:369 使用 isDiscoveryMap（proof/source/tag 任一），移除 discovered 标签仍可登记/应用；P01/P06。 | REPAIRABLE |
| V-3 同人校验遗漏姓名与已有人物完整作者键 | P1 | 04 I-2；master I-4 | PersonService:61–89 不校验组全名；279–312 attach 不把既有根成员/作者键加入冲突检查，重试不重读 ES；P02/P03。 | REPAIRABLE |
| V-4 恢复链吞掉保护读取/人物登记失败 | P1 | 02 I-2；05 I-2；master I-3/I-8 | Recovery:207–214 联系人/alias 读异常当不存在；110–115 忽略 refresh FAILED/STALE/异常，138 返回 APPLIED；重试被194已有proof挡住。 | REPAIRABLE |
| V-5 发送只检查可见成员和缓存资格 | P1 | 08 I-1/I-3；09 I-3；master I-4/I-7 | Recipient:117,153,162,251–265 缺失/移层成员被丢弃，scope 先过滤隐藏 HOLD；无映射仍逐档案可发。Guard:133–154 SAFE_RETRY 绕历史复核，194–220 不重读 ES/准入；详情 profile null 直接无 guard；P08。 | REPAIRABLE |
| V-6 重试与新目标缺统一人物集合 | P1 | 08 I-2；master I-7 | ManualInitial:567–581,711–724,1967–1994 以 docId/orcid 去重，未把 personKeyOf 接入；P13 同人 retry D2 后 ES D1 又计1。Recipient:279 把全部成员邮箱当默认，未使用primaryDocId。 | REPAIRABLE |
| V-7 详情事务回滚撤销已开始投递的保护 | P1 | 09 I-2；master I-7/I-8 | ManualExpert:63/79 外层事务包含 guard reserve/claim、SMTP110、mailRecord121；Guard REQUIRED。P09 真MySQL SAFE_RETRY→SENDING→假SMTP→外层回滚后SAFE_RETRY，下一次Granted。 | REPAIRABLE |
| V-8 无法确认 SMTP 未接受却允许安全重试 | P1 | 09 I-2；master I-8 | Initial:164–166、ManualExpert:114–116 对所有非SENT标SAFE_RETRY；ManualInitial:1276仅INFRASTRUCTURE归UNKNOWN。实际SmtpMailDelivery:141–148把MessagingException变FAILED，Classifier:101无返回码为TRANSIENT，含交付后网络中断。 | REPAIRABLE |
| V-9 未发送退出遗留 RESERVED | P1 | 09 I-2；master I-8 | Initial:99–117 account/contact 异常在cleanup外；ManualInitial:1050,1068,1086,1110,1120,1147,1286 取消/compose/audit等退出未转换已持有ticket，后续永久IN_FLIGHT。 | REPAIRABLE |
| V-10 混合查询深分页被新上限拒绝 | P1 | 06 I-1；master I-6 | Query:90把from+size传size；Search:1254要求size≤1000。P04 from1000,size50 抛IllegalArgumentException。 | REPAIRABLE |
| V-11 DB联系人分组使用无效关系且改范围外单位 | P1 | 06 I-1/I-3；master I-4/I-6 | Query:247裸SQL映射不重核；ContactController:95–116 fallback orcidId把普通同orcid不同contactId合并并改排序；app默认新模式。 | REPAIRABLE |
| V-12 详情以旧filterResult冒充邮箱准入许可 | P1 | 06 I-2；07 I-1/S-2；master I-5/I-7 | Query:229 admissionStatus=filterResult，未读取V148/独立邮箱状态；app:13508 PASSED显示可发送，HOLD/抑制可能被误标。 | REPAIRABLE |
| V-13 ES不完整响应被算为精确人数 | P1 | 06 I-3；master I-8 | Search:1275,1310,1328 未拒绝timed_out/_shards.failed/body缺失/中途不完整；P12 total100且timed_out=true+failed shard正常返回0。 | REPAIRABLE |
| V-14 维护快照未绑定全部受保护事实且审计丢字段 | P1 | 10 I-1及步骤2；master I-4/I-8 | Maintenance:390仅updatedAt/RAW版本、494摘要仅docId+身份指纹；Domain:158不含proof/version/tag/seq/SQLrevision/人工历史；429/450/466读异常转null；239再次读后不对原expected。P10更换proof hash仍APPLIED；reconcile:263落盘遗漏原始复核字段。 | REPAIRABLE |
| V-15 历史保护未导入且事务/回滚契约未成立 | P1 | 10 I-2/I-3；09 I-3；master I-8 | Maintenance:228自调用@Transactional applyGroup231；248关系落库后258只读import；279未持久化SENT/UNKNOWN却报protectionImported。321回滚仅拦SENT/UNKNOWN，P11 SENDING仍safeToRollback=true。 | REPAIRABLE |
| V-16 子集试点和断点恢复无法通过全量摘要 | P1 | 10步骤3/4；master历史试点 | Maintenance:193用请求子集重建全量摘要；reconcile limit50/过滤done仍用原previewDigest，P05合法子集被PREVIEW_DIGEST_MISMATCH拒绝。 | REPAIRABLE |
| V-17 冻结清单处置不互斥 | P1 | 10 I-1；master I-1/数据交付 | Maintenance:66不拒绝候选组重叠，77重复累计；P07冻结2条、groups[a,b],[a]输出total3。 | REPAIRABLE |
| V-18 维护CLI无法通过现有会话认证 | P1 | 02/10维护入口可用性与既有访问控制 | replay:166、reconcile:223请求只有Content-Type，无cookie/auth参数；对应controller要求getSession(false).username，全部请求401。 | REPAIRABLE |
| V-19 02 部分成功无法按原请求继续 | P1 | 02 I-3；master I-3/I-8 | Recovery:100–107返回PARTIAL，但下一次inspect:194任一已有proof即拒绝；P15 首次PARTIAL，排除故障后preview=NEEDS_MANUAL:EXISTING_PROOF，同原hash apply=STALE。 | REPAIRABLE |
| V-20 PMCID 回源未核对原文来源编号 | P1 | 02 I-1验收“来源 ID 不符不签证”；master I-4 | Recovery:369–376为PMCID只构造空PaperMetadata并调用提取，不校验JATS article-id。P16真实原文article-id=PMC7759461，存量定位改PMC1234567、HTTP边界返回原字节，仍RECOVERABLE并签出proof；不 mock 身份解析。 | REPAIRABLE |
| V-25 operator_status 守卫噪声位置未随查询改动更新 | P2（mandatory） | master 必需 JDK11 clean package；CLAUDE.md 构建门禁 | OperatorStatusWriteSeamGuardTest.kt:163 在 C20/C21 均失败。ContactController:632、Query:228、IndexController:80/108/477 为只读参数/DTO 投影，旧精确排除项行号失效且新增投影未登记；原真实写入口两文件不变。基线扫描闭包通过；C21 4999 tests、1F/0E/20S。 | REPAIRABLE |

## Findings Excluded

| Finding | Classification | Reason |
|---|---|---|
| V-21 | P2 | admission O-1：重验测试名仍写旧门槛；只改说明，不影响当前断言/行为。 |
| V-22 | P2 | 04 O-4：fake rejectNextCreate 未被使用，不能把该 service 单测当作真实冲突复验；可选测试深度，生产缺陷由 V-3 单独覆盖。 |
| V-23 | P2 | 05 O-1：SCOPE_CONFLICT 计数路径缺直接断言；未证明该计数错误。 |
| V-24 | P2 | 08 O-1：ALREADY_CONTACTED KDoc 错写人工拒绝/身份冲突；纯说明问题。 |
| B-1 | BLOCKED | 06 I-3 的 51,829 条真实形状脱敏数据 p95≤2s /额外堆≤128MiB 缺实测；brief 延后声明不能豁免。 |
| B-2 | BLOCKED | 09 I-1 跨三个真实入口组合、共享真库和假 SMTP 总调用≤1 缺完整证据；现真库套件只测 guard，service 接线 mock guard。 |
| O-1 | Observation | C04 首次 JS 时序失败；相关产品/测试未改，focused 27 和后续全量 JS 已通过，保留首次失败及 exact rerun，不修无关基线。 |
| O-2 | Observation | 01 O-1 测试数变化属于前置 admission；04 O-3 V154断言与既有 V155 对齐；09 O-3 文档31与实际32版本断言偏差；admission O-2 taskFailureCount按记录的任务处理语义。 |
| O-3 | Observation | C20 的19个错误来自未改动国家时区目录使用 America/Coyhaique 与 Zulu11.0.15/tzdb2021e 不兼容；现有11.0.32.1/tzdb2026b对照C21为0E。不得改无关目录或降低校验。 |

B-1/B-2 是证据状态，不是可修产品缺陷；本修复不据此新增产品范围。独立 aggregate 再审仍须取得这些必需证据才可 PASS。与 V-7 等已证实缺陷直接相关的回归可提供 B-2 的部分或完整证据，由独立再审重新判断。

## Unchanged Contract

- 原 docId、邮箱、非空姓名、contactId、邮件/别名/人工决定、运营与学术事实不因合并而重写；不删除历史、虚建联系人、猜测姓名、签发人工认可。
- 仅当前 discovered，历史再受冻结名单约束；原 10,686 待确认名单与03存量执行不在本轮。161 待核组不自动合并，两个已有人物根不自动吞并。
- 人物关系只有04仓储写入；人物首发保护只有09写服务。既有准入SSOT、每邮箱校验/抑制、显式筛选、配额、模板门禁、人工暂停、明确永久失败策略保持。非 INTRODUCTION 与范围外行为保持。
- 不修改既有迁移、共享表字段、ES mapping、CandidateEligibilityService、分类器或全局旧发现判定；不新建产品文件、CSS、审核UI或解析器重写。
- 不把 MySQL 与 ES 声称为同一事务。只在新关系/保护的同库边界保证原子；外部事实用受控复核及明确失效处理。
- 不启动生产应用、真实 SMTP、生产数据 apply、开关配置修改、全量迁移执行；不清队列；不发布、push、merge 或改写历史。
- 基准计划、fast-p evidence、aggregate report、manual acceptance 与知识文件只读；仅下文明确授权的执行 handoff 可作为独立 docs 证据写入。

## Authorized Files

仅以下精确文件可作产品/测试变更；文件 ID 只用于任务索引，不授权同目录其他文件。

| File ID | File | Purpose |
|---|---|---|
| D | src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt | R-1, R-2, R-3, R-13, R-15 |
| R | src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt | R-3, R-13, R-14, R-15 |
| P | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt | R-1, R-2, R-3, R-13, R-15 |
| IR | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt | R-1, R-2, R-4 |
| REC | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt | R-2, R-4, R-5 |
| DISC | src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | R-1 |
| PIPE | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt | R-1 |
| Q | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt | R-1, R-2, R-9, R-10, R-11, R-12 |
| ES | src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt | R-9, R-10 |
| EC | src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt | R-1 |
| CC | src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt | R-1, R-11 |
| SEL | src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt | R-1, R-2, R-6, R-7 |
| BS | src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt | R-6 |
| MI | src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | R-7, R-8 |
| INIT | src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt | R-7, R-8 |
| ITER | src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt | R-7 |
| G | src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt | R-1, R-2, R-6, R-8, R-14 |
| TX | src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt | R-8 |
| MEM | src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt | R-6, R-8 |
| MAINT | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt | R-1, R-2, R-13, R-14, R-15, R-16 |
| MC | src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceController.kt | R-15 |
| PT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt | R-1, R-2, R-3 |
| RT | src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt | R-3, R-14 |
| IRT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt | R-1, R-2, R-4 |
| RECT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt | R-2, R-4, R-5 |
| DISCT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | R-1 |
| PIPET | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt | R-1 |
| QT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt | R-1, R-2, R-9, R-10, R-11, R-12 |
| EST | src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt | R-9, R-10 |
| ECT | src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt | R-1 |
| CCT | src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt | R-1, R-11 |
| SELT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt | R-1, R-2, R-6, R-7 |
| BST | src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt | R-6 |
| MIT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | R-7, R-8 |
| INITT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt | R-7, R-8 |
| ITERT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt | R-7 |
| GT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt | R-1, R-2, R-6, R-8, R-14 |
| MEMT | src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt | R-6, R-8 |
| MAT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt | R-1, R-2, R-13, R-14, R-15, R-16 |
| MCT | src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt | R-15 |
| JS | src/main/resources/static/app.js | R-12 |
| JST | src/test/js/discoveryPersonEmails.test.js | R-12 |
| RP | tools/discovery-identity-recovery/replay.py | R-4, R-17 |
| RPT | tools/discovery-identity-recovery/test_replay.py | R-4, R-17 |
| CP | tools/discovery-person-recovery/reconcile.py | R-13, R-15, R-16, R-17 |
| CPT | tools/discovery-person-recovery/test_reconcile.py | R-13, R-15, R-16, R-17 |
| SEAM | src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt | R-18：精确只读噪声位置，保持原写入口白名单 |

## Repair Tasks

### R-1: 落实默认关闭与不可撤销的既有发送保护

- Resolves: V-1。
- Root cause: 05–09 直接调用人物能力，04 所称默认关闭仅指旧版本无调用者。默认配置部署即改既有读写。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt；src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt。
- Change: 提供真实默认关闭的共同配置，05 增量、06/07 展示及08目标按同一开启边界工作；明确维护窗口可在关闭时受限建立映射/导入保护。回退展示或配置关闭不得绕过已建立的 SENT/UNKNOWN/SENDING 保护。保持未开启、无人物保护的旧范围行为；不得调用生产开关。
- Discriminating regression: 默认配置不启用人物读写/选择；显式开启后完整链生效；关闭显示后再次 INTRODUCTION 仍被已有保护拒绝。
- Existing verification: C03, C08, C10, C12, C16, C17, C18（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-2: 人物链严格使用实时 discovered 范围

- Resolves: V-2。
- Root cause: PersonService:155、Recovery:190、Registration:facts、Maintenance:369 使用 isDiscoveryMap（proof/source/tag 任一），移除 discovered 标签仍可登记/应用；P01/P06。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt。
- Change: 将人物专属范围检查收敛为当前真实 docId、发现来源和字面 discovered 标签同时成立；历史应用再加冻结名单。三层不一致/标签移除明确失效。保持旧准入 isDiscoveryOutreach 的宽口径，不能全局改它影响既有准入范围。
- Discriminating regression: 保留合法 proof/emailSource 但移除标签后，登记、历史 apply、人物聚合/选址都不得沿用有效关系；范围外行零写。
- Existing verification: C01, C03, C05, C08, C10, C12, C16, C17, C18（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-3: 在竞争边界复核整个有效人物组

- Resolves: V-3。
- Root cause: PersonService:61–89 不校验组全名；279–312 attach 不把既有根成员/作者键加入冲突检查，重试不重读 ES；P02/P03。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt。
- Change: 新组及附加成员都校验规范全名、逐邮箱绑定、层间一致、连通性和每类作者键唯一；将既有根全部当前成员/作者键纳入检查。锁/唯一键冲突后重读必要事实，不使用第一次验证缓存当复验。两个已有根仍只待核。
- Discriminating regression: 不同全名同 ORCID 不归组；已有人物 ORCID+OA A111 不接纳同 ORCID+OA A222；真实并发相容成员一人，不相容成员不能借竞争提交。
- Existing verification: C07, C08, C11（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-4: 恢复流程显式失败并按已验证检查点续跑

- Resolves: V-4, V-19。
- Root cause: Recovery:207–214 联系人/alias 读异常当不存在；110–115 忽略 refresh FAILED/STALE/异常，138 返回 APPLIED；重试被194已有proof挡住。 Recovery:100–107返回PARTIAL，但下一次inspect:194任一已有proof即拒绝；P15 首次PARTIAL，排除故障后preview=NEEDS_MANUAL:EXISTING_PROOF，同原hash apply=STALE。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt；tools/discovery-identity-recovery/replay.py；tools/discovery-identity-recovery/test_replay.py。
- Change: 联系人/别名保护读取异常立即停止写入；人物登记失败/STALE 不报完整 APPLIED。对同一原申请的部分层成功、登记/准入后续失败保留可核对的续跑状态，只接受原值或本次目标值，遇第三值停止；不把所有已有 proof 放宽为自动可改。
- Discriminating regression: 跨层第一次 PARTIAL，故障解除后同一检查点补齐剩余层，第三方并发值不覆盖；登记失败可从现有 ES 事实续跑；任一保护读取异常零身份写。
- Existing verification: C03, C04, C05, C06, C10（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-5: 核对精确来源与原文身份

- Resolves: V-20。
- Root cause: Recovery:369–376为PMCID只构造空PaperMetadata并调用提取，不校验JATS article-id。P16真实原文article-id=PMC7759461，存量定位改PMC1234567、HTTP边界返回原字节，仍RECOVERABLE并签出proof；不 mock 身份解析。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt。
- Change: PMCID 路径校验原始 JATS 的明确论文标识与服务端存量 locator 一致，冲突不签 proof；沿用既有抓取/解析器和预算，不增加任意 URL 或客户端证据入口。
- Discriminating regression: 归档 PMC7759461 原字节正向可回放；locator=PMC1234567 而原文明确为 PMC7759461 时停止；使用真实解析器、仅替身 HTTP 边界。
- Existing verification: C05, C10（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-6: 完整人物保护与发送前当前资格

- Resolves: V-5。
- Root cause: Recipient:117,153,162,251–265 缺失/移层成员被丢弃，scope 先过滤隐藏 HOLD；无映射仍逐档案可发。Guard:133–154 SAFE_RETRY 绕历史复核，194–220 不重读 ES/准入；详情 profile null 直接无 guard；P08。
- Files: src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt；src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt。
- Change: 人物硬保护先读取整个有效组，包括不满足当前筛选或不在当前层的成员；缺失/失效/不可读/未映射 discovered 明确排除。随后对具体邮箱执行原 scope、准入、验证和抑制。每次 reserve 包括 SAFE_RETRY 及 claim 前复核当前组、标签、关系 revision、具体 recipient 与资格；已知人物画像不可读不能退成无 guard 普通邮件。
- Discriminating regression: RAW HOLD 成员不在候选页仍阻断另一合格邮箱；reserve 后修改标签/准入/邮箱以及 SAFE_RETRY 后新增历史均不能 SMTP；普通范围与非 INTRODUCTION 保持。
- Existing verification: C02, C16, C17（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-7: 重试与新目标共用人物去重和默认地址

- Resolves: V-6。
- Root cause: ManualInitial:567–581,711–724,1967–1994 以 docId/orcid 去重，未把 personKeyOf 接入；P13 同人 retry D2 后 ES D1 又计1。Recipient:279 把全部成员邮箱当默认，未使用primaryDocId。
- Files: src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt。
- Change: 预估/执行/跨页/重试共享本次 scope 下人物集合，完整判定后入 seen；实际接入迭代器人物键。安全重试绑定原地址，显示 primaryDocId 仅在该邮箱合格时优先，否则按原验证级别/docId。
- Discriminating regression: retry D2 与新页 D1 属同人仅1目标/预估1；首遇不合格页不漏掉后续合格成员；primary 合格时优先，不合格时确定性选址。
- Existing verification: C02, C16（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-8: 把人物投递状态做成持久且保守的生命周期

- Resolves: V-7, V-8, V-9。
- Root cause: ManualExpert:63/79 外层事务包含 guard reserve/claim、SMTP110、mailRecord121；Guard REQUIRED。P09 真MySQL SAFE_RETRY→SENDING→假SMTP→外层回滚后SAFE_RETRY，下一次Granted。 Initial:164–166、ManualExpert:114–116 对所有非SENT标SAFE_RETRY；ManualInitial:1276仅INFRASTRUCTURE归UNKNOWN。实际SmtpMailDelivery:141–148把MessagingException变FAILED，Classifier:101无返回码为TRANSIENT，含交付后网络中断。 Initial:99–117 account/contact 异常在cleanup外；ManualInitial:1050,1068,1086,1110,1120,1147,1286 取消/compose/audit等退出未转换已持有ticket，后续永久IN_FLIGHT。
- Files: src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt；src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt。
- Change: 在 SMTP 前独立持久化交付权，使业务成功事务回滚不会撤销 SENDING/UNKNOWN；成功事实与原邮件业务保持联动。只有可证明未调用 SMTP 或明确未被接受才 SAFE_RETRY，不从 FAILED/TRANSIENT 推断；所有 reserve 后 pre-SMTP 退出清理到可解释安全状态。SENDING/未知不靠超时释放，SAFE_RETRY 不换地址。维护历史导入仍须与人物登记同库原子，不能被运行时独立事务破坏。
- Discriminating regression: 真 MySQL +假 SMTP：从 SAFE_RETRY 经详情入口发送后业务写失败，第二次拒绝且累计 SMTP1；无回复码网络异常不得重试；取消/compose/账户/contact/audit失败且未 SMTP 时允许原址安全续跑。
- Existing verification: C02, C16, C17（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-9: 恢复混合人物查询合法深分页

- Resolves: V-10。
- Root cause: Query:90把from+size传size；Search:1254要求size≤1000。P04 from1000,size50 抛IllegalArgumentException。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt；src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt。
- Change: 保留非 discovered 原精确 count，仅获取归并所需前缀，并支持原允许 from/size；不把 from+size 误当受1000限制的单页 size，不全扫描范围外专家。
- Discriminating regression: 混合查询 from1000,size50 返回正确第21页及 total，边界重叠人物不重复；较浅页排序相同。
- Existing verification: C12（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-10: 拒绝不完整 ES 查询结果

- Resolves: V-13。
- Root cause: Search:1275,1310,1328 未拒绝timed_out/_shards.failed/body缺失/中途不完整；P12 total100且timed_out=true+failed shard正常返回0。
- Files: src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt；src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt。
- Change: 初始页/后续 scroll 的超时、失败分片、缺 body 或不完整终止返回明确查询不可用；只在完整遍历后发布精确 total，所有失败路径关闭 scroll。
- Discriminating regression: timed_out=true、failed shard、第二页body缺失均不返回部分0/total；close-scroll 被执行；完整样本人数不变。
- Existing verification: C12（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-11: DB 联系人只用当前有效关系聚合

- Resolves: V-11。
- Root cause: Query:247裸SQL映射不重核；ContactController:95–116 fallback orcidId把普通同orcid不同contactId合并并改排序；app默认新模式。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt。
- Change: DB分支以当前标签/证据核验过的关系归组；无效关系回到原联系人单位并标待核。范围外以原contactId为单位并保留既有排序/过滤；不按orcidId合并普通联系人。
- Discriminating regression: 同orcid不同contactId的普通联系人保持两条；人物成员移标/指纹变化不继续聚合；非代表邮箱域名命中仍有效。
- Existing verification: C12（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-12: 详情展示真实逐邮箱准入与邮箱状态

- Resolves: V-12。
- Root cause: Query:229 admissionStatus=filterResult，未读取V148/独立邮箱状态；app:13508 PASSED显示可发送，HOLD/抑制可能被误标。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt；src/main/resources/static/app.js；src/test/js/discoveryPersonEmails.test.js。
- Change: 逐邮箱读取既有准入及邮箱状态真值，无法取得时明确未知；不把旧filterResult映射为可发送。两宿主共用契约，HOLD/抑制/不合格地址不显示可发送；保持原doc/contact历史导航与转义/generation。
- Discriminating regression: filterResult=PASSED但V148 HOLD或邮箱抑制时，两详情都不显示可发送，正常/未知状态分别准确。
- Existing verification: C12, C13, C14, C15（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-13: 维护 expected 状态覆盖完整审计事实

- Resolves: V-14。
- Root cause: Maintenance:390仅updatedAt/RAW版本、494摘要仅docId+身份指纹；Domain:158不含proof/version/tag/seq/SQLrevision/人工历史；429/450/466读异常转null；239再次读后不对原expected。P10更换proof hash仍APPLIED；reconcile:263落盘遗漏原始复核字段。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt；tools/discovery-person-recovery/reconcile.py；tools/discovery-person-recovery/test_reconcile.py；src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt。
- Change: 预览和应用同一摘要绑定三层版本/标签/proof源哈希版本/身份证据/SQLrevision/人工与历史保护；任一必需读取异常明确失败。提交前再次复核原 expected，不能用新读结果覆盖预期。CLI完整保存合同要求的逐组证据与前后关系，保持0700/0600和原子落盘。 同一申请已完整提交的目标状态须识别为 UNCHANGED，保留安全断点继续；这项幂等识别不得把第三方版本或身份变化当作自己的写入。
- Discriminating regression: 只改变 proofHash、CANDIDATE seq、SQLrevision 或人工决定，旧预览均跳过且零关系写；审计输出可重建全部复核字段；读取异常不报可发送。
- Existing verification: C18, C19（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-14: 原子导入历史保护并保守生成回滚提案

- Resolves: V-15。
- Root cause: Maintenance:228自调用@Transactional applyGroup231；248关系落库后258只读import；279未持久化SENT/UNKNOWN却报protectionImported。321回滚仅拦SENT/UNKNOWN，P11 SENDING仍safeToRollback=true。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt；src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt。
- Change: 通过09唯一写服务把已有成功/未知历史持久化为人物保护；整组关系与保护导入同库事务，避免self-invocation无事务或先关系后失败半提交。完整查询所有成员历史/联系人/别名/资料冲突，不以任意selector排除冒充已导入。回滚提案核对关系版本/后续成员/历史及所有进行中或不确定状态。
- Discriminating regression: 历史SENT/UNKNOWN导入后增加新邮箱也不能首发；导入故障整组关系零提交；SENDING/RESERVED/版本变化均不标safeToRollback；无历史不制造永久RESERVED。
- Existing verification: C07, C11, C17, C18（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-15: 冻结清单与本次选择子集分离

- Resolves: V-16。
- Root cause: Maintenance:193用请求子集重建全量摘要；reconcile limit50/过滤done仍用原previewDigest，P05合法子集被PREVIEW_DIGEST_MISMATCH拒绝。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceController.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt；tools/discovery-person-recovery/reconcile.py；tools/discovery-person-recovery/test_reconcile.py；src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt；src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt。
- Change: 请求明确完整冻结清单/预览身份与本次选择组；受限应用只处理冻结清单内已复核组，合法50组试点或断点剩余组不因子集摘要误拒；服务端仍重验，不能扩大冻结名单。 同一申请已完整提交的目标状态须识别为 UNCHANGED，保留安全断点继续；这项幂等识别不得把第三方版本或身份变化当作自己的写入。
- Discriminating regression: 全量预览至少51组→只apply50成功，续跑剩余1成功且已完成组不重复；夹入非冻结ID拒绝。
- Existing verification: C18, C19（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-16: 保证每个冻结 ID 恰一个处置

- Resolves: V-17。
- Root cause: Maintenance:66不拒绝候选组重叠，77重复累计；P07冻结2条、groups[a,b],[a]输出total3。
- Files: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt；src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt；tools/discovery-person-recovery/reconcile.py；tools/discovery-person-recovery/test_reconcile.py。
- Change: 在预览入口拒绝重叠候选组或形成明确定义的互斥分区，不能重复累计；主类总和始终等于唯一冻结分母，范围外不进分母。
- Discriminating regression: 冻结[a,b]而候选[a,b],[a]明确拒绝/互斥归类，总数不得3；原四类样本每ID一次。
- Existing verification: C18, C19（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-17: CLI 使用现有会话认证

- Resolves: V-18。
- Root cause: replay:166、reconcile:223请求只有Content-Type，无cookie/auth参数；对应controller要求getSession(false).username，全部请求401。
- Files: tools/discovery-identity-recovery/replay.py；tools/discovery-identity-recovery/test_replay.py；tools/discovery-person-recovery/reconcile.py；tools/discovery-person-recovery/test_reconcile.py。
- Change: 两CLI增加明确、安全的现有会话凭证输入并发送到指定API，缺失/过期给出可操作错误；不降低controller访问控制，不将凭证写入审计/标准日志/文件名，保留私有输出权限。
- Discriminating regression: 会话有效请求到达preview/apply；无/过期会话保留401并停止；审计中无认证信息。
- Existing verification: C06, C19（下节精确命令），最终使用兼容 JDK11 的 C21（C20 环境限制见下节）。
- Must not change: 本计划 Unchanged Contract 全部适用；本任务不改变其他入口业务语义。
- Prohibited: 文件集之外写入、可选重构、用 mock 掩盖本项真实状态/事务边界、跳过失败测试、对生产发送或应用。
- Machine completion: 上述反例在真实相关服务路径不再成立；新增回归有辨别性，相关既有命令退出0；不引入本任务外差异。

### R-18: 同步只读投影噪声位置，恢复必需全量构建

- Resolves: V-25（P2，mandatory build）。
- Root cause: OperatorStatusWriteSeamGuardTest.kt:163 在 C20/C21 均失败。ContactController:632、Query:228、IndexController:80/108/477 为只读参数/DTO 投影，旧精确排除项行号失效且新增投影未登记；原真实写入口两文件不变。基线扫描闭包通过；C21 4999 tests、1F/0E/20S。。
- Files: src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt。
- Change: 其他产品修复完成后，逐项核实只读 DTO/查询参数投影，更新 EXCLUDED_NOISE_SITES 的准确 path/line/context；保留两个真实写入口白名单及排除项失效检查。不得将查询/控制器加入 ALLOWED_WRITE_SITES，不得删测试、放宽扫描或吞断言。
- Discriminating regression: 守卫精确扫描得到原两个真实写入口，所有噪声排除项均仍命中；完整 JDK11 构建通过。
- Existing verification: C22，最终 C21；C20 旧 JDK 环境例外见下节。
- Must not change: 仅 ExpertOperatorStatusService.kt 与 ManualInitialOutreachService.kt 允许真实 operator_status 写入，原白名单闭包保持。
- Prohibited: 删除测试、移除失效排除项检查、加入控制器/查询类到真实写白名单、修改无关时区数据。
- Machine completion: C22 与兼容 JDK11 的 C21 退出0，全部真实写入口与精确噪声排除项可逐项审计。

## Verification Commands

全部从上述 worktree 执行，JDK11；Docker 仅临时测试库，所有发送替身。保留每次失败及重跑退出码/计数，不用子计划旧 PASS 替代新结果。C01—C21 对应 aggregate report 同名命令；C22 为 V-25 修复的聚焦守卫命令。共享完整命令只需新跑一次。C20 原命令已在审查执行，Zulu11.0.15 的 tzdb2021e 导致19个无关时区错误；现有 Zulu11.0.32.1 对照消除这19个错误，仅余 V-25。修复验收要求 C01—C19、C21、C22 新鲜通过；无需为旧 JDK 的已知 tzdb 缺项修改产品。若 C01—C19 在修复后同样受该环境限制，可仅将 JAVA_HOME 换为下列 C21 的现有 JDK11 路径，并准确记录原命令失败和对照命令；不得跳过任何用例。C20 保留为原合同命令及环境证据，C21 是同一 JDK11 clean package 门禁的兼容补丁版本执行。

- C01: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest test`
- C02: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test`
- C03: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test`
- C04: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test`
- C05: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test`
- C06: `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'`
- C07: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test`
- C08: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonServiceTest test`
- C09: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`
- C10: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test`
- C11: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test`
- C12: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test`
- C13: `node --check src/main/resources/static/app.js`
- C14: `node --test src/test/js/discoveryPersonEmails.test.js`
- C15: `node --test src/test/js/*.test.js`
- C16: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test`
- C17: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test`
- C18: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test`
- C19: `python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'`
- C20: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
- C21: `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn clean package`
- C22: `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dtest=OperatorStatusWriteSeamGuardTest test`

## Completion Criteria

- V-1—V-20、V-25 全部通过对应辨别性回归与代码路径审查；持久化/并发/SMTP 反例使用真测试数据库和假 SMTP，不只 mock 被验证的 guard。
- C01—C19、C21、C22 按上述环境规则新鲜执行通过；JS 所有用例全绿；需要 Docker 的仓储/迁移/守卫测试实际执行而非 skipped。保留完整失败记录和原因，不通过修改断言抹除缺陷。
- 产品/测试 changed files 是 Authorized Files 的子集；保持原ID/历史/准入/预算；真实邮件、生产 apply、物理删除均0。
- 无 BLOCKED/Observation/非必需P2 被偷偷纳入产品修复；V-25 为明确列入的必需构建P2。B-1/B-2 证据未补齐时不得声称 master PASS 或 READY_TO_INTEGRATE；修复执行成功最多 READY_FOR_VERIFICATION。
- 本计划不产生自动下一轮、不拆 continuation 或并行 repair 文件。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
明确的人类来源 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md` 批准该路径当时内容及本计划限定权限；代理建议或自动技能转移不构成批准。批准后使用 execute-p，未批准不实施。

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only changed Authorized Files, with product commit subject `fix(discovery): repair person identity and outreach invariants`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/review/2026-10-13-discovery-identity-recovery-master/repair-execution.md` with the exact approval source, repair identity/hash, pre/post code SHAs, changed files, every executor command/result/count, deviations, executor identity when exposed, and clean-state evidence. This is the only separately authorized execution-evidence file. The baseline fast handoff is `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fast/2026-10-13-discovery-identity-recovery-master/human-review-handoff.md` and remains read-only.
4. Exactly one docs-only evidence commit containing only that repair-execution.md, with evidence commit subject `docs(review): record discovery person repair execution`.
5. Returning to the already authorized review-fast-p aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan. The executor does not write machine-verification.md/manual-acceptance.md/final-decision.md or fast-p evidence. A product/evidence commit is authorized only by the later human invocation; drafting this plan authorizes none.

## Self-Review

- 每个任务对应已证实的合同违反与准确文件；按根因组织，未按测试断言数量机械拆分。
- BLOCKED、无关基线与可选P2已排除；无新增产品目标、存储字段或静默计划修订。
- 运行时持久 SENDING 与维护同库历史导入分别满足各自事务合同，不能互相破坏。
- 只有一个 current repair.md；后续只允许上述一次产品提交与一次限定证据提交；无自动实现、续轮或集成授权。
