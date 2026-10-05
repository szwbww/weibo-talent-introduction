# 04：自动准入、人工批准与数据写入衔接

依赖：01–03。仍不切换发送逻辑；存量初始化完成后才允许05/06/D1整体发布。

## 需求描述

新发现基础校验通过自动准入；人工通过者缺项保留但进入候选范围；补全和重验不再推翻有效人工批准。保持来信晋升、人工降级、标签与联系人状态语义；旧非发现专家不强制补审核记录。

范围外：重新抽取论文、改变分类算法、自动恢复人工降级、历史发送政策。

## 关键不变量

### Invariant I-1：自动写入与人工优先级
- Rule：RAW成功后执行01基础校验并持久AUTO_PASSED/NEEDS_REVIEW；同身份存在MANUAL_APPROVED/LEGACY_APPROVED/HOLD/REJECTED则自动任务不得覆盖。身份变化显式生成新自动结论并保留旧审核历史。补全引起自动资格变化可更新AUTO状态，但不抹掉人工决定。
- Applies to：两个发现收录分支、旧数据初始化、补全后重验、候选复核。
- Violation consequence：人工审核后又被黑盒撤销。
- 来源：X1/X3；用户要求。

### Invariant I-2：投影成功与审核保存分离
- Rule：批准以MySQL事务为权威；RAW-only批准后按真实_id向候选create；已存在候选/有效不覆盖，不新建contact。409只有复读身份一致才当已存在；ES故障记录候选同步失败，可重试投影，不重签审核。审核不改filterResult/PASSED伪装事实。
- Applies to：review confirm后的投影、自动晋升、重试。
- Violation consequence：错误ID覆盖、虚假成功、伪造自动证据。
- 来源：K-promotion-source-passthrough；E5。

### Invariant I-3：重验不能删除人工认可候选
- Rule：发现重验先读取当前准入结论；有效人工批准跳过基础重新拒绝，仅更新确有依据的分类/事实；人工暂缓/拒绝不自动晋升。学术来源绑定仍必须DiscoveryIdentity.allowed，人工批准不开放作者绑定。
- Applies to：revalidateDiscovery、reconcileDiscoveryCandidate、enrichment。
- Violation consequence：发信入口虽放行却无候选文档、错误作者合并。
- 来源：K-enrichment-write-three-layers；E1。

### Invariant I-4：存量初始化不扩大授权
- Rule：固定批次扫描RAW与候选/有效的发现/待确认数据，真实_id去重；只有有效legacy回执写LEGACY_APPROVED。其余按自动判定，不按标签或历史人数默认批准。初始化不删除候选/有效文档、不发送邮件、不擅自取消人工降级；对缺RAW记录使用现存层source并记录level。
- Applies to：初始化/重跑、层级变更。
- Violation consequence：几万人被未经确认批准或已回复专家档案被删。
- 来源：用户“不猜测”；E5。

## 现状审计

DiscoveryService两条初次收录：旧ORCID:1407–1420、consumeOutcomeInternal:1733–1750；初次晋升:2307自己PUT。补全:3004更新三层；:3215调用revalidateDiscovery。Revalidation:325先allowedMap，Writer:751再次allowedMap并按reasons删除候选。仅在发送端加manual旁路无法解决这些写入冲突。

Writer:531人工层级晋升、:404有效层晋升、:808降级不代表这次发现审核；保留原行为，数据库当前结论不会因source透传丢失。完整原始/候选/有效层写读清单见审计E5；新增admission表写入口收敛到ReviewService的recordAutomatic/applyManual，其他service不直接SQL。operatorStatus不新增写入口。

## 实现方案

