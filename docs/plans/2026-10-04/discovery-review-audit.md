# 深度发现审核 / 批量发送：代码审计

审计日期：2026-10-04。基线：本地 `main`，HEAD `e28e53fd898edd62905a0d45a6bf90396b18b1bf`。既有未提交工作保留。本文件说明**现有代码事实**；方案选择在各子计划明确列出。本轮没有 SSH 改配置、发送邮件或部署业务代码；不能把本地历史兼容补丁视为已经上线。

## 证据索引

- [逐行源码摘录](discovery-review-evidence/source-excerpts.md)：凭证、基础筛选、组装、重试、晋升、滚动查询。
- [ES 写路径检索](discovery-review-evidence/es-write-paths.txt)、[ES 读路径检索](discovery-review-evidence/es-read-paths.txt)。检索范围 `src/main`、`tools`、`scripts`；不是对服务器临时运维脚本的完整性保证。匹配结果包含 GET/HEAD、注释、测试及外部 API 噪声，不能把匹配行数当写路径数量。
- [批量分支检索](discovery-review-evidence/batch-gates.txt)、[共享投递接缝检索](discovery-review-evidence/shared-send-writers.txt)。
- [当前 UI/CSS 原文](discovery-review-evidence/frontend-baseline.md)、[缓存键反查](discovery-review-evidence/cache-key.txt)。当前 11 个版本化资源；当前键在 `src/test` 无命中，不沿用历史“固定九个测试”的结论。

以下 Kotlin 路径均相对 `src/main/kotlin/com/weibo/talentintroduction/`；方法名为定位依据，行号为本次快照。

## 现状审计

### E1：两套资格判断，不是一套

1. `expert/service/CandidateEligibilityService.kt:21`：候选校验读取 `eligibility_filter_setting` / 配置，检查 ORCID、邮箱格式、一次性邮箱、学历、年龄、国籍、h-index、引用、活跃年限；只在相关开关开启时生效。发现专家另按分类产生 `RND_SCOPE_UNCONFIRMED / RND_EVIDENCE_INSUFFICIENT / RND_OUT_OF_SCOPE / RND_SERVICE_ONLY`。不能把这些条件全部说成“线上已开启”。
2. `campaign/domain/BatchExecutionModels.kt:262`：发送又要求身份凭证、机构、可映射国家、`filterResult=PASSED`、机构凭证；有效历史人工认可只替代身份/机构凭证，不替代机构/国家/PASSED。
3. `expert/service/ExpertRevalidationService.kt:325` 重验会读 RAW、重新检查身份和资格，再经 Writer 更新 RAW/新增或删除 CANDIDATE。人工认可若只写一个发送旁路，重验仍可能把专家移出候选。
4. `expert/service/ExpertIndexWriterService.kt:715` 的 `discoveryProfile()` **当前没有装载 institutionEvidence 和 filterResult**；`ExpertSearchService.toExpertProfile():465` 有装载。新解释器不能直接相信前者信息齐全。
5. 发现初次写入与后续学术补全存在时间差：`ExpertDiscoveryService.kt:1733/1748` 先写资格与晋升，补全 `3004` 才局部更新研究字段。不能把“现在有研究方向”反推为当时也有。

### E2：缺少凭证的可证明原因

| 现象 | 代码事实 | 页面可说 / 不可说 |
|---|---|---|
| 身份凭证不通过 | `DiscoveryIdentity.allowed():45` 检查版本、VERIFIED、姓名、邮箱、来源摘要格式 | 分别显示缺对象、版本不支持、字段不一致；不能统称专家不真实 |
| LEGACY_APPROVED | `DiscoveryIdentity.kt:74–91` 固定 source、版本、绑定摘要 | 显示“历史人工认可”；不把它写成学术来源验证成功 |
| 机构为空 | `OrcidDataSource.kt:167–179` 机构去空去重后 `singleOrNull()` | 可能零家或多家；当前存储未保留原机构集合，无法逐人断定是哪一种 |
| ORCID 国家为空 | `ExpertDiscoveryService.kt:1519` 明确 `country=null`；来源 DTO 也为空 | 可以说明“该采集路径未写入所在国家”；不能推断国籍 |
| token 未写入 | `DiscoveryIdentity.institutionEvidence():110`：身份不通过、身份 ID 冲突、机构缺失、来源必需 ID 不齐均返回 null；发现存储点 `2303` 只在非 null 时写入 | 显示当前缺少哪些输入；无历史证据时写“无法确认当时未生成原因” |
| OpenAlex 必需 ID 不齐 | 上述函数 `122–128`：作者 ID + DOI/PMC 至少一项 | 明确列出缺的键；不能仅凭 `dataSource=OPENALEX` 认定 token 一定可生成 |
| token 存在但验签失败 | `validInstitutionEvidence():133` 重算 token | 分开显示“不支持来源”或“当前字段与凭证不一致”；不伪造旧字段值 |