1. **I-1/I-4**：ReviewService新增`recordAutomatic`、`resolveAdmissionBatch`、`initializeExistingAdmissions`，后者由已登录review controller的明确初始化操作触发，复用03任务与scroll。列表只读，不在GET偷偷写自动结论。无admission的存量显示“尚未初始化”，不是“人工待审”；初始化完成后才切换新发送。
2. **I-1/I-3**：两个发现收录分支在RAW成功之后调用统一准入；避免先调用旧candidate eligibility决定晋升再调用新policy产生另一结论。现有早于RAW的邮箱抽取/有效性拒绝仍属于采集输入处理，不伪称这些未入库人已可审核；收录后的基础问题才纳入本次审核。采集前被丢弃的数据不在本次扩大保存范围。
3. **I-2**：ReviewService新增候选投影方法，供人工批准与自动晋升共用；Writer新增或限定修改discovery晋升接缝，以真实docId、源快照和显式准入结果执行create/CAS。不直接调用`promoteToCandidate(orcid,contact)`造一条contact；没有contact的RAW专家也可审核。
4. **I-1/I-3**：Revalidation去掉人工分支前的硬性allowedMap拒绝；先取权威批准，再决定自动校验或保留。Writer接收受控准入结果；自动分支保留现有CAS，人工批准分支不把缺项覆盖为PASSED，不按基础失败删候选；非发现重验保持原样。
5. **I-2/I-4**：审核投影失败在原item.error_code记录CANDIDATE_SYNC_FAILED，state仍APPLIED（审核已存）；列表显示独立同步状态，重试仅补投影。有效层存在时不反向复制候选；被人工降到RAW后不由普通读取/重验自动恢复，仅新一次明确批准或既有合法自动流程可晋升。
6. **I-1–I-4**：补测试两条收录、RAW-only、candidate已有、APPLICATION-only、来源冲突、legacy有效/失效、补全重验保留、人工降级不恢复。只改受影响断言，不能删除防错测试来凑通过。

## 变更文件清单

共10文件，发现准入与索引同步2子系统；既有共享表新增字段0。

| 文件 | 操作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt | 自动准入/批量读取/投影 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt | 存量初始化与同步重试 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | 两初次写路径共用准入 |
| src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt | 尊重人工准入 |
| src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt | 真实_id/CAS准入投影 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt | 初始化/投影覆盖 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | 两初次写路径 |
| src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt | 自动/人工重验 |
| src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt | 非发现/降级回归 |
| src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt | CAS/create/409 |

## 验收标准

- I-1：三种已人工处理状态分别重验，revision/决定不被自动覆盖；自动数据可从NEEDS_REVIEW变AUTO_PASSED。
- I-2：RAW-only批准后创建候选；已有候选运营字段不变；create409复读不一致失败；500显示审核已保存/同步失败；重试不重复审核。
- I-3：人工认可仍不能通过学术身份验证；重验不删其候选；非发现路径输出与改前相同。
- I-4：有效legacy按真实digest识别；初始化幂等；RAW缺失的候选不会被删除；重启后状态可查；全程SMTP调用0。
- 回归原有测试；数据库/ES交互失败使用隔离集成测试与故障注入。

## 人工验收清单

### A-1：自动收录与人工晋升
- 前置条件：隔离发现源返回全过A、缺机构B，两者有效邮箱；B只有RAW。
- 操作步骤：1. 跑发现与补全。2. 看A自动通过/B待审。3. 批准B。4. 刷新候选列表。
- 预期结果：A无需人工；B进入候选，机构仍空；没有新contact，没有邮件发出。
- 覆盖：I-1/I-2、X1/X2。

### A-2：重验及同步失败
- 前置条件：B已批准，另一个C批准时ES写失败。
- 操作步骤：1. 跑补全/重验。2. 查看B。3. 查看C同步失败并重试。4. 将B升有效，再降RAW。
- 预期结果：B仍人工通过；C重试后候选存在且历史仅一条批准；B降级后不被后台私自恢复，历史保留。
- 覆盖：I-2/I-3、X3/X4。

### A-3：存量回执与非发现回归
- 前置条件：有效legacy1人、无效legacy1人、普通非发现1人；候选/有效存在而RAW缺失1人。
- 操作步骤：1. 初始化两遍。2. 查看结论及原数据。3. 运行旧非发现重验。
- 预期结果：仅有效legacy为历史人工认可；重复初始化不扩张授权；普通专家行为不变；缺RAW者原索引不丢失。
- 覆盖：I-4及必须保持项。