研究方向与机构凭证不是同一个条件。有研究方向不能证明机构凭证有效；人工放行不应修改或伪造来源凭证。

### E3：现有页面显式条件及后端差异

页面：`static/index.html:1330–1490` 配置编辑、`1583–1760` 手动执行；快照：`BatchExecutionModels.kt:59`。

| 页面条件 | 现有字段 | 审计结果 / 计划契约 |
|---|---|---|
| 漏斗层级 | funnelLevel | 空=候选+有效；页面没有 RAW 选项，不新增 RAW 发信旁路 |
| 标签 | tags | 集合内 OR、不同维度 AND；`discovered` 显示为新发现 |
| 地区 | regions | ES `country OR nationality`，发现最终谓词 country-only，重试 country-only，当前不一致；发现专家按所在地 country，非发现维持原查询语义并统一内存路径 |
| 邮箱服务商 | emailDomains | 显式域名集合，空不限 |
| 学科 | discipline | 空不限；UNCLASSIFIED 为字段缺失 |
| 研究方向 | researchDirectionFilter | ANY/PRESENT/ABSENT；基础存在性与模板实际变量缺失是两个语义，不混用 |
| 专家状态 | operatorStatuses | 页面空不限，但 `buildEsFiltersForLevel():1843` 在候选首发偷偷切到 NOT_CONTACTED 基座；应修复 |
| 研发类型 | expertTypes | INTRODUCTION 必选；空集合零人，不自动换成隐藏默认类型 |
| 模板门禁 | gateFilterEnabled + templateId | `resolveScope():474` 仅对允许的 ES 字段预筛；`IntroductionMailComposer.kt:29` 无条件做完整门禁，关闭不真正关闭 |
| 排除已验证不可用邮箱 | excludeVerifiedUnavailableEmails | `1793` 按开关；保留独立于实时验证的语义 |
| 发前邮箱验证/放行结果 | emailVerificationEnabled / emailVerificationAllowedStates | 运行时调用验证；预估不得假装已经调用。关闭不自动添加格式/历史验证门槛 |
| 发件邮箱、轮次与配额 | senderAccountCodes / roundSize 等 | 执行参数；不应冒充专家学术不合格 |

### E4：尚需用户定案的历史发送行为（D1）

**2026-10-04 已发出澄清，尚无答复。以下不是获准保留的例外。**

| 行为 | 代码证据 | 为什么不能擅自决定 |
|---|---|---|
| 已退订/抑制 | `ManualInitialOutreachService:313/733/1726`，投递层 `SmtpMailDeliveryService:22` | 单删上层仍被 SMTP 接缝拦截；已有 `ComposedMail.allowSuppressedRecipient`，但注释限定人工单发 |
| 已绑定账号 | `ManualInitialOutreachService:747/1488/1720`；页面 `index.html:1331` **已有跳过提示**，没有开关 | 移除后须决定使用现有绑定还是改绑；不能静默重绑 |
| 已发首封/材料提醒 | `1327/1610/1472/1727/932/355` | “同一执行防重复”与“历史发过永久排除”不同；不能把后者伪装成技术幂等 |
| 首封永久失败 | `895/1472` | 目前不受“排除已验证不可用邮箱”开关控制；仅改开关无效 |
| NEW 重试排除 EMAIL_INVALID | `1473` | 不能在页面状态空不限时另行排除；与历史失败政策关联 |
| 材料提醒必须存在 contact | `1721` | 所选 ES 专家无 contact 会直接消失；需明确为可见任务类型前提或创建 contact，不能猜测 |

D1 选项：移到深度发现审核后可授权；完全服从现有配置；保留但明示。三者会影响不同代码，不能在计划中同时假装都实现。**未定案前不能声称“所有黑盒均消除”，不能上线发送切换。**

### E5：数据存储与全部匹配路径的归类

**RAW/CANDIDATE/APPLICATION**：三个 `resources/es/orcid_info_{raw,candidate,application}.json:7` 均 `dynamic:false`；身份对象也是 dynamic:false，externalIds 为 enabled:false。方案不新增 ES 审核字段，避免三层事实复制；数据库记录审核。

写路径（完整命中见附件，按实际写入归类）：

| 入口 | 写入内容 | 本计划处理 |
|---|---|---|
| DiscoveryService consumeOutcomeInternal:1737、旧 ORCID 分支:1412 → indexToRaw:602 | 发现 RAW 整份 create-only | 复用；自动准入在 RAW 成功之后，禁止伪造身份 |
| DiscoveryService promoteDiscoveredToCandidate:2307 | CANDIDATE create-only | 接统一准入结果；409 复读确认，禁止覆盖已有运营字段 |
| Writer reconcileDiscoveryCandidate:744 | RAW qualification CAS、候选 create/delete CAS | 接人工结论，重验不能覆盖有效人工批准 |
| Revalidation revalidateCandidates:37 / promoteEligibleRaw:158 / promoteSingleRaw:267 / revalidateEnrichedRaw:368 | 扫描、晋升、删除 | 发现分支统一准入；非发现路径回归不变 |
| DiscoveryService updateExpertAcademicFields:3004 / updateRawExpertEmail:3337 / promoteRawToCandidateWithEmail:3348 | 三层学术字段 partial update；旧 RAW 邮箱补全及晋升 | 审核不放 ES；补全不覆盖审核。邮箱变化按新身份处理，不自动沿用旧授权 |
| Writer promoteToCandidate:531，调用者 ExpertIndexLevelOperationService:67、ExpertContactManagementService:285 | 人工层级晋升，RAW source 透传 | 不等于本次审核批准；不能绕过准入，也不能自动签发审核记录 |
| Writer promoteToApplication:404 / retryFailedPromotion:478；AutomaticApplicationPromotionService、UnmatchedInboundMailService、ExpertIndexController、contact management/level operation 调用 | 晋升 APPLICATION、可能删除候选 | 保留来信链路；审核以真实 docId+邮箱绑定，不随索引移动丢失 |
| Writer demoteToRaw:808，contact management/level operation 调用 | 删除 CANDIDATE/APPLICATION | 审核记录保留；批准不擅自恢复人为降级层级 |
| Writer syncOperatorStatus:75 / syncOperatorStatusBatch:122、markApplicationClosed:50、syncApplicationStatus:499、addTag:637/removeTag:655、bulkUpdateExpertClassifications:234 | 状态、标签、分类局部写 | 不改审核；分类变化不撤销同身份人工批准。批量显式条件仍可因类型/标签变化而改变 |
| scripts/apply_legacy_discovery_approval.py:98 | CAS bulk 写历史 identityVerification | 不执行、不扩大授权人群；有效历史回执可显示为历史批准 |
| scripts/build_sbir_expert_import.py:571、update_sbir_employment.py:89 | 导入文件 / employment partial bulk | 不修改；非发现样本回归 |
| scripts/expert_discovery/enterprise_batch/{import_candidate_es.py:231,import_es_documents.py:74/102,update_es_research_fields.py:67} | 企业专家导入 / 更新 / 批量标签 | 不自动签发发现审核；若身份字段被替换，新身份不继承旧批准 |

读路径（见 es-read-paths.txt）：ExpertSearchService 的分页、scroll、search-after、mget、ORCID 查询、随机字段抽样、聚合；ExpertIndexController 列表/详情/模板变量；ExpertRevalidationService；ExpertDiscoveryService 补全/统计/邮箱回填；ManualInitialOutreachService 与 InitialOutreachService；MailboxService / MailboxConversationService / MailVariableService；上述 Writer 晋升前读取。新审核查询直接使用真实 `_id`，不假定 orcidId 与 `_id` 相等。

**task_execution / task_progress_log**：V4 为 request_payload/result_summary TEXT、计数、时间；V137 增 owner/heartbeat/中断信息。TaskExecutionService:138 支持任意字符串 taskType 与 SummaryProvider；TaskTypeCatalog 决定名称、进度白名单。TaskProgressStore 内存负责活跃进度，不能作为审核持久状态。复用现有任务记录；不把几万人名单塞进 TEXT/内存。

**新审核表**：当前不存在。子计划02给出当前准入表与审核明细表；明细同时存名单快照、人工决策、应用状态与历史；不另建规则引擎、Redis、ES 审核索引。新增读写只有各子计划列出的 repository/service/controller。actor 来自 `AuthSessionKeys.USERNAME`，接口沿用 AuthInterceptor 的 `/api/**` 登录保护。

**expert_contact / mail_record / mail_send_attempt / email_suppression / 验证记录**：前述批量历史检查读取。`V23` 的 UNIQUE(orcid_id,mail_type)、`V24` 的 mail_record attempt 唯一外键意味着允许跨执行重发不能简单复用旧 attempt。ManualOutreachTxHelper:87/136 按 attemptId 记账；ManualReplySendAttemptService:221/252 共享仓储但使用专属 mailType key；改唯一键将影响人工回复。D1 未定案，不先做迁移。

### E6：前端基线与交互

- 现有深度发现入口 `index.html:635`，容器 `taskProgressModal:1121`；采集运行、启动、定时、日志都复用通用任务弹窗。不得把审核塞进批量任务配置，也不得破坏其他任务弹窗。
- 现有预览为 `docs/mockups/discovery-review-preview/`；真实上线页面预览地址 `/talent/previews/discovery-review-20261004/index.html`。其审核仅内存、全部默认待审核、发信衔接新增审核筛选及固定规则的部分，**已被用户后续要求取代**，不能照搬成正式逻辑。
- 复用 `.button:802`、`.button.primary:838`、`.button.secondary:852`、`.data-table:3421`、`.bsc-input:5498`、`.batch-send-tabs:9150`、`.batch-send-tab:9159`、`.modal-content:4494`、`.modal-header:4510`、`.modal-body:4525`。原文见 frontend-baseline.md。
- 基准值：primary #1e40af；hover #1e3a8a；primary-bright #3b82f6；text #1e293b；secondary #475569；surface #f5f7fb；radius 7/10/18px；button 32px/12px；输入 7px 10px/13px；tab gap28px、min-height48px；modal body padding20px/gap16px。
- `--panel-bg=rgba(255,255,255,.55)`，不用于遮挡内容的浮层。（来源 K-panel-bg-token-is-translucent）
- 当前版本资源 11 项统一，测试按动态键读取。（来源 K-frontend-cache-key-triad）

## 关键跨路径关系

X1：发现写 RAW → 自动准入 → 候选读取。X2：人工批准 → RAW-only 晋升 → 批量预估/执行/重试。X3：人工批准 → 补全/重验 → 批准保留。X4：索引晋升/降级 → 审核历史不丢失、不反向恢复索引。X5：全页快照 → 新数据/并发审核 → 确认目标不扩张。X6：模板配置 → 相同变体与变量 → 预估/执行同判定。X7：显式配置 → ES/DB 重试 → 发送明细。X8：共享 composer/SMTP/attempt → 人工单发与自动回复回归。X9：任务记录 → 重启恢复/页面进度 → 不把部分完成标成全完成。

知识来源：K-recipient-count-preview-parity、K-batch-send-filter-retry-parity、K-batch-send-round-loop-symmetry、K-dual-outreach-paths、K-enrichment-write-three-layers、K-promotion-source-passthrough、K-operator-status-write-seam-guard。本次已核代码；K-suppression-check-call-sites 中“建议加投递兜底”已由现有 SmtpMailDeliveryService:22 实现，历史调用行号不作为当前事实。
